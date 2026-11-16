package io.github.shaonialife.cardeck.navigation

/** 只认通知中明确写出的常见动作。距离必须和动作相邻，不能拿行程剩余里程替代路口距离。 */
internal object GuidanceSample {
    data class Hint(val maneuver: String, val meters: Int?)

    private val action = Regex("""(?:向|往)?(?:左前方|右前方|左后方|右后方)(?:行驶|转弯|转)|(?:向|往)?[左右]转(?:弯)?|掉头|调头|直行|进入|驶入""")
    private val road = Regex("""^[\p{IsHan}A-Za-z0-9·（）()\-]{0,26}(?:路|街|道|巷|匝道|隧道|高速|高架|环岛|大桥|立交)(?=$|[，,。；;、：:\s])""")
    private val approach = Regex("""(?:^|[，,。；;：:\s]|前方|再行驶|行驶)(\d+(?:\.\d+)?)\s*(公里|千米|米)(?:处|后)?\s*(?:[，,]\s*)?(?:请)?$""")
    private val boundary = Regex("""[，,。；;、：:\s]""")
    private val prohibition = Regex("""(?:禁止|不可|请勿|不能|不得|严禁)[^，,。；;：:]{0,24}$""")

    fun parse(fields: List<String>): Hint? {
        var firstHint: Hint? = null
        for (field in fields) {
            val text = field.take(512)
            for (match in action.findAll(text)) {
                val prefix = text.substring(0, match.range.first).trimEnd()
                val suffix = text.substring(match.range.last + 1)
                if (prohibition.containsMatchIn(prefix)) continue
                // “左转车道”“直行道”仅描述车道。“后右转”则明确给出了动作。
                val enteringRoad = match.value == "进入" || match.value == "驶入"
                if (!enteringRoad && suffix.isNotEmpty() && !boundary.containsMatchIn(suffix.take(1)) &&
                    !suffix.startsWith("进入") && !suffix.startsWith("驶入") && !suffix.startsWith("前往")) continue
                val distance = approach.find(prefix)?.takeUnless {
                    val lead = prefix.substring(0, it.range.first).trimEnd()
                    lead.endsWith("剩余") || lead.endsWith("全程") || lead.endsWith("总里程")
                }
                if (prefix.isNotBlank() && distance == null && !prefix.endsWith("请") &&
                    !prefix.endsWith("路口") && !boundary.containsMatchIn(prefix.takeLast(1))) continue
                val meters = distance?.let {
                    val value = it.groupValues[1].toDoubleOrNull() ?: return@let null
                    val converted = value * if (it.groupValues[2] == "米") 1 else 1000
                    if (converted in 0.0..100_000.0) kotlin.math.round(converted).toInt() else null
                }
                if (enteringRoad && (meters == null || !road.containsMatchIn(suffix.trimStart()))) continue
                val hint = Hint(if (enteringRoad) "驶入道路" else match.value, meters)
                if (firstHint == null) firstHint = hint
                if (hint.maneuver == firstHint.maneuver && meters != null) return hint
                break
            }
        }
        return firstHint
    }

    data class Notice(
        val navigation: Boolean,
        val ongoing: Boolean,
        val groupSummary: Boolean,
        val texts: List<String>,
    )

    enum class Evidence { NAVIGATION_CATEGORY, ONGOING_ROUTE_TEXT, ROUTE_TEXT_ONLY }

    data class Match(val hint: Hint, val evidence: Evidence)

    data class Scan(val state: String, val candidates: Int, val match: Match?)

    private val routeContext = Regex("""导航中|正在导航|路线引导中|下一路口|下个路口""")
    private val ended = Regex("""导航已结束|导航结束了|已结束导航|已退出导航|已到达目的地""")

    /** Notices are ordered newest first. A navigation-category match takes priority over text inference. */
    fun inspect(notices: List<Notice>): Scan {
        val usable = notices.filterNot { it.groupSummary }
        var candidates = 0
        var selected: Match? = null
        for (notice in usable) {
            if (notice.texts.any(ended::containsMatchIn)) continue
            val hint = parse(notice.texts)
            val context = notice.texts.any(routeContext::containsMatchIn)
            // A plain app notification can be a promotion. A lone “左转” or a distance on its own is insufficient.
            val evidence = when {
                notice.navigation -> Evidence.NAVIGATION_CATEGORY
                hint != null && notice.ongoing && (hint.meters != null || context) -> Evidence.ONGOING_ROUTE_TEXT
                hint != null && (hint.meters != null || context) -> Evidence.ROUTE_TEXT_ONLY
                else -> null
            }
            if (evidence != null || context) candidates++
            if (hint != null && evidence != null && (selected == null || evidence.ordinal < selected.evidence.ordinal)) {
                selected = Match(hint, evidence)
            }
        }
        val state = when {
            selected != null -> "matched"
            notices.isEmpty() -> "no_baidu_notifications"
            usable.isEmpty() -> "group_summaries_only"
            usable.none { n -> n.texts.any { it.isNotBlank() } } -> "no_standard_text"
            candidates > 0 -> "guidance_without_recognized_instruction"
            else -> "no_matching_guidance"
        }
        return Scan(state, candidates, selected)
    }
}
