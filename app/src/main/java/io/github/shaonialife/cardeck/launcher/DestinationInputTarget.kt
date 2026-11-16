package io.github.shaonialife.cardeck.launcher

import java.net.URLEncoder

/** The phone is an optional text entry surface for this particular projected search. */
internal class DestinationInputTarget(
    val display: Int,
    private val session: Long,
    private val navigation: Long,
    private val presentation: Long,
    private val screen: Long?,
) {
    private var probes = 0
    private var lastProbe = 0L

    fun matches(display: Int, session: Long, navigation: Long, presentation: Long, screen: Long?): Boolean =
        this.display == display && this.session == session && this.navigation == navigation &&
            this.presentation == presentation && this.screen == screen

    @Synchronized fun claimKeyboardProbe(now: Long): Boolean {
        if (probes >= 3 || (probes > 0 && now - lastProbe < 3500)) return false
        probes++
        lastProbe = now
        return true
    }
}

internal fun baiduKeywordSearchUri(text: String): String? {
    val keyword = text.trim()
    if (keyword.isEmpty() || keyword.length > 128) return null
    val encoded = URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
    return "baidumap://map/place/search?query=$encoded&src=andr.shaonialife.cardeck"
}
