package io.github.shaonialife.cardeck.launcher

import android.content.ComponentName
import android.content.Context

object DefaultMapPrefs {
    fun resolve(context: Context): ComponentName? {
        val manager = context.packageManager
        val launch = manager.getLaunchIntentForPackage(BaiduCruise.PACKAGE) ?: return null
        val activity = manager.resolveActivity(launch, 0)?.activityInfo ?: return null
        if (activity.packageName != BaiduCruise.PACKAGE) return null
        return ComponentName(activity.packageName, activity.name)
    }

    internal fun clearLegacySelection(context: Context) {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (preferences.contains("defaultMapPackage") || preferences.contains("pause_center_navigation")) {
            preferences.edit().remove("defaultMapPackage").remove("pause_center_navigation").apply()
        }
    }
}
