package io.github.shaonialife.cardeck.launcher

import org.junit.Assert.*
import org.junit.Test

class BaiduReturnWindowTest {
    @Test fun waitingInSettingsThenExpandingTheMapStartsOnlyOneCheckPerScene() {
        val window = BaiduReturnWindow(10)
        assertTrue(window.start(120_010))
        assertFalse(window.start(120_020))
        repeat(3) {
            assertTrue(window.changedScene(120_030L + it))
            assertTrue(window.start(120_034L + it))
        }
        assertFalse(window.changedScene(120_050))
        assertFalse(window.start(120_060))
    }

    @Test fun unusedReturnExpiresAndNewConnectionGetsItsOwnWindow() {
        assertFalse(BaiduReturnWindow(0).start(15 * 60_000L))
        assertTrue(BaiduReturnWindow(15 * 60_000L).start(15 * 60_000L))
    }
}
