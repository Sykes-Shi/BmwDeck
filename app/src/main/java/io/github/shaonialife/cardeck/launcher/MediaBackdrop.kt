package io.github.shaonialife.cardeck.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import io.github.shaonialife.cardeck.media.ArtworkTone

/** A translucent field of sampled album color. The foreground cover is the only bitmap. */
internal class MediaBackdrop(private val glass: View) {
    private var shownDay: Boolean? = null
    private var shownSeed: Int? = null
    private var shownTone: Int? = null

    fun bind(hasArt: Boolean, tone: Int, seed: Int?, palette: ProjectedPalette) {
        if (!hasArt) {
            if (shownDay != null) clear()
            return
        }
        if (shownDay == palette.day && shownSeed == seed && shownTone == tone) return
        shownDay = palette.day
        shownSeed = seed
        shownTone = tone

        val stops = ArtworkTone.glassStops(seed, tone, palette.surface, palette.day)
        val pigment = GradientDrawable(GradientDrawable.Orientation.TL_BR, stops)
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(if (palette.day) 28 else 12, 255, 255, 255), Color.TRANSPARENT),
        )
        glass.background = LayerDrawable(arrayOf(pigment, sheen))
    }

    fun clear() {
        shownDay = null
        shownSeed = null
        shownTone = null
        glass.background = null
    }
}
