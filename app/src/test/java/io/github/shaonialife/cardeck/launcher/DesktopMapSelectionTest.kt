package io.github.shaonialife.cardeck.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopMapSelectionTest {
    private val car = "${BaiduCruise.PACKAGE}/.Main"
    private val phone = "${BaiduCruise.PHONE_PACKAGE}/.Main"
    private val oldMap = "com.autonavi.minimap/.Main"
    private val music = "com.netease.cloudmusic.iot/.Main"

    @Test fun oldSelectionsMoveToTheCarMapAndKeepMusic() {
        val installed = setOf(car, phone, oldMap, music)
        assertEquals(setOf(car, music), selectedDesktopComponents(installed, setOf(phone, oldMap, music)))
        assertEquals(setOf(car), selectedDesktopComponents(installed, null))
        assertEquals(emptySet<String>(), selectedDesktopComponents(installed, emptySet()))
    }

    @Test fun missingCarMapDoesNotSubstituteThePhoneApp() {
        val installed = setOf(phone, music)
        assertEquals(setOf(music), selectedDesktopComponents(installed, setOf(oldMap, phone, music)))
        assertEquals(emptySet<String>(), selectedDesktopComponents(installed, null))
    }
}
