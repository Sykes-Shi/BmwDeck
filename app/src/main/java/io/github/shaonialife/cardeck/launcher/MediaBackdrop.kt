package io.github.shaonialife.cardeck.launcher

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import io.github.shaonialife.cardeck.media.ArtworkTone

/** 复用后台已解码的小封面；柔化交给渲染器，主线程不重新解码或逐像素处理。 */
internal class MediaBackdrop(private val image: ImageView, private val veil: View) {
    private var blurredArt: Bitmap? = null
    private var shownColor: Int? = null
    private var shownDay: Boolean? = null

    init {
        image.alpha = 0f
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        val radius = 28f * image.resources.displayMetrics.density
        image.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
    }

    fun bind(art: Bitmap?, color: Int, daylightColor: Int?, palette: ProjectedPalette) {
        // Daylight uses opaque cover-derived colors. Only night needs a second, blurred cover.
        val blurred = if (palette.day) null else art
        if (blurredArt !== blurred) {
            blurredArt = blurred
            image.setImageBitmap(blurred)
            image.alpha = if (blurred == null) 0f else 0.55f
        }
        val seed = if (palette.day) daylightColor.takeIf { art != null } else color
        if (shownColor != seed || shownDay != palette.day) {
            shownDay = palette.day
            shownColor = seed
            image.setBackgroundColor(color)
            val surface = palette.surface
            val stops = if (palette.day) ArtworkTone.daylightStops(seed, surface) else intArrayOf(
                ColorUtils.setAlphaComponent(ColorUtils.blendARGB(color, surface, 0.42f), 238), surface,
            )
            veil.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                stops,
            )
        }
    }

    fun clear() {
        blurredArt = null
        shownDay = null
        image.setImageDrawable(null)
        image.alpha = 0f
    }
}
