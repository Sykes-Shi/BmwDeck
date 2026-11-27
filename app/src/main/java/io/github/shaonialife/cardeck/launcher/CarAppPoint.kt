package io.github.shaonialife.cardeck.launcher

import io.github.shaonialife.cardeck.input.CarControllerAction
import io.github.shaonialife.cardeck.render.VideoBarControls

/** Positions are in the projected app, excluding the dock and independent of supersampling. */
internal data class CarAppPoint(val target: VideoBarControls.Target, val x: Int, val y: Int) {
    fun move(action: CarControllerAction, steps: Int, width: Int, height: Int): CarAppPoint {
        // The stick moves the pointer. Wheel turns scroll the list under it.
        val coarse = (minOf(width, height) / 42).coerceAtLeast(1)
        val count = steps.coerceIn(1, 8)
        val dx = when (action) {
            CarControllerAction.LEFT -> -coarse
            CarControllerAction.RIGHT -> coarse
            else -> 0
        }
        val dy = when (action) {
            CarControllerAction.UP -> -coarse
            CarControllerAction.DOWN -> coarse
            else -> 0
        }
        return copy(x = (x + dx * count).coerceIn(0, (width - 1).coerceAtLeast(0)),
            y = (y + dy * count).coerceIn(0, (height - 1).coerceAtLeast(0)))
    }
}
