package io.github.shaonialife.cardeck.launcher

import org.junit.Assert.*
import org.junit.Test

class DestinationInputTargetTest {
    @Test fun onlyVisibleKeyboardBoundToTheCarMapCanConsumeBack() {
        val current = "mInputShown=true\nmCurTokenDisplayId=118\n"
        assertTrue(keyboardShownOnDisplay(current, 118))
        assertFalse(keyboardShownOnDisplay(current, 119))
        assertFalse(keyboardShownOnDisplay(current.replace("true", "false"), 118))
        assertFalse(keyboardShownOnDisplay("mInputShown=true", 118))
        assertFalse(keyboardShownOnDisplay("mCurTokenDisplayId=0\nmInputShown=true", 0))
        assertFalse(keyboardShownOnDisplay(current + "mCurTokenDisplayId=16\n", 118))
    }

    @Test fun searchDensityTracksItsOwnSceneAndRestoresAfterTheImeSettles() {
        val layout = BaiduKeyboardLayout()
        assertEquals(300, layout.density(300, 5))
        assertTrue(layout.observe(5, true, 0))
        assertEquals(240, layout.density(300, 5))
        assertEquals(180, layout.density(180, 5))
        assertFalse(layout.observe(5, false, 450))
        assertFalse(layout.observe(5, true, 700))
        assertFalse(layout.observe(5, false, 900))
        assertFalse(layout.observe(5, false, 1200))
        assertTrue(layout.observe(5, false, 2000))
        assertEquals(300, layout.density(300, 5))
        assertTrue(layout.observe(5, true, 2400))
        assertEquals(300, layout.density(300, 6))
        assertTrue(layout.observe(6, false, 2500))
    }
}
