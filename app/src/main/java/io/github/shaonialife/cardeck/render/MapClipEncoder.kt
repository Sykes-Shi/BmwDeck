package io.github.shaonialife.cardeck.render

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Looper
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

internal object MapClipEncoder {
    private const val AVC = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val FLEXIBLE = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible

    fun record(
        file: File,
        surface: Surface,
        geometry: MapFrameSize,
        frameCount: () -> Int,
        checkSource: () -> Unit,
    ) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "请从后台录制地图样片" }
        check(file.isFile && file.length() == 0L) { "请先创建空的地图视频临时文件" }
        var success = false
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            checkSource()
            val (name, format) = chooseFormat(geometry)
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val pixels = MapClipPixels(width, height)
            val encoder = MediaCodec.createByCodecName(name)
            codec = encoder
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val writer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            val output = ClipOutput(encoder, writer)
            val firstDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1800)
            while (frameCount() == 0) {
                checkSource()
                check(System.nanoTime() < firstDeadline) { "地图还没有画面，请在中控打开地图后重试" }
                Thread.sleep(12)
            }
            // The working bitmap and all four CPU arrays are reused throughout the clip.
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            var ownsBitmap = true
            try {
                var copiedFrame: Int? = null
                var nextSlot = 0
                var start = 0L
                while (nextSlot < MapClipTiming.frameCount) {
                    checkSource()
                    var slot = nextSlot
                    if (nextSlot > 0) {
                        val due = start + TimeUnit.MICROSECONDS.toNanos(MapClipTiming.pts(nextSlot))
                        val waitNs = due - System.nanoTime()
                        if (waitNs > 0) TimeUnit.NANOSECONDS.sleep(waitNs)
                        slot = MapClipTiming.slot((System.nanoTime() - start) / 1000)
                        if (slot >= MapClipTiming.frameCount) break
                    }
                    val frame = frameCount()
                    if (frame != copiedFrame) {
                        ownsBitmap = false // PixelCopy releases a failed target, including late writes.
                        MapFrameCopy.copyInto(surface, bitmap, TimeUnit.MILLISECONDS.toNanos(600))
                        ownsBitmap = true
                        bitmap.getPixels(pixels.argb, 0, width, 0, 0, width, height)
                        pixels.convert()
                        copiedFrame = frame
                    }
                    checkSource()
                    if (nextSlot == 0) start = System.nanoTime()
                    val index = output.inputIndex()
                    val image = checkNotNull(encoder.getInputImage(index)) { "软件编码器没有提供可写的地图图像" }
                    check(image.planes.size == 3) { "软件编码器的图像格式不兼容" }
                    image.planes.forEachIndexed { planeIndex, plane ->
                        pixels.writePlane(planeIndex, plane.buffer, plane.rowStride, plane.pixelStride)
                    }
                    // queueInputBuffer returns the image and its backing planes to MediaCodec.
                    encoder.queueInputBuffer(index, 0, width * height * 3 / 2, MapClipTiming.pts(slot), 0)
                    output.drain()
                    nextSlot = slot + 1
                }
            } finally {
                if (ownsBitmap) bitmap.recycle()
            }
            checkSource()
            val eosInput = output.inputIndex()
            encoder.queueInputBuffer(eosInput, 0, 0, MapClipTiming.DURATION_US, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            output.finish()
            writer.stop()
            checkSource()
            success = true
        } catch (failure: MediaCodec.CodecException) {
            throw IllegalStateException("手机软件编码器暂不可用，请关闭其他录屏后重试，或先导出地图样图", failure)
        } finally {
            runCatching { codec?.release() }
            runCatching { muxer?.release() }
            if (!success) file.delete()
        }
    }

    private fun chooseFormat(geometry: MapFrameSize): Pair<String, MediaFormat> {
        for (codec in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
            if (!codec.isEncoder || !codec.isSoftwareOnly || codec.isAlias || AVC !in codec.supportedTypes) continue
            val caps = codec.getCapabilitiesForType(AVC)
            if (FLEXIBLE !in caps.colorFormats) continue
            val video = caps.videoCapabilities
            for (edge in listOf(960, 720, 480)) {
                val size = geometry.exportSize(edge)
                val alignW = maxOf(2, video.widthAlignment)
                val alignH = maxOf(2, video.heightAlignment)
                val width = size.width / alignW * alignW
                val height = size.height / alignH * alignH
                if (width < 2 || height < 2 || !video.areSizeAndRateSupported(width, height, MapClipTiming.FPS.toDouble())) continue
                val format = MediaFormat.createVideoFormat(AVC, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, FLEXIBLE)
                    setInteger(MediaFormat.KEY_BIT_RATE, video.bitrateRange.clamp(width * height * 4))
                    setInteger(MediaFormat.KEY_FRAME_RATE, MapClipTiming.FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                    setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)
                    setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                    setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                }
                return codec.name to format
            }
        }
        error("手机没有可独立使用的软件 H.264 编码器，请先导出地图样图")
    }

    private class ClipOutput(private val codec: MediaCodec, private val muxer: MediaMuxer) {
        private val info = MediaCodec.BufferInfo()
        private var track = -1
        private var firstPts = -1L
        private var lastPts = -1L
        private var eos = false

        fun inputIndex(): Int {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(750)
            do {
                drain()
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) return index
            } while (System.nanoTime() < deadline)
            error("地图视频编码器等待输入超时，请重试")
        }

        fun drain(waitUs: Long = 0) {
            while (!eos) {
                val index = codec.dequeueOutputBuffer(info, waitUs)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(track < 0)
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                    }
                    index < 0 -> return
                    else -> try {
                        eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            if (firstPts < 0) firstPts = info.presentationTimeUs
                            info.presentationTimeUs -= firstPts
                            check(track >= 0 && info.presentationTimeUs > lastPts) { "地图视频时间戳异常" }
                            lastPts = info.presentationTimeUs
                            info.flags = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()
                            muxer.writeSampleData(track, checkNotNull(codec.getOutputBuffer(index)), info)
                        }
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
        }

        fun finish() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (!eos && System.nanoTime() < deadline) drain(10_000)
            check(eos && track >= 0 && lastPts in 4_000_000L until MapClipTiming.DURATION_US) {
                "地图视频采样不足五秒或编码未完成，请重试"
            }
            // MP4 supports an empty final sample that sets the exact duration of the last image.
            info.set(0, 0, MapClipTiming.DURATION_US, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            muxer.writeSampleData(track, ByteBuffer.allocate(0), info)
        }
    }
}
