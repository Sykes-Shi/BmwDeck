package io.github.shaonialife.cardeck.launcher

import org.junit.Assert.*
import org.junit.Test

class BaiduDestinationTest {
    @Test fun baiduOnPhoneOrABackgroundTaskCannotConfirmPlacementOnTheCarScreen() {
        val dump = """
            Display #0 (activities from top to bottom):
              topResumedActivity=ActivityRecord{abc u0 com.baidu.BaiduMap/.Main t7}
            Display #42 (activities from top to bottom):
              mResumedActivity: ActivityRecord{def u0 com.other.app/.Top t9}
              * Hist #0: ActivityRecord{fff u0 com.baidu.naviauto/.Old t8}
        """.trimIndent()
        assertTrue(phoneBaiduInForeground(dump))
        assertFalse(phoneBaiduInForeground(dump.replace("com.baidu.BaiduMap/.Main", "com.other.app/.Main")))
        assertEquals(BaiduMapPlacement(), baiduOnMapDisplay(0, dump))
        assertEquals(BaiduMapPlacement(taskId = 8), baiduOnMapDisplay(42, dump))
        assertEquals(BaiduMapPlacement(), baiduOnMapDisplay(41, dump))
        val car = BaiduMapActivity("com.baidu.naviauto/.Maps", 9)
        assertEquals(car, baiduOnMapDisplay(42, dump.replace("com.other.app/.Top", "com.baidu.naviauto/.Maps")).activity)
        assertEquals(car, baiduOnMapDisplay(42, dump.replace("u0 com.other.app/.Top", "324:com.baidu.naviauto/.Maps/u0")).activity)
        assertNull(baiduOnMapDisplay(42, dump.replace("com.other.app/.Top", "com.baidu.naviauto.fake/.Maps")).activity)
    }

    @Test fun selectedPackageMustBeForegroundOnItsOwnDisplay() {
        val dump = """
            Display #0 (activities from top to bottom):
              topResumedActivity=ActivityRecord{abc u0 com.baidu.BaiduMap/.Maps t7}
            Display #42 (activities from top to bottom):
              topResumedActivity=ActivityRecord{123 u0 com.autonavi.minimap/.Map t9}
              * Hist #0: ActivityRecord{def u0 com.baidu.BaiduMap/.Maps t8}
        """.trimIndent()
        assertNull(mapPackagePlacement(42, dump, "com.baidu.BaiduMap").activity)
        assertNull(mapPackagePlacement(0, dump, "com.baidu.BaiduMap").activity)
        assertEquals("com.autonavi.minimap/.Map", mapPackagePlacement(42, dump, "com.autonavi.minimap").activity?.component)
    }

    @Test fun android16DecimalIdentityAndDisplayAreaResumedLinePlaceExistingCarTask() {
        val dump = """
            Display #74 (activities from top to bottom):
              topResumedActivity=ActivityRecord{1712345678 u0 com.baidu.naviauto/.MapsActivity t127}
            Display #0 (activities from top to bottom):
              Resumed: ActivityRecord{234567890 u0 com.baidu.BaiduMap/.OnPhone t703}
        """.trimIndent()
        val expected = BaiduMapPlacement(BaiduMapActivity("com.baidu.naviauto/.MapsActivity", 127), 127)
        assertEquals(expected, baiduOnMapDisplay(74, dump))
        assertEquals(expected, baiduOnMapDisplay(74, dump.replace("topResumedActivity=", "Resumed: ")))
        assertEquals(expected, baiduOnMapDisplay(74, dump.replace("Display #74 (activities from top to bottom):", "Display: mDisplayId=74")))
    }


}
