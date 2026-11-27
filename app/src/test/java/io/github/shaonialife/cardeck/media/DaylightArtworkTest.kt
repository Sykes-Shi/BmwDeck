package io.github.shaonialife.cardeck.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class DaylightArtworkTest {
    private val surface = 0xffeaf0f5.toInt()

    @Test fun transparentAndAchromaticCoversUseNeutralGradients() {
        assertNull(ArtworkTone.daylightColor(intArrayOf(0x00ff0000)))
        assertEquals(0xffd6e0e8.toInt(), ArtworkTone.daylightStops(null, surface).first())
        val dark = ArtworkTone.daylightStops(ArtworkTone.daylightColor(intArrayOf(0xff000000.toInt())), surface)
        val white = ArtworkTone.daylightStops(ArtworkTone.daylightColor(intArrayOf(-1)), surface)
        assertNotEquals(dark.first(), white.first())
        assertTrue(dark.all { abs((it ushr 16 and 255) - (it and 255)) < 6 })
    }

    @Test fun aColoredCoverSurvivesItsNeutralBorderAndDoesNotMixComplementaryHuesToGray() {
        val coral = 0xffdd533b.toInt()
        val blue = 0xff2354bc.toInt()
        val seed = ArtworkTone.daylightColor(IntArray(64) { if (it < 32) -1 else if (it < 55) coral else blue })
        assertEquals(coral, seed)
        val mixed = ArtworkTone.daylightColor(IntArray(64) { if (it < 30) 0xffff0000.toInt() else 0xff00ffff.toInt() })
        assertEquals(0xff00ffff.toInt(), mixed)
        assertNotEquals(ArtworkTone.daylightStops(seed, surface).first(), ArtworkTone.daylightStops(blue, surface).first())
    }

    @Test fun stopsAreVisiblyDistinctAndEveryInterpolatedBackgroundSupportsDaytimeTextAndControls() {
        val text = 0xff14293b.toInt()
        val muted = 0xff394f61.toInt()
        val accent = 0xff175879.toInt()
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val stops = ArtworkTone.daylightStops(0xff000000.toInt() or (r shl 16) or (g shl 8) or b, surface)
            val start = stops.first()
            val end = stops.last()
            val strongestDifference = listOf(0, 8, 16).maxOf { abs((start ushr it and 255) - (end ushr it and 255)) }
            assertTrue(strongestDifference >= 32)
            for (i in 0 until stops.lastIndex) for (step in 0..4) {
                val bg = blend(stops[i], stops[i + 1], step)
                assertTrue((bg + 0.05) / (luma(muted) + 0.05) >= 4.5)
                assertTrue((bg + 0.05) / (luma(text) + 0.05) >= 4.5)
                assertTrue((bg + 0.05) / (luma(accent) + 0.05) >= 3.0)
            }
        }
    }

    @Test fun dayControlColorsSeparateIconsAndProgressFromTheirOwnSurfaces() {
        val accent = 0xff175879.toInt()
        val text = 0xff14293b.toInt()
        val secondary = 0xff394f61.toInt()
        fun trackChannel(shift: Int) = ((secondary ushr shift and 255) * 0.35f + (surface ushr shift and 255) * 0.65f).toInt()
        val track = (trackChannel(16) shl 16) or (trackChannel(8) shl 8) or trackChannel(0)
        assertTrue((luma(track) + 0.05) / (luma(accent) + 0.05) >= 3.0)
        assertTrue((luma(0xffd7e9f0.toInt()) + 0.05) / (luma(accent) + 0.05) >= 3.0)
        assertTrue((luma(0xffd5e1eb.toInt()) + 0.05) / (luma(text) + 0.05) >= 3.0)
        assertTrue((luma(-1) + 0.05) / (luma(accent) + 0.05) >= 4.5)
    }

    @Test fun frostedGlassKeepsCoverHueAndTextContrastAcrossLightAndDarkWallpaper() {
        val dayMuted = 0xff253a4c.toInt()
        val nightMuted = 0xffbcc8d5.toInt()
        val nightSurface = 0xff171f2a.toInt()
        fun composite(color: Int, under: Int, alpha: Int): Int {
            fun channel(shift: Int) = ((color ushr shift and 255) * alpha + (under ushr shift and 255) * (255 - alpha) + 127) / 255
            return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val seed = 0xff000000.toInt() or (r shl 16) or (g shl 8) or b
            val tone = ArtworkTone.fromPixels(intArrayOf(seed))
            for (day in listOf(true, false)) {
                val stops = ArtworkTone.glassStops(seed, tone, if (day) surface else nightSurface, day)
                val foreground = luma(if (day) dayMuted else nightMuted)
                for (stop in stops) {
                    assertTrue(stop ushr 24 in 1..254)
                    // Worst case for each mode, including the sheen at its brightest at night.
                    val pigment = composite(stop, if (day) 0 else -1, stop ushr 24)
                    val background = luma(composite(-1, pigment, if (day) 0 else 12))
                    assertTrue((maxOf(background, foreground) + 0.05) / (minOf(background, foreground) + 0.05) >= 4.5)
                }
            }
        }
        val paper = 0xffd5c6a9.toInt()
        val coral = 0xffd64f3b.toInt()
        val navy = 0xff243b80.toInt()
        for (day in listOf(true, false)) {
            fun tint(cover: Int) = ArtworkTone.glassStops(cover, ArtworkTone.fromPixels(intArrayOf(cover)), if (day) surface else nightSurface, day).first()
            assertTrue((tint(paper) ushr 16 and 255) - (tint(paper) and 255) >= 12)
            assertTrue((tint(coral) ushr 16 and 255) > (tint(coral) and 255))
            assertTrue((tint(navy) ushr 16 and 255) < (tint(navy) and 255))
        }
    }

    private fun blend(a: Int, b: Int, step: Int): Double {
        fun channel(shift: Int) = ((a ushr shift and 255) * (4 - step) + (b ushr shift and 255) * step) / 4
        return luma((channel(16) shl 16) or (channel(8) shl 8) or channel(0))
    }

    private fun luma(color: Int): Double = listOf(0.2126, 0.7152, 0.0722).mapIndexed { index, coeff ->
        val v = (color ushr (16 - index * 8) and 255) / 255.0
        coeff * (if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4))
    }.sum()
}
