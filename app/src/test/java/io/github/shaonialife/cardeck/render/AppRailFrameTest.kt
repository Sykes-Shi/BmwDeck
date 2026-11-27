package io.github.shaonialife.cardeck.render

import org.junit.Assert.*
import org.junit.Test

class AppRailFrameTest {
    private fun frame(side: AppRailFrame.Side, open: Boolean, sourceW: Int, sourceH: Int, portrait: Boolean = false) =
        AppRailFrame(side, open, true, false, if (portrait) 720 else 1920, if (portrait) 1080 else 720,
            if (portrait) 720 else 168, if (portrait) 100 else 720, sourceW, sourceH, 4, 7L, 267)

    @Test fun allCollapsedHandlesUseLeftAndTheTouchAreaCovers44ProjectedDp() {
        for (side in AppRailFrame.Side.entries) {
            val rail = frame(side, false, 1920, 720)
            assertEquals(AppRailFrame.Side.LEFT, rail.handleSide)
            assertTrue(rail.containsToggle(0, rail.toggleCenterY))
            assertTrue(rail.containsToggle(72, rail.toggleCenterY))
            assertFalse(rail.containsToggle(74, rail.toggleCenterY))
            assertFalse(rail.containsDock(7, 400))
        }
    }

    @Test fun expandedDockReceivesUnmirroredCoordinatesOnEverySide() {
        val right = frame(AppRailFrame.Side.RIGHT, true, 2880, 1080)
        assertTrue(right.containsDock(1752, 289))
        assertFalse(right.containsDock(1751, 289))
        assertEquals(0, right.dockX(1752))
        assertFalse(right.containsToggle(1770, right.toggleCenterY))
        assertEquals(1500, right.inputX(1000))
        assertEquals(600, right.inputY(400))
        val left = frame(AppRailFrame.Side.LEFT, true, 1920, 720)
        assertTrue(left.containsDock(167, 100))
        assertFalse(left.containsDock(168, 100))
        val bottom = frame(AppRailFrame.Side.BOTTOM, true, 720, 1080, portrait = true)
        assertTrue(bottom.containsDock(15, 980))
        assertEquals(1, bottom.dockY(981))
        assertTrue(bottom.containsToggle(bottom.toggleCenterX, 978))
    }

    @Test fun resizeFallbackShowsWholeSourceAndExcludesDecorativeEdge() {
        val rail = frame(AppRailFrame.Side.LEFT, false, 1752, 720).copy(fullscreenBuffer = false)
        assertEquals(84, rail.contentLeft)
        assertEquals(1836, rail.contentRight)
        assertFalse(rail.containsContent(83, 100))
        assertTrue(rail.containsContent(84, 100))
        assertEquals(0, rail.inputX(84))
        assertEquals(1751, rail.inputX(1835))
        val smallTile = rail.copy(map = true, sourceWidth = 640, sourceHeight = 630)
        assertEquals(640.0 / 630, smallTile.contentWidth.toDouble() / smallTile.contentHeight, .002)
        assertEquals(639, smallTile.inputX(smallTile.contentRight - 1))
        assertEquals(629, smallTile.inputY(smallTile.contentBottom - 1))
    }
}
