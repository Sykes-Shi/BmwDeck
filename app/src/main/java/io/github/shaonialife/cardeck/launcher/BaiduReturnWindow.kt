package io.github.shaonialife.cardeck.launcher

/** One return to the car may pass through Settings, the dashboard and the expanded map. */
internal class BaiduReturnWindow(armedAt: Long) {
    private val expires = armedAt + 15 * 60_000L
    private var observing = false
    private var scenes = 0

    fun isLive(now: Long): Boolean = now < expires

    @Synchronized fun start(now: Long): Boolean {
        if (observing || scenes >= 4 || !isLive(now)) return false
        scenes++
        observing = true
        return true
    }

    @Synchronized fun changedScene(now: Long): Boolean {
        observing = false
        return scenes < 4 && isLive(now)
    }
}
