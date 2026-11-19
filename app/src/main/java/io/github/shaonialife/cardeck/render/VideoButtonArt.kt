package io.github.shaonialife.cardeck.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/** Shared small textures. Only the rectangle occupied by each button is composited. */
internal object VideoButtonArt {
    val bitmaps by lazy { VideoBarControls.Action.entries.associateWith(::draw) }

    private fun draw(action: VideoBarControls.Action): Bitmap {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(39, 43, 48)
        canvas.drawRoundRect(2f, 2f, 126f, 126f, 26f, 26f, paint)
        paint.color = Color.WHITE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 6f
        when (action) {
            VideoBarControls.Action.BACK -> {
                paint.style = Paint.Style.STROKE
                canvas.drawPath(Path().apply { moveTo(66f, 22f); lineTo(44f, 43f); lineTo(66f, 64f) }, paint)
                canvas.drawLine(46f, 43f, 87f, 43f, paint)
            }
            VideoBarControls.Action.DESKTOP -> {
                for (x in listOf(42f, 68f)) for (y in listOf(22f, 48f)) {
                    canvas.drawRoundRect(x, y, x + 19f, y + 19f, 4f, 4f, paint)
                }
            }
            VideoBarControls.Action.PAUSE -> {
                canvas.drawRoundRect(45f, 23f, 58f, 65f, 3f, 3f, paint)
                canvas.drawRoundRect(70f, 23f, 83f, 65f, 3f, 3f, paint)
            }
        }
        paint.style = Paint.Style.FILL
        paint.textSize = 33f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(action.label, 64f, 108f, paint)
        return bitmap
    }
}
