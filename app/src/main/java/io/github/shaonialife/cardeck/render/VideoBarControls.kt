package io.github.shaonialife.cardeck.render

import kotlin.math.abs

internal object VideoBarControls {
    val packages = setOf("com.ukids.client.phone", "com.youku.phone")
    const val WIDTH = 96
    const val HEIGHT = 48

    enum class Axis { SIDES, TOP_BOTTOM }
    enum class Action(val label: String) { BACK("返回"), DESKTOP("桌面"), PAUSE("暂停") }

    /** Boundaries are fractions of the app (without the dock), measured from the left or top. */
    data class Bars(val axis: Axis, val firstEnd: Float, val secondStart: Float)
    data class Target(val generation: Long, val revision: Long, val display: Int, val hi: Boolean, val component: String, val task: Int) {
        val pkg get() = component.substringBefore('/')
    }
    data class Button(val action: Action, val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
    }
    data class Hit(val target: Target, val button: Button)

    /** Require two full opposite black margins and picture detail across the other dimension. */
    fun detect(pixels: IntArray, aspect: Float): Bars? {
        if (pixels.size != WIDTH * HEIGHT || aspect <= 0f) return null
        fun lit(x: Int, y: Int): Boolean {
            val p = pixels[y * WIDTH + x]
            return maxOf(p ushr 16 and 255, p ushr 8 and 255, p and 255) > 15
        }
        fun pair(axis: Axis): Bars? {
            val sides = axis == Axis.SIDES
            val size = if (sides) WIDTH else HEIGHT
            val across = if (sides) HEIGHT else WIDTH
            fun light(a: Int, b: Int) = if (sides) lit(a, b) else lit(b, a)
            fun empty(a: Int) = (0 until across).none { light(a, it) }
            val first = (0 until size / 2).firstOrNull { !empty(it) } ?: return null
            val last = (size - 1 downTo size / 2).firstOrNull { !empty(it) } ?: return null
            if (minOf(first, size - 1 - last) < 2 || abs(first - (size - 1 - last)) > 2) return null
            val span = last - first + 1
            val pictureAspect = if (sides) aspect * span / size else aspect * size / span
            if (pictureAspect !in 1.18f..3.05f) return null
            for (b in intArrayOf(1, across / 3, across * 2 / 3, across - 2)) {
                if ((first..last).count { light(it, b) } < span / 4) return null
            }
            // One spare low-resolution pixel at each boundary accounts for resampling.
            return Bars(axis, (first - 1f) / size, (last + 2f) / size)
        }
        return pair(Axis.SIDES) ?: pair(Axis.TOP_BOTTOM)
    }

    /** A scrolling menu or a single scene transition cannot open the controls on its own. */
    class Motion {
        private var lastFrame: IntArray? = null
        private var changes = 0
        private var movingAt = -10_000L
        private var previousBars: Bars? = null

        fun reset() {
            lastFrame = null
            changes = 0
            movingAt = -10_000L
            previousBars = null
        }

        fun playing(frame: IntArray, bars: Bars, now: Long, sessionPlaying: Boolean?): Boolean {
            if (sessionPlaying == false || previousBars != bars) reset()
            if (sessionPlaying == false) return false
            previousBars = bars
            val old = lastFrame
            lastFrame = frame
            val changed = old != null && (0 until 48).count { i ->
                val at = (6 + i / 8 * 7) * WIDTH + 16 + i % 8 * 9
                val a = frame[at]
                val b = old[at]
                maxOf(abs((a ushr 16 and 255) - (b ushr 16 and 255)), abs((a ushr 8 and 255) - (b ushr 8 and 255)),
                    abs((a and 255) - (b and 255))) > 14
            } >= 5
            if (changed) {
                changes++
                if (changes >= 2) movingAt = now
            } else {
                changes = 0
            }
            return now - movingAt < (if (sessionPlaying == true) 3500L else 1350L)
        }
    }

    /** Output stream coordinates also drive touch routing. Narrow bars have no hit area. */
    fun buttons(bars: Bars, dock: Int, width: Int, height: Int, canPause: Boolean): List<Button> {
        val appW = width - dock
        if (appW <= 0 || height <= 0) return emptyList()
        val sides = bars.axis == Axis.SIDES
        val extent = if (sides) appW.toFloat() else height.toFloat()
        val thickness = minOf(bars.firstEnd, 1f - bars.secondStart) * extent
        val shortSide = minOf(appW, height)
        val size = minOf(shortSide * .13f, thickness * .80f)
        if (size < shortSide * .075f) return emptyList()
        val first = bars.firstEnd * extent / 2
        val second = (1f + bars.secondStart) * extent / 2
        fun button(action: Action, edge: Float, offset: Float): Button {
            val x = dock + if (sides) edge else appW / 2f + offset
            val y = if (sides) height / 2f + offset else edge
            return Button(action, x - size / 2, y - size / 2, x + size / 2, y + size / 2)
        }
        return buildList {
            add(button(Action.BACK, first, 0f))
            add(button(Action.DESKTOP, second, if (canPause) size * .65f else 0f))
            if (canPause) add(button(Action.PAUSE, second, -size * .65f))
        }
    }

    /** Own a gesture only if it began inside a button. Dragging out cancels until finger-up. */
    class Gesture {
        private var slot = -1
        private var held: Hit? = null

        val active get() = slot >= 0
        fun owns(finger: Int) = slot == finger
        fun heldButton() = held?.button

        fun reset() {
            slot = -1
            held = null
        }

        fun handle(finger: Int, down: Boolean, hit: Hit?, appPressed: Boolean, tap: (Hit) -> Unit): Boolean {
            if (slot < 0) {
                if (!down || appPressed || hit == null) return false
                slot = finger
                held = hit
                return true
            }
            if (finger != slot) {
                if (down) held = null
                return true
            }
            if (held != hit) held = null
            if (!down) {
                val selected = held
                reset()
                selected?.let(tap)
            }
            return true
        }
    }
}
