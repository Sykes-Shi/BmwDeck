package io.github.shaonialife.cardeck.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Light ring with a dark outline stays legible over a film or a pale results list. */
internal object CarAppPointerArt {
    val bitmap: Bitmap by lazy {
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { image ->
            val canvas = Canvas(image)
            val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            for ((color, line) in listOf(Color.BLACK to 9f, Color.WHITE to 4f)) {
                pen.color = color
                pen.strokeWidth = line
                canvas.drawCircle(32f, 32f, 24f, pen)
                canvas.drawLine(25f, 32f, 39f, 32f, pen)
                canvas.drawLine(32f, 25f, 32f, 39f, pen)
            }
        }
    }
}
