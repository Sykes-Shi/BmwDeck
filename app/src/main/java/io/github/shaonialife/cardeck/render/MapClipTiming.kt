package io.github.shaonialife.cardeck.render

internal object MapClipTiming {
    const val FPS = 10
    const val FRAME_US = 1_000_000L / FPS
    const val DURATION_US = 5_000_000L
    val frameCount = (DURATION_US / FRAME_US).toInt()

    /** Dropped sampling opportunities retain wall-clock timing; never catch up by encoding a burst. */
    fun slot(elapsedUs: Long): Int = (elapsedUs.coerceIn(0, DURATION_US) / FRAME_US).toInt()
    fun pts(slot: Int): Long = slot * FRAME_US
}
