package io.github.shaonialife.cardeck.launcher

import android.content.Context

/** The dashboard map is fixed. Desktop app selections, including an empty desktop, stay optional. */
object DesktopApps {
    private const val PREFS_NAME = "settings"
    private const val KEY_SELECTED = "desktopApps"

    /** 当前应显示在桌面上的应用（component flattenToString 集合）。 */
    fun selected(
        context: Context,
        apps: List<VirtualLauncher.AppEntry>,
    ): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return selectedDesktopComponents(
            apps.mapTo(linkedSetOf()) { it.component.flattenToString() },
            prefs.getStringSet(KEY_SELECTED, null),
        )
    }

    fun save(
        context: Context,
        selection: Set<String>,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_SELECTED, selection.toMutableSet())
            .apply()
    }
}

internal fun selectedDesktopComponents(available: Set<String>, saved: Set<String>?): Set<String> {
    val carMap = available.firstOrNull { it.substringBefore('/') == BaiduCruise.PACKAGE }
    if (saved == null) return setOfNotNull(carMap)
    return saved.mapNotNullTo(linkedSetOf()) { component ->
        val pkg = component.substringBefore('/')
        when {
            pkg == BaiduCruise.PACKAGE || BaiduCruise.isExcludedCarMap(pkg) -> carMap
            component in available -> component
            else -> null
        }
    }
}
