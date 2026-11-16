package io.github.shaonialife.cardeck.render

import java.nio.ByteBuffer

/** Limited-range BT.601 for the software AVC encoder, laid out according to its actual plane strides. */
internal class MapClipPixels(private val width: Int, private val height: Int) {
    val argb = IntArray(width * height)
    private val y = ByteArray(argb.size)
    private val u = ByteArray(argb.size / 4)
    private val v = ByteArray(argb.size / 4)

    fun convert() {
        for (row in 0 until height step 2) {
            for (col in 0 until width step 2) {
                val top = row * width + col
                var redSum = 0
                var greenSum = 0
                var blueSum = 0
                for (dy in 0..1) {
                    val start = top + dy * width
                    for (pixel in start..start + 1) {
                        val value = argb[pixel]
                        val red = value ushr 16 and 255
                        val green = value ushr 8 and 255
                        val blue = value and 255
                        y[pixel] = (16 + ((66 * red + 129 * green + 25 * blue + 128) shr 8)).toByte()
                        redSum += red
                        greenSum += green
                        blueSum += blue
                    }
                }
                val chroma = (row / 2) * (width / 2) + col / 2
                u[chroma] = (128 + ((-38 * redSum - 74 * greenSum + 112 * blueSum + 512) shr 10)).toByte()
                v[chroma] = (128 + ((112 * redSum - 94 * greenSum - 18 * blueSum + 512) shr 10)).toByte()
            }
        }
    }

    fun writePlane(index: Int, buffer: ByteBuffer, rowStride: Int, pixelStride: Int) {
        val plane = when (index) { 0 -> y; 1 -> u; 2 -> v; else -> error("无效颜色分量") }
        val columns = if (index == 0) width else width / 2
        val rows = plane.size / columns
        val base = buffer.position()
        if (rowStride == columns && pixelStride == 1) {
            buffer.put(plane)
            return
        }
        for (row in 0 until rows) {
            val offset = base + row * rowStride
            val start = row * columns
            if (pixelStride == 1) {
                buffer.position(offset)
                buffer.put(plane, start, columns)
            } else {
                for (col in 0 until columns) buffer.put(offset + col * pixelStride, plane[start + col])
            }
        }
    }
}
