package io.github.shaonialife.cardeck.render

import kotlin.math.abs

internal object KidsVideoBackdrop {
    const val PACKAGE = "com.ukids.client.phone"
    const val WIDTH = 96
    const val HEIGHT = 48

    data class Bands(val left: Float, val right: Float, val leftColor: Int, val rightColor: Int)

    /** A static menu in a persistent player surface must not acquire a backdrop. */
    class Motion {
        private var previous: IntArray? = null
        private var lastMovement = -10_000L
        fun reset() {
            previous = null
            lastMovement = -10_000L
        }
        fun isMoving(pixels: IntArray, time: Long): Boolean {
            val sample = IntArray(48) { i -> pixels[(5 + i / 8 * 7) * WIDTH + 20 + i % 8 * 8] }
            val old = previous
            previous = sample
            if (old != null && sample.indices.count { i ->
                    val a = sample[i]
                    val b = old[i]
                    maxOf(abs((a ushr 16 and 255) - (b ushr 16 and 255)),
                        abs((a ushr 8 and 255) - (b ushr 8 and 255)), abs((a and 255) - (b and 255))) > 12
                } >= 3) lastMovement = time
            return time - lastMovement < 2000L
        }
    }

    /** Both bars must be black, symmetric, and bound a full-height landscape video. */
    fun detect(pixels: IntArray, displayAspect: Float): Bands? {
        if (pixels.size != WIDTH * HEIGHT || displayAspect < 1.96f) return null
        fun lit(x: Int, y: Int): Boolean {
            val p = pixels[y * WIDTH + x]
            return maxOf(p ushr 16 and 255, p ushr 8 and 255, p and 255) > 15
        }
        fun blackColumn(x: Int) = (1 until HEIGHT - 1).none { y -> lit(x, y) }
        val first = (0 until WIDTH / 3).firstOrNull { !blackColumn(it) } ?: return null
        val last = (WIDTH - 1 downTo WIDTH * 2 / 3).firstOrNull { !blackColumn(it) } ?: return null
        if (first < 4 || abs(first - (WIDTH - 1 - last)) > 2) return null
        val visibleWidth = last - first + 1
        if (visibleWidth * displayAspect / WIDTH !in 1.62f..1.92f) return null
        // An embedded thumbnail or a player menu often leaves additional bands above and below.
        for (y in listOf(2, HEIGHT / 3, HEIGHT * 2 / 3, HEIGHT - 3)) {
            if ((first..last).count { x -> lit(x, y) } < visibleWidth / 4) return null
        }
        fun tint(start: Int): Int {
            var red = 0
            var green = 0
            var blue = 0
            var count = 0
            for (y in 2 until HEIGHT - 2 step 2) for (x in start until start + 8) {
                val p = pixels[y * WIDTH + x]
                red += p ushr 16 and 255
                green += p ushr 8 and 255
                blue += p and 255
                count++
            }
            return ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
        }
        // One spare probe pixel on either edge keeps interpolation off the original picture.
        return Bands((first - 1f) / WIDTH, (last + 2f) / WIDTH, tint(first + 1), tint(last - 8))
    }
}
