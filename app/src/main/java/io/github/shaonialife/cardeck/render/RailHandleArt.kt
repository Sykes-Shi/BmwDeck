package io.github.shaonialife.cardeck.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Path

/** Drawn at the output edge, so it is also visible over a fullscreen third-party map. */
internal object RailHandleArt {
    fun bitmap(direction: Int, day: Boolean = false): Bitmap {
        val horizontal = direction == 0 || direction == 180
        val image = Bitmap.createBitmap(if (horizontal) 62 else 98, if (horizontal) 98 else 62, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = image.width.toFloat()
        val h = image.height.toFloat()
        // A soft edge keeps the control legible on both a white map and a dark album cover.
        p.color = Color.argb(if (day) 60 else 95, 14, 24, 37)
        canvas.drawRoundRect(1.5f, 3f, w - 1.5f, h - 1f, 19f, 19f, p)
        p.color = Color.WHITE
        val top = if (day) 0xF0EEF5F9.toInt() else 0xED354657.toInt()
        val bottom = if (day) 0xE6D4E2EC.toInt() else 0xEA202D3C.toInt()
        p.shader = LinearGradient(0f, 0f, w, h, top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(2.5f, 1.5f, w - 2.5f, h - 3.5f, 18f, 18f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.9f
        p.color = if (day) 0xC27D95A7.toInt() else 0xC58FA5B8.toInt()
        canvas.drawRoundRect(2.5f, 1.5f, w - 2.5f, h - 3.5f, 18f, 18f, p)
        canvas.translate(w / 2, h / 2)
        canvas.rotate(direction.toFloat())
        p.color = if (day) 0xFF27465D.toInt() else 0xFFE2EEF7.toInt()
        p.strokeWidth = 2.9f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        canvas.drawRoundRect(-19f, -14f, 19f, 14f, 4.5f, 4.5f, p)
        canvas.drawLine(-8f, -13f, -8f, 13f, p)
        p.color = if (day) 0xFF175879.toInt() else 0xFFB6D7EF.toInt()
        p.strokeWidth = 3.2f
        canvas.drawPath(Path().apply { moveTo(1f, -5f); lineTo(7f, 0f); lineTo(1f, 5f) }, p)
        return image
    }

    fun direction(frame: AppRailFrame): Int = when (frame.handleSide) {
        AppRailFrame.Side.LEFT -> if (frame.expanded) 180 else 0
        AppRailFrame.Side.RIGHT -> if (frame.expanded) 0 else 180
        AppRailFrame.Side.BOTTOM -> if (frame.expanded) 90 else 270
    }
}
