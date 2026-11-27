package io.github.shaonialife.cardeck.shizuku

/** Keep only Baidu's control identifiers and a small set of fixed UI captions. */
internal object BaiduReturnControls {
    val closeLabels = setOf("关闭", "收起", "关闭推荐", "关闭停车推荐", "关闭面板", "关闭页面", "×")
    val returnLabels = setOf("返回导航", "回到导航")
    private val conflictingLabels = setOf("退出导航", "结束导航", "停止导航", "设为终点", "替换终点", "开始导航", "去停车")
    val headers = setOf("为您推荐终点附近停车场", "终点附近停车场", "终点停车推荐", "终点停车", "选择终点", "选择目的地", "选择地点", "终点详情", "目的地详情")
    private val turnControls = setOf("bnav_rg_sg_turn_icon", "bnav_rg_sg_next_turn_icon", "bnav_rg_enlarge_turn_icon",
        "bnav_rg_hw_turn_icon", "bnav_rg_float_turn_icon", "bnav_rg_enlarge_next_road",
        "bnav_rg_enlarge_guide_layout", "bnav_rg_default_guide_panel_lottie_anim_view", "bnav_rg_small_screen_guide_panel_container")
    private val closeIds = setOf("bnav_rg_close_iv", "close_btn", "btn_close", "iv_close", "close_iv", "close_icon", "panel_close")
    private const val PARK_ROOT = "bnav_rg_arrive_dest_park_root"
    private val parkClose = setOf("bnav_rg_arrive_dest_park_cancel_btn", "bnav_rg_arrive_dest_park_cancel_btn_layout")

    data class Node(
        val parent: Int, val id: String, val caption: String, val clickable: Boolean, val visible: Boolean,
        val top: Int = 0, val bottom: Int = 1,
    )

    fun caption(text: CharSequence?, description: CharSequence?): String {
        val labels = listOfNotNull(text, description).filter { it.length <= 30 }.map { it.toString().trim() }
        return labels.firstOrNull { it in conflictingLabels } ?: labels.firstOrNull { it in headers || it in returnLabels || it in closeLabels }.orEmpty()
    }

    /** No generic back action. A standalone POI, route preview, normal guidance and exit confirmation never qualify. */
    fun closeTarget(nodes: List<Node>): Int? {
        val visible = nodes.indices.filter { nodes[it].visible }
        if (visible.none { nodes[it].id == "bnav_rg_mapmode_main_layout_new" } ||
            visible.none { nodes[it].id in turnControls }) return null
        fun within(child: Int, ancestor: Int): Boolean {
            var at = child
            while (at > ancestor) at = nodes[at].parent
            return at == ancestor
        }
        val candidates = linkedSetOf<Int>()
        for (header in visible.filter { nodes[it].caption in headers || nodes[it].id == PARK_ROOT }) {
            if (nodes[header].id == PARK_ROOT) {
                visible.filterTo(candidates) { nodes[it].clickable && nodes[it].id in parkClose && within(it, header) }
                continue
            }
            // Stop at a local header or panel. Do not search sibling panes at the page or decor root.
            var group = nodes[header].parent
            for (depth in 1..3) {
                if (group <= 0 || nodes[group].parent <= 0 || nodes[group].id == "bnav_rg_mapmode_main_layout_new") break
                val local = visible.filter {
                    val control = nodes[it]
                    val sameHeaderRow = minOf(nodes[header].bottom, control.bottom) > maxOf(nodes[header].top, control.top)
                    within(it, group) && control.caption !in conflictingLabels && (control.caption in returnLabels ||
                        (sameHeaderRow && (control.caption in closeLabels || control.id in closeIds)))
                }
                if (local.isNotEmpty()) {
                    for (control in local) {
                        val button = if (nodes[control].clickable) control else nodes[control].parent
                        if (button > group && nodes[button].visible && nodes[button].clickable &&
                            nodes[button].caption !in conflictingLabels) candidates += button
                    }
                    break
                } else group = nodes[group].parent
            }
        }
        // The same cancel button can expose both its wrapper and its label as click targets.
        return candidates.filter { outer -> candidates.none { it != outer && within(it, outer) } }.singleOrNull()
    }
}
