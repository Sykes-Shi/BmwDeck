package io.github.shaonialife.cardeck.shizuku

import org.junit.Assert.*
import org.junit.Test

class BaiduReturnControlsTest {
    private fun node(p: Int, id: String = "", label: String = "", click: Boolean = false, visible: Boolean = true,
                     top: Int = 0, bottom: Int = 32) = BaiduReturnControls.Node(p, id, label, click, visible, top, bottom)
    private val guidance = listOf(node(-1), node(0, "bnav_rg_mapmode_main_layout_new"), node(1, "bnav_rg_sg_turn_icon"))
    private fun panel(label: String) = guidance + listOf(node(1), node(3), node(4, label = label), node(4, "iv_close", click = true))

    @Test fun matchingGuidanceAndPanelSelectOnlyItsOwnCloseButton() {
        for (label in listOf("为您推荐终点附近停车场", "终点详情", "选择地点")) {
            assertEquals(6, BaiduReturnControls.closeTarget(panel(label)))
        }
        val card = guidance + listOf(node(1, "bnav_rg_arrive_dest_park_root"),
            node(3, "bnav_rg_arrive_dest_park_cancel_btn_layout", click = true), node(4, "bnav_rg_arrive_dest_park_cancel_btn", click = true))
        assertEquals(5, BaiduReturnControls.closeTarget(card))
    }

    @Test fun ordinaryPoiHiddenGuidanceAndAmbiguousOrDangerousActionsArePreserved() {
        val full = panel("终点详情")
        assertNull(BaiduReturnControls.closeTarget(guidance))
        assertNull(BaiduReturnControls.closeTarget(full.toMutableList().also { it[2] = it[2].copy(visible = false) }))
        assertNull(BaiduReturnControls.closeTarget(full.toMutableList().also { it[2] = node(1, "map_poi_turn_list") }))
        assertNull(BaiduReturnControls.closeTarget(full + node(4, "btn_close", click = true)))
        assertNull(BaiduReturnControls.closeTarget(full.toMutableList().also { it[6] = it[6].copy(top = 550, bottom = 620) }))
        assertNull(BaiduReturnControls.closeTarget(full.toMutableList().also { it[6] = it[6].copy(caption = "退出导航") }))
        assertNull(BaiduReturnControls.closeTarget(full.toMutableList().also { it[5] = it[5].copy(caption = "目的地") }))
        assertEquals("结束导航", BaiduReturnControls.caption("×", "结束导航"))
    }
}
