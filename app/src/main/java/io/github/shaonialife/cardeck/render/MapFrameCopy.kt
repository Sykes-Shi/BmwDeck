package io.github.shaonialife.cardeck.render

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal data class MapFrameSize(val width: Int, val height: Int) {
    fun exportSize(maxEdge: Int = 1280): MapFrameSize {
        require(width > 0 && height > 0 && maxEdge > 0)
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return this
        fun scale(value: Int) = ((value * maxEdge.toLong() + longest / 2) / longest).toInt().coerceAtLeast(1)
        return MapFrameSize(scale(width), scale(height))
    }
}

internal object MapFrameCopy {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /** PixelCopy applies the producer's transform and scales the whole surface to the destination bitmap. */
    fun capture(
        surface: Surface,
        geometry: MapFrameSize,
        hasFrame: () -> Boolean,
        isCurrent: () -> Boolean,
    ): Bitmap {
        check(Looper.myLooper() != Looper.getMainLooper()) { "地图样图需从后台导出" }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1800)
        val size = geometry.exportSize()
        fun checkSource() = check(isCurrent() && surface.isValid) { "地图会话或显示尺寸已改变，请重新导出" }
        fun timeLeft() = (deadline - System.nanoTime()).also {
            check(it > 0) { "等待地图新画面超时，请打开地图并移动一下地图后重试" }
        }

        checkSource()
        while (!hasFrame()) {
            TimeUnit.NANOSECONDS.sleep(minOf(timeLeft(), TimeUnit.MILLISECONDS.toNanos(12)))
            checkSource()
        }
        val timeout = timeLeft()
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        copyInto(surface, bitmap, timeout)
        try {
            checkSource()
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    /** A failed copy owns the bitmap and recycles it after any pending native writes end. */
    fun copyInto(surface: Surface, bitmap: Bitmap, timeoutNs: Long) {
        val result = CompletableFuture<Int>()
        try {
            PixelCopy.request(surface, bitmap, { status ->
                if (!result.complete(status)) bitmap.recycle()
            }, handler)
        } catch (failure: RuntimeException) {
            bitmap.recycle()
            throw IllegalStateException("地图画面读取失败", failure)
        }
        var delivered = false
        try {
            val status = result.get(timeoutNs, TimeUnit.NANOSECONDS)
            check(status == PixelCopy.SUCCESS) { "地图画面读取失败（系统返回 $status）" }
            delivered = true
        } catch (_: TimeoutException) {
            error("地图画面复制超时，请重试")
        } finally {
            if (!delivered && !result.cancel(false)) bitmap.recycle()
        }
    }
}
