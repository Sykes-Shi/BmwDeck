package io.github.shaonialife.cardeck.launcher

import org.junit.Assert.*
import org.junit.Test

class ProjectedAppRailStateTest {
    @Test fun expansionBelongsToOneProjectedApp() {
        val rail = ProjectedAppRailState()
        val maps = ProjectedAppRailState.Scene(9, "com.baidu.naviauto")
        assertTrue(rail.select(maps))
        assertFalse(rail.expanded)
        val firstGeneration = rail.generation
        rail.expanded = true
        assertFalse(rail.select(maps.copy()))
        assertEquals(firstGeneration, rail.generation)
        assertTrue(rail.expanded)
        assertTrue(rail.select(ProjectedAppRailState.Scene(4, "music")))
        assertNotEquals(firstGeneration, rail.generation)
        assertFalse(rail.expanded)
        rail.expanded = true
        assertTrue(rail.select(null))
        assertFalse(rail.expanded)
        assertNull(rail.scene)
    }

    @Test fun movingTheSameApplicationToAnotherDisplayStartsCollapsed() {
        val rail = ProjectedAppRailState()
        rail.select(ProjectedAppRailState.Scene(4, "music"))
        rail.expanded = true
        rail.select(ProjectedAppRailState.Scene(7, "music"))
        assertFalse(rail.expanded)
    }
}
