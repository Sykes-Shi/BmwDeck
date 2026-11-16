package io.github.shaonialife.cardeck.media

/** 从小样本提取深色基调，白封面也不会把文字背景冲成白色。 */
internal object ArtworkTone {
    const val DEFAULT = 0xFF111820.toInt()

    /** Choose a hue family from the original artwork before applying the night-time darkening. */
    fun daylightColor(pixels: IntArray): Int? {
        val weights = IntArray(12)
        val red = IntArray(12)
        val green = IntArray(12)
        val blue = IntArray(12)
        var lumaTotal = 0L
        var opaque = 0
        for (pixel in pixels) {
            if (pixel ushr 24 < 128) continue
            val r = pixel ushr 16 and 255
            val g = pixel ushr 8 and 255
            val b = pixel and 255
            opaque++
            lumaTotal += (54 * r + 183 * g + 19 * b) / 256
            val high = maxOf(r, g, b)
            val chroma = high - minOf(r, g, b)
            if (chroma < 24 || high < 40) continue
            val sector = when (high) {
                r -> (g - b).toDouble() / chroma
                g -> 2.0 + (b - r).toDouble() / chroma
                else -> 4.0 + (r - g).toDouble() / chroma
            }
            val bin = ((sector + 6.0) * 2 + 0.5).toInt() % 12
            weights[bin] += chroma
            red[bin] += r * chroma
            green[bin] += g * chroma
            blue[bin] += b * chroma
        }
        if (opaque == 0) return null
        val index = weights.indices.maxBy { weights[it] }
        val weight = weights[index]
        if (weight < opaque * 6) {
            val gray = (lumaTotal / opaque).toInt()
            return rgb(gray, gray, gray)
        }
        return rgb(red[index] / weight, green[index] / weight, blue[index] / weight)
    }

    /** Opaque stops keep the light theme readable over both dark and white cover art. */
    fun daylightStops(seed: Int?, surface: Int): IntArray {
        if (seed == null) return intArrayOf(0xFFD6E0E8.toInt(), surface)
        val r = seed ushr 16 and 255
        val g = seed ushr 8 and 255
        val b = seed and 255
        val low = minOf(r, g, b)
        val chroma = maxOf(r, g, b) - low
        if (chroma < 24) {
            val gray = 191 + low * 22 / 255
            return intArrayOf(rgb(gray, gray + 2, gray + 4), mix(seed, -1, 92))
        }
        fun tint(value: Int) = 164 + (value - low) * 84 / chroma
        var start = rgb(tint(r), tint(g), tint(b))
        // Only brighten hues whose light background would otherwise compromise secondary text.
        while (relativeLuminance(start) < 0.515) start = mix(start, -1, 4)
        return intArrayOf(start, mix(start, -1, 42), mix(start, -1, 85))
    }

    private fun rgb(r: Int, g: Int, b: Int) = (255 shl 24) or (r shl 16) or (g shl 8) or b

    private fun mix(a: Int, b: Int, percent: Int): Int {
        fun channel(shift: Int) = (((a ushr shift and 255) * (100 - percent)) + (b ushr shift and 255) * percent + 50) / 100
        return rgb(channel(16), channel(8), channel(0))
    }

    private fun relativeLuminance(color: Int): Double {
        fun linear(shift: Int): Double {
            val value = (color ushr shift and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    }

    fun fromPixels(pixels: IntArray): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0
        pixels.forEach { color ->
            if ((color ushr 24) >= 128) {
                red += color ushr 16 and 255
                green += color ushr 8 and 255
                blue += color and 255
                count++
            }
        }
        if (count == 0) return DEFAULT
        fun channel(sum: Long, base: Int): Int = ((sum / count) * 0.30 + base * 0.70).toInt().coerceIn(0, 95)
        return (255 shl 24) or (channel(red, 17) shl 16) or (channel(green, 24) shl 8) or channel(blue, 32)
    }
}
