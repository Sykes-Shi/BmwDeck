package io.github.shaonialife.cardeck.launcher

/** Only the manager's current token and visibility are needed, never its editor text. */
internal fun keyboardShownOnDisplay(state: String, display: Int): Boolean {
    if (display <= 0) return false
    val fields = state.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    return fields.size == 2 && "mCurTokenDisplayId=$display" in fields && "mInputShown=true" in fields
}

/** Owned by one projection session. IME relayout may momentarily report hidden after a resize. */
internal class BaiduKeyboardLayout {
    companion object {
        // On a 720px map, lower densities can switch a landscape IME to a taller tablet layout.
        const val SEARCH_DPI = 240
    }
    @Volatile private var revision: Long? = null
    private var hiddenSince: Long? = null

    fun active(scene: Long): Boolean = revision == scene

    @Synchronized fun observe(scene: Long, visible: Boolean, now: Long): Boolean {
        val previous = revision
        if (previous != scene) {
            revision = null
            hiddenSince = null
        }
        if (visible) {
            revision = scene
            hiddenSince = null
        } else if (revision != null) {
            val since = hiddenSince
            if (since == null) hiddenSince = now
            else if (now - since >= 1000) {
                revision = null
                hiddenSince = null
            }
        }
        return previous != revision
    }

    fun density(preferred: Int, scene: Long): Int = if (active(scene)) minOf(preferred, SEARCH_DPI) else preferred
}
