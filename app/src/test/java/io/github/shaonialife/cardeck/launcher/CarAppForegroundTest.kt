package io.github.shaonialife.cardeck.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class CarAppForegroundTest {
    private val player = "ActivityRecord{123456 u0 com.youku.phone/.Player t82}"
    private val other = "ActivityRecord{ef u0 example.app/.Main t83}"
    private fun resumed(display: Int, record: String) =
        "Display #$display (activities from top to bottom):\n  topResumedActivity=$record"

    @Test fun cheyuInThePhoneOrBackgroundNeverEnablesProjectedControls() {
        val cheyu = "ActivityRecord{acd u0 com.bytedance.byteautoservice3/com.bytedance.auto.lite.apps.framework.ui.MainActivity t111}"
        assertEquals(null, CarAppForeground.foregroundOnDisplay(resumed(0, cheyu) + "\n" + resumed(22, other), 22))
        assertEquals(null, CarAppForeground.foregroundOnDisplay(resumed(22, other) + "\n  Resumed: " + cheyu, 22))
        assertEquals(CarAppForeground.Foreground("com.bytedance.byteautoservice3/com.bytedance.auto.lite.apps.framework.ui.MainActivity", 111),
            CarAppForeground.foregroundOnDisplay(resumed(0, other) + "\n" + resumed(22, cheyu), 22))
    }

    @Test fun backTargetsOnlyTheResumedActivityOnTheCarDisplay() {
        val phone = resumed(0, player)
        val car = resumed(265, player.replace("u0 com.youku.phone/.Player", "118:com.youku.phone/com.youku.ui.activity.DetailActivity/u0"))
        assertEquals(CarAppForeground.Foreground("com.youku.phone/com.youku.ui.activity.DetailActivity", 82),
            CarAppForeground.foregroundOnDisplay("$phone\n$car", 265))
        assertEquals(null, CarAppForeground.foregroundOnDisplay("$phone\n$car", 0))
        assertEquals(null, CarAppForeground.foregroundOnDisplay("$phone\n${resumed(265, other)}\n  Resumed: $player", 265))
    }

    @Test fun netEaseMustBeTheResumedTaskOnTheCarDisplay() {
        val car = "ActivityRecord{123 u0 com.netease.cloudmusic.iot/.Main t12}"
        val phoneMusic = resumed(0, car)
        val otherCarApp = resumed(3, other)
        assertEquals("example.app/example.app.Main",
            CarAppForeground.topOnDisplay("$phoneMusic\n$otherCarApp\n  Resumed: $car", 3)?.component)
        assertEquals("com.netease.cloudmusic.iot/com.netease.cloudmusic.iot.Main",
            CarAppForeground.topOnDisplay("$otherCarApp\n" + resumed(4, car), 4)?.component)
        assertEquals(null, CarAppForeground.foregroundOnDisplay(resumed(4, car), 4))
        assertEquals(null, CarAppForeground.topOnDisplay(phoneMusic, 3))
    }
}
