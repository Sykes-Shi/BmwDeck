package io.github.shaonialife.cardeck.render

import kotlin.math.min
import kotlin.math.roundToInt

/** The coordinates and sampled buffer of a single encoded application frame. */
internal data class AppRailFrame(
    val side: Side, val expanded: Boolean, val fullscreenBuffer: Boolean, val map: Boolean,
    val width: Int, val height: Int, val dockWidth: Int, val dockHeight: Int,
    val sourceWidth: Int, val sourceHeight: Int, val displayId: Int, val revision: Long, val densityDpi: Int = 160, val sceneGeneration: Long = 0,
) {
    enum class Side { LEFT, RIGHT, BOTTOM }
    private val fit = min(width.toDouble() / sourceWidth.coerceAtLeast(1), height.toDouble() / sourceHeight.coerceAtLeast(1))
    val contentWidth = (sourceWidth * fit).roundToInt().coerceIn(1, width)
    val contentHeight = (sourceHeight * fit).roundToInt().coerceIn(1, height)
    val contentLeft = (width - contentWidth) / 2
    val contentTop = (height - contentHeight) / 2
    val contentRight = contentLeft + contentWidth
    val contentBottom = contentTop + contentHeight

    val rightDock get() = side == Side.RIGHT
    val handleSide get() = if (expanded) side else Side.LEFT

    val controlLength = (min(width, height) * .104f).roundToInt().coerceAtLeast(28)
    val controlThickness = (min(width, height) * .065f).roundToInt().coerceAtLeast(20)
    // The application's real density, not the larger decorative rail density, sets the touch minimum.
    private val hitSize = maxOf(controlThickness, (44f * densityDpi / 160f).roundToInt())
    private val edge = when (handleSide) {
        Side.LEFT -> if (expanded) dockWidth else 0
        Side.RIGHT -> if (expanded) width - dockWidth else width
        Side.BOTTOM -> if (expanded) height - dockHeight else height
    }
    val toggleCenterX = when (handleSide) {
        Side.LEFT -> edge + controlThickness / 2
        Side.RIGHT -> edge - controlThickness / 2
        Side.BOTTOM -> (width * .60f).roundToInt()
    }
    val toggleCenterY = if (handleSide == Side.BOTTOM) edge - controlThickness / 2 else (height * .364f).roundToInt()
    val toggleWidth get() = if (handleSide == Side.BOTTOM) controlLength else controlThickness
    val toggleHeight get() = if (handleSide == Side.BOTTOM) controlThickness else controlLength

    fun containsToggle(x: Int, y: Int): Boolean = when (handleSide) {
        Side.LEFT -> x in edge until edge + hitSize && kotlin.math.abs(y - toggleCenterY) <= maxOf(hitSize, controlLength) / 2
        Side.RIGHT -> x in (edge - hitSize) until edge && kotlin.math.abs(y - toggleCenterY) <= maxOf(hitSize, controlLength) / 2
        Side.BOTTOM -> y in (edge - hitSize) until edge && kotlin.math.abs(x - toggleCenterX) <= maxOf(hitSize, controlLength) / 2
    }

    fun containsDock(x: Int, y: Int) = expanded && when (side) {
        Side.LEFT -> x < dockWidth
        Side.RIGHT -> x >= width - dockWidth
        Side.BOTTOM -> y >= height - dockHeight
    }

    fun containsContent(x: Int, y: Int) = x in contentLeft until contentRight && y in contentTop until contentBottom
    fun inputX(x: Int) = ((x - contentLeft).toLong() * sourceWidth / contentWidth).toInt().coerceIn(0, sourceWidth - 1)
    fun inputY(y: Int) = ((y - contentTop).toLong() * sourceHeight / contentHeight).toInt().coerceIn(0, sourceHeight - 1)
    fun dockX(x: Int) = if (side == Side.RIGHT) x - (width - dockWidth) else x
    fun dockY(y: Int) = if (side == Side.BOTTOM) y - (height - dockHeight) else y
}
