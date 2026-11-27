package io.github.shaonialife.cardeck.launcher

internal enum class CommuteGuidance { NONE, PANEL, ROUTE, CALCULATING }

private const val panel = "bnav_rg_icar_guide_panel_container"
private const val corner = "bnav_rg_default_guide_panel_corner_container"
private const val road = "bn_rg_main_guide_road_tv"
private const val remaining = "bnav_rg_toolbox_remain_time_and_dist"
private const val loading = "guide_2d_border_tv"
private const val waiting = "banav_rg_toolbox_rp_watting_tv"

internal const val commuteViewIds = "$panel|$corner|$road|$remaining|$loading|$waiting"
private val visibleBounds = Regex("""\sV\S*\s+\S+\s+(-?\d+),(-?\d+)-(-?\d+),(-?\d+)\s""")

/** A parked or calculating map may retain both an old panel and zero-sized child views. */
internal fun commuteGuidance(dump: String, display: Int, task: Int): CommuteGuidance {
    if (task <= 0 || display <= 0) return CommuteGuidance.NONE
    var matching = false
    val visible = mutableSetOf<String>()
    for (line in dump.lineSequence()) {
        if (line.startsWith("TASK ")) {
            matching = line.contains(" id=$task ") && Regex("""\bdisplayId=$display\b""").containsMatchIn(line)
        } else if (matching) {
            val bounds = visibleBounds.find(line)?.destructured ?: continue
            val (left, top, right, bottom) = bounds
            if (right.toInt() > left.toInt() && bottom.toInt() > top.toInt()) {
                visible += line.substringAfter("app:id/", "").substringBefore('}')
            }
        }
    }
    return when {
        panel !in visible && corner !in visible -> CommuteGuidance.NONE
        loading in visible && waiting in visible && road !in visible -> CommuteGuidance.CALCULATING
        road in visible && remaining in visible && loading !in visible -> CommuteGuidance.ROUTE
        else -> CommuteGuidance.PANEL
    }
}
