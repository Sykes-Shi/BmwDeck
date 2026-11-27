package io.github.shaonialife.cardeck.launcher

internal data class BaiduMapActivity(val component: String, val taskId: Int)
internal data class BaiduMapPlacement(
    val activity: BaiduMapActivity? = null,
    val taskId: Int? = null,
)

/** Android 16 prints decimal ActivityRecord identities but accepts hex dump selectors.
 * Use the task ID and component instead; both also identify the target on older versions. */
internal fun baiduOnMapDisplay(display: Int, dump: String): BaiduMapPlacement =
    mapPackagePlacement(display, dump, BaiduCruise.PACKAGE)

internal fun mapPackagePlacement(display: Int, dump: String, packageName: String): BaiduMapPlacement {
    if (display <= 0) return BaiduMapPlacement()
    return placementOnDisplay(display, dump, packageName)
}

internal fun phoneBaiduInForeground(dump: String): Boolean =
    placementOnDisplay(0, dump, BaiduCruise.PHONE_PACKAGE).activity != null

private fun placementOnDisplay(display: Int, dump: String, packageName: String): BaiduMapPlacement {
    var onTarget = false
    var sawTop = false
    var top: BaiduMapActivity? = null
    var existingTask: Int? = null
    for (line in dump.lineSequence()) {
        val displayHeader = activityDisplay.find(line)
        if (displayHeader != null) {
            if (onTarget) break
            onTarget = displayHeader.groupValues[1].toIntOrNull() == display
            continue
        }
        if (!onTarget) continue
        val record = displayActivity.find(line) ?: continue
        val task = record.groupValues[3].toIntOrNull() ?: continue
        val component = record.groupValues[2]
        val matchesPackage = component.substringBefore('/') == packageName
        if (matchesPackage && existingTask == null) existingTask = task
        if (!sawTop && !record.groupValues[1].startsWith("* Hist")) {
            sawTop = true
            if (matchesPackage) top = BaiduMapActivity(component, task)
        }
    }
    return BaiduMapPlacement(top, top?.taskId ?: existingTask)
}

private val activityDisplay = Regex("""^\s*Display(?:\s+#|:\s*mDisplayId=)(\d+)\b""")
private val displayActivity = Regex(
    """^\s*(topResumedActivity|mResumedActivity|Resumed|\* Hist\s+#\d+)\s*[:=]\s*ActivityRecord\{[0-9a-fA-F]+\s+(?:u\d+\s+|\d+:)([\w.]+/[\w.$]+)(?:/u\d+)?\s+t(\d+)\b""",
)

object BaiduCruise {
    const val PACKAGE = "com.baidu.naviauto"
    const val PHONE_PACKAGE = "com.baidu.BaiduMap"

    private val excludedCarMaps = setOf(PHONE_PACKAGE, "com.autonavi.minimap", "com.autonavi.amapauto", "com.tencent.map", "com.google.android.apps.maps")

    fun isExcludedCarMap(packageName: String, category: Int = android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED): Boolean {
        val pkg = packageName.substringBefore('/')
        return pkg != PACKAGE && (pkg in excludedCarMaps || category == android.content.pm.ApplicationInfo.CATEGORY_MAPS)
    }
}
