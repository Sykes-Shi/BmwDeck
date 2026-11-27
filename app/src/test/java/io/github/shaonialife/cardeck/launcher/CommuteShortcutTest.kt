package io.github.shaonialife.cardeck.launcher

import org.junit.Assert.*
import org.junit.Test

class CommuteShortcutTest {
    @Test fun savedPlacesUseSeparateBaiduNavigationActions() {
        assertTrue(CommutePlace.HOME.uri.contains("addr=home&"))
        assertTrue(CommutePlace.WORK.uri.contains("addr=company&"))
        assertTrue(CommutePlace.WORK.uri.startsWith("baidumap://map/navi/common?"))
        val gate = CarShortcutGate()
        val home = gate.claim(CarShortcut.NAV, 4, 900, CommutePlace.HOME)!!
        assertNull(gate.claim(CarShortcut.NAV, 4, 910, CommutePlace.HOME))
        val work = gate.claim(CarShortcut.NAV, 4, 920, CommutePlace.WORK)!!
        assertFalse(gate.owns(home))
        assertTrue(gate.owns(work))
    }

    private val header = "TASK 10192:com.baidu.naviauto id=17 userId=0 displayId=6(type=VIRTUAL)"
    private fun view(id: String, bounds: String = "0,0-100,40", flag: String = "V.ED.....") =
        "  android.widget.TextView{af3ce69 $flag ........ $bounds #7f00 app:id/$id}"
    private val panel = view("bnav_rg_icar_guide_panel_container")
    private val road = view("bn_rg_main_guide_road_tv")
    private val remaining = view("bnav_rg_toolbox_remain_time_and_dist")
    private val waiting = view("banav_rg_toolbox_rp_watting_tv")
    private val loading = view("guide_2d_border_tv")

    @Test fun onlyAVisibleGuidancePanelInTheRequestedMapTaskQualifies() {
        assertEquals(CommuteGuidance.PANEL, commuteGuidance("$header\n$panel", 6, 17))
        assertEquals(CommuteGuidance.NONE, commuteGuidance("$header\n${panel.replace("V.ED", "G.ED")}", 6, 17))
        assertEquals(CommuteGuidance.NONE, commuteGuidance("$header\n$panel", 0, 17))
        assertEquals(CommuteGuidance.NONE, commuteGuidance("$header\n$panel", 6, 1))
        assertEquals(CommuteGuidance.NONE, commuteGuidance("$header\n${header.replace("displayId=6", "displayId=60")}\n$panel", 6, 17))
    }

    @Test fun nativeCalculatingAndGuidancePanelsIgnoreStaleOrZeroSizedViews() {
        val ready = listOf(header, panel, waiting, loading.replace("V.ED", "G.ED"), road, remaining)
        assertEquals(CommuteGuidance.ROUTE, commuteGuidance(ready.joinToString("\n"), 6, 17))
        val calculating = listOf(header, panel, waiting, loading, road.replace("V.ED", "G.ED"),
            view("bnav_rg_toolbox_remain_time_and_dist", "0,0-0,70"))
        assertEquals(CommuteGuidance.CALCULATING, commuteGuidance(calculating.joinToString("\n"), 6, 17))
        assertEquals(CommuteGuidance.PANEL, commuteGuidance("$header\n$panel\n$waiting", 6, 17))
    }
}
