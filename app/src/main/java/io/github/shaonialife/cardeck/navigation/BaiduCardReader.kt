package io.github.shaonialife.cardeck.navigation

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A conservative local reader for a cropped Baidu V22 turn card. Never interprets the road or trip footer. */
internal object BaiduCardReader {
    data class Reading(val maneuver: String, val distanceMeters: Int)

    fun read(card: Bitmap): Reading? =
        if (card.isRecycled) null else read(card.width, card.height, card::getPixel)

    internal fun read(width: Int, height: Int, pixel: (Int, Int) -> Int): Reading? {
        if (height !in 55..220 || width.toDouble() / height !in 2.75..3.10) return null
        fun neutral(x: Int, y: Int, floor: Int): Boolean {
            val c = pixel(x, y)
            val r = c ushr 16 and 255
            val g = c ushr 8 and 255
            val b = c and 255
            return minOf(r, g, b) > floor && maxOf(r, g, b) - minOf(r, g, b) < 42
        }
        fun mask(left: Int, right: Int, bottom: Int, floor: Int) =
            Array(bottom) { y -> BooleanArray(right - left) { x -> neutral(x + left, y, floor) } }
        val arrow = mask(0, (height * .90).roundToInt(), height, 150)
        val turn = readArrow(arrow) ?: return null
        val letters = mask((height * .96).roundToInt(), (width * .94).roundToInt(), (height * .60).roundToInt(), 62)
        val glyphs = mutableListOf<Bounds>()
        var start = -1
        for (x in 0..letters[0].size) {
            val filled = x < letters[0].size && letters.any { it[x] }
            if (filled && start < 0) start = x
            if (!filled && start >= 0) {
                val box = bounds(letters, start, x - 1) ?: return null
                // Dot, slash or dash must never disappear and silently turn a decimal into an integer.
                if (box.w < 2 || box.h < height * .14) return null
                glyphs += box
                start = -1
            }
        }
        if (glyphs.size !in 2..5) return null
        val unit = glyphs.last()
        val digits = glyphs.dropLast(1)
        val digitHeight = digits.first().h
        if (unit.h.toDouble() / digitHeight !in .57.. .92 || unit.w.toDouble() / unit.h !in .75..1.28 ||
            abs(unit.bottom - digits.first().bottom) > maxOf(2, height / 20) ||
            unit.left - digits.last().right < height * .035) return null
        if (identify(letters, unit) != '米') return null
        val value = StringBuilder()
        for (digit in digits) {
            if (digit.h.toDouble() / digitHeight !in .88..1.14 || digit.w.toDouble() / digit.h !in .28.. .93 ||
                abs(digit.bottom - digits.first().bottom) > maxOf(2, height / 20)) return null
            val character = identify(letters, digit) ?: return null
            if (!character.isDigit()) return null
            value.append(character)
        }
        if (value.length > 1 && value[0] == '0') return null
        return Reading(turn, value.toString().toInt())
    }

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val w get() = right - left + 1
        val h get() = bottom - top + 1
    }

    private fun bounds(source: Array<BooleanArray>, first: Int = 0, last: Int = source[0].lastIndex): Bounds? {
        var left = last + 1
        var right = -1
        var top = source.size
        var bottom = -1
        for (y in source.indices) for (x in first..last) if (source[y][x]) {
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = y
        }
        return if (right < 0) null else Bounds(left, top, right, bottom)
    }

    private fun readArrow(source: Array<BooleanArray>): String? {
        val b = bounds(source) ?: return null
        if (b.h !in (source.size * .42).toInt()..(source.size * .78).toInt() || b.w.toDouble() / b.h !in .83..1.15) return null
        fun horizontal(left: Boolean): Boolean {
            fun occupied(x: Int, y: Int) = source[b.top + y][if (left) b.left + x else b.right - x]
            fun verticalSpan(fraction: Double): IntRange? {
                val x = ((b.w - 1) * fraction).roundToInt()
                val ys = (0 until b.h).filter { occupied(x, it) }
                return if (ys.isEmpty()) null else ys.first()..ys.last()
            }
            val tip = verticalSpan(.04) ?: return false
            val shoulder = verticalSpan(.34) ?: return false
            val shaft = verticalSpan(.62) ?: return false
            val foot = verticalSpan(.90) ?: return false
            // Thin tip, flaring arrowhead, horizontal shaft and a vertical stem down the opposite side.
            return tip.first > b.h * .12 && tip.last < b.h * .43 && tip.last - tip.first < b.h * .20 &&
                shoulder.first < b.h * .15 && shoulder.last > b.h * .42 && shoulder.last < b.h * .70 &&
                shaft.first > b.h * .10 && shaft.last < b.h * .49 && foot.last >= b.h * .94 &&
                !occupied((b.w * .50).toInt(), (b.h * .84).toInt())
        }
        val left = horizontal(true)
        val right = horizontal(false)
        return when {
            left && !right -> "左转"
            right && !left -> "右转"
            else -> null
        }
    }

    // Hand-drawn centre lines on a 12 x 18 grid. Generic digits and the metre symbol only.
    // No captured glyphs, screenshots, device-specific fonts or sample distance values are included.
    private val outlines = mapOf(
        '0' to "5,1 8,1 10,3 11,7 11,12 10,16 8,18 5,18 3,16 2,12 2,7 3,3 5,1",
        '1' to "3,5 7,2 8,1 8,18",
        '2' to "2,4 4,2 7,1 10,2 11,5 11,7 9,10 6,13 2,17 2,18 11,18",
        '3' to "2,3 5,1 8,1 10,2 11,5 10,7 8,9 5,9/8,9 10,10 11,12 11,15 9,17 7,18 4,18 2,17",
        '4' to "9,18 9,1 8,1 2,12 2,13 12,13",
        '5' to "11,1 3,1 3,9 6,8 8,8 11,10 11,14 10,16 8,18 4,18 2,17",
        '6' to "10,1 7,1 4,3 2,7 2,12 3,16 5,18 8,18 10,16 11,13 10,10 8,8 5,8 2,10",
        '7' to "2,1 11,1 11,2 7,10 4,18",
        '8' to "6,9 3,7 2,5 3,2 5,1 8,1 10,3 10,6 8,8 6,9 3,11 2,14 3,17 5,18 8,18 10,16 11,13 9,11 6,9",
        '9' to "11,9 8,11 5,11 3,9 2,6 3,3 5,1 8,1 10,3 11,7 10,12 9,15 7,17 4,18",
        '米' to "6,1 6,18/1,8 12,8/2,2 4,6/10,2 8,6/6,8 4,12 1,16/6,8 9,13 12,16",
    )

    private const val COLS = 24
    private const val ROWS = 32

    private fun normalized(mask: Array<BooleanArray>, b: Bounds): BooleanArray =
        BooleanArray(COLS * ROWS) { i ->
            val x = b.left + ((i % COLS + .5) * b.w / COLS).toInt()
            val y = b.top + ((i / COLS + .5) * b.h / ROWS).toInt()
            mask[y][x]
        }

    private val references by lazy {
        outlines.mapValues { (_, outline) ->
            listOf(.67, .92, 1.17).map { radius ->
                val canvas = Array(86) { BooleanArray(60) }
                for (stroke in outline.split('/')) {
                    val points = stroke.split(' ').map { coordinates ->
                        val (x, y) = coordinates.split(',')
                        x.toInt() * 4 to y.toInt() * 4
                    }
                    for ((start, end) in points.zipWithNext()) {
                        val (x1, y1) = start
                        val dx = end.first - x1
                        val dy = end.second - y1
                        for (y in maxOf(0, minOf(y1, y1 + dy) - 5)..minOf(canvas.lastIndex, maxOf(y1, y1 + dy) + 5)) {
                            for (x in maxOf(0, minOf(x1, x1 + dx) - 5)..minOf(canvas[0].lastIndex, maxOf(x1, x1 + dx) + 5)) {
                                val t = (((x - x1) * dx + (y - y1) * dy).toDouble() / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
                                if (hypot(x - x1 - t * dx, y - y1 - t * dy) <= radius * 4) canvas[y][x] = true
                            }
                        }
                    }
                }
                normalized(canvas, checkNotNull(bounds(canvas)))
            }
        }
    }

    private fun identify(mask: Array<BooleanArray>, b: Bounds): Char? {
        val input = normalized(mask, b)
        val scores = references.map { (c, variants) ->
            c to variants.maxOf { template ->
                var intersection = 0
                var union = 0
                for (i in input.indices) {
                    if (input[i] || template[i]) union++
                    if (input[i] && template[i]) intersection++
                }
                intersection.toDouble() / union
            }
        }.sortedByDescending { it.second }
        val (best, score) = scores.first()
        val minimum = if (best == '米') .48 else .60
        return best.takeIf { score >= minimum && score - scores[1].second >= .085 }
    }
}
