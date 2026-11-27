package io.github.shaonialife.cardeck.input

/** Two separate presses towards the visible side of the dock select it. */
internal class DockControllerNavigation {
    enum class Route { CONTENT, ENTER, PREVIOUS, NEXT, ACTIVATE, LEAVE, STAY }

    var active = false
        private set
    var dockOnRight = false
        set(value) {
            if (value != field) reset()
            field = value
        }
    val towardDock: CarControllerAction
        get() = if (dockOnRight) CarControllerAction.RIGHT else CarControllerAction.LEFT
    private data class Press(val action: CarControllerAction, val at: Long)
    private var firstPress: Press? = null

    fun entryDirection(collapsed: Boolean) = if (collapsed) CarControllerAction.LEFT else towardDock

    fun reset() {
        firstPress = null
        active = false
    }

    fun route(action: CarControllerAction, now: Long, collapsed: Boolean = false): Route {
        if (!active) {
            val last = firstPress
            if (action != entryDirection(collapsed)) {
                firstPress = null
                return Route.CONTENT
            }
            if (last == null || last.action != action || now - last.at !in 1..750L) {
                firstPress = Press(action, now)
                return Route.CONTENT
            }
            firstPress = null
            active = true
            return Route.ENTER
        }
        return when (action) {
            CarControllerAction.PREVIOUS, CarControllerAction.UP -> Route.PREVIOUS
            CarControllerAction.NEXT, CarControllerAction.DOWN -> Route.NEXT
            CarControllerAction.CONFIRM -> Route.ACTIVATE.also { reset() }
            CarControllerAction.BACK -> Route.LEAVE.also { reset() }
            CarControllerAction.LEFT, CarControllerAction.RIGHT ->
                if (action == towardDock) Route.STAY else Route.LEAVE.also { reset() }
        }
    }

    fun advance(selected: Int, count: Int, forward: Boolean, steps: Int): Int {
        require(count > 0)
        val delta = steps.coerceIn(1, 8) * if (forward) 1 else -1
        return Math.floorMod(selected + delta, count)
    }
}
