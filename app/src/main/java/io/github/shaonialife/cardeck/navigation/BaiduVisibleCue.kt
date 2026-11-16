package io.github.shaonialife.cardeck.navigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect

/** Bitmap evidence only. The white arrow and distance are copied exactly as Baidu draws them. */
internal object BaiduVisibleCue {
    private const val REFERENCE_WIDTH = 1232
    private const val REFERENCE_HEIGHT = 688

    fun extract(frame: Bitmap): Bitmap? {
        if (frame.isRecycled) return null
        val layout = nightGuidanceLayout(frame.width, frame.height, frame::getPixel) ?: return null
        fun x(value: Int) = (value * layout.width + REFERENCE_WIDTH / 2) / REFERENCE_WIDTH
        fun y(value: Int) = (value * layout.height + REFERENCE_HEIGHT / 2) / REFERENCE_HEIGHT

        val left = x(39)
        val top = y(18)
        val arrow = Rect(left, top, x(136), y(124))
        // The road name begins underneath the first line. Copy the arrow and the line separately.
        val distance = Rect(x(141), top, x(346), y(81))
        val result = Bitmap.createBitmap(distance.right - left, arrow.height(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(0xff0c111a.toInt())
        for (source in listOf(arrow, distance)) {
            val target = Rect(source)
            target.offset(-left, -top)
            canvas.drawBitmap(frame, source, target, null)
        }
        return result
    }

    private class Layout(val width: Int, val height: Int, val pixel: (Int, Int) -> Int) {
        fun supported() = width >= 640 && height >= 360 && width.toDouble() / height in 1.65..1.95

        fun fraction(left: Int, top: Int, right: Int, bottom: Int, step: Int, tone: (Int, Int, Int) -> Boolean): Double {
            var hits = 0
            var total = 0
            for (y in top until bottom step step) {
                val sourceY = y * height / REFERENCE_HEIGHT
                for (x in left until right step step) {
                    val color = pixel(x * width / REFERENCE_WIDTH, sourceY)
                    if (tone(color ushr 16 and 255, color ushr 8 and 255, color and 255)) hits++
                    total++
                }
            }
            return hits.toDouble() / total
        }
    }

    /** The V22 night layout seen on the field device. Unknown layouts intentionally have no match. */
    internal fun matchesNightGuidance(width: Int, height: Int, pixel: (Int, Int) -> Int): Boolean =
        nightGuidanceLayout(width, height, pixel) != null

    private fun nightGuidanceLayout(width: Int, height: Int, pixel: (Int, Int) -> Int): Layout? {
        val frame = Layout(width, height, pixel)
        val panel = if (frame.supported()) frame else widePanel(width, height, pixel) ?: return null
        val matches = with(panel) {
            fun dark(r: Int, g: Int, b: Int) = maxOf(r, g, b) < 55
            fun white(r: Int, g: Int, b: Int) =
                minOf(r, g, b) > 157 && maxOf(r, g, b) - minOf(r, g, b) < 45
            fun cyan(r: Int, g: Int, b: Int) = g > 105 && g - r > 55 && b - r > 60

            // Dark map outside the card, dark card margins and the neighbouring cyan speed ring.
            if (frame.fraction(465, 242, 1195, 660, 25) { r, g, b -> maxOf(r, g, b) < 145 } < 0.60 ||
                fraction(60, 14, 331, 22, 3, ::dark) < 0.92 ||
                fraction(380, 61, 405, 148, 3, ::dark) < 0.92 ||
                fraction(450, 9, 547, 109, 3, ::cyan) !in 0.035..0.23
            ) return@with false

            // Require both guidance fields, the road-name row, and both groups of trip-summary text.
            fraction(42, 20, 130, 127, 3, ::white) in 0.035..0.32 &&
                fraction(146, 22, 333, 80, 3, ::white) in 0.030..0.40 &&
                fraction(146, 85, 360, 124, 3, ::white) in 0.025..0.40 &&
                fraction(44, 163, 205, 191, 3, ::white) in 0.035..0.55 &&
                fraction(286, 163, 405, 191, 3, ::white) in 0.035..0.55
        }
        return panel.takeIf { matches }
    }

    /**
     * In V22's wide full-screen map the road canvas grows sideways, while the guidance panel
     * follows the device density. Measure the left arc of the neighbouring speed dial to obtain
     * its scale. The same arrow, distance and trip-row checks then apply with these coordinates.
     */
    private fun widePanel(width: Int, height: Int, pixel: (Int, Int) -> Int): Layout? {
        if (width < 640 || height < 360 || width.toDouble() / height !in 2.2..2.65) return null
        val edges = ArrayList<Int>(9)
        for (row in 81..113 step 4) {
            val y = height * row / 1000
            val edge = (height * 60 / 100 .. height * 79 / 100).firstOrNull { x ->
                val color = pixel(x, y)
                val r = color ushr 16 and 255
                val g = color ushr 8 and 255
                val b = color and 255
                g > 105 && g - r > 55 && b - r > 60
            }
            if (edge != null) edges.add(edge)
        }
        if (edges.size < 6) return null
        edges.sort()
        val anchor = edges[edges.size / 2]
        if (edges.count { kotlin.math.abs(it - anchor) <= maxOf(2, height / 150) } < 6) return null
        // On the reference map the arc begins 449 px from the left edge.
        return Layout((anchor * REFERENCE_WIDTH + 224) / 449, (anchor * REFERENCE_HEIGHT + 224) / 449, pixel)
    }

    fun isArrivalSummary(frame: Bitmap): Boolean =
        !frame.isRecycled && matchesDayArrival(frame.width, frame.height, frame::getPixel)

    /** Daytime arrival page on V22: two-tone heading, trip feedback row and its three coloured faces. */
    internal fun matchesDayArrival(width: Int, height: Int, pixel: (Int, Int) -> Int): Boolean =
        with(Layout(width, height, pixel)) {
            if (!supported()) return@with false
            fun ink(r: Int, g: Int, b: Int) = maxOf(r, g, b) < 120
            fun ratingFace(r: Int, g: Int, b: Int) = r > 182 && r - g > 11 && r - b > 25

            fraction(250, 155, 383, 174, 2) { r, g, b -> r > 160 && g > 203 && b > 224 && b - r > 15 } > 0.85 &&
                fraction(41, 175, 141, 195, 2, ::ink) in 0.23..0.56 &&
                fraction(147, 175, 249, 195, 2) { r, g, b -> b > 155 && b - r > 75 && b - g > 43 } in 0.36..0.70 &&
                fraction(41, 232, 177, 252, 2, ::ink) in 0.19..0.48 &&
                fraction(207, 230, 231, 250, 2, ::ratingFace) in 0.29..0.72 &&
                fraction(298, 230, 322, 250, 2, ::ratingFace) in 0.30..0.78 &&
                fraction(387, 230, 411, 250, 2, ::ratingFace) in 0.19..0.58
        }
}
