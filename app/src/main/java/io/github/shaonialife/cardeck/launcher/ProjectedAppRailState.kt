package io.github.shaonialife.cardeck.launcher

/** Each foreground app owns the expansion choice until the projected page changes. */
internal class ProjectedAppRailState {
    data class Scene(val display: Int, val key: String)

    var scene: Scene? = null
        private set
    var generation = 0L
        private set
    var expanded = false

    fun select(next: Scene?): Boolean {
        if (scene == next) return false
        generation++
        scene = next
        expanded = false
        return true
    }
}
