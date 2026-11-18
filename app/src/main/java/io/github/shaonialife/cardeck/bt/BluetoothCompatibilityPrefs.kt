package io.github.shaonialife.cardeck.bt

import android.content.Context
import io.github.shaonialife.cardeck.R

/** User-selected compatibility behavior, snapshotted when a new connection starts. */
data class BluetoothCompatibilityPolicy(val keepSystemBluetooth: Boolean = false) {
    val disconnectProfiles: Boolean get() = !keepSystemBluetooth
    val probeSelectedDevice: Boolean get() = keepSystemBluetooth

    /** Pairing can finish after preferences or the chosen target have changed. */
    fun allowsPostBondDisconnect(
        targetMatches: Boolean,
        isBonded: Boolean,
        interrupted: Boolean,
        activeSessionKeepsBluetooth: Boolean? = null,
    ): Boolean =
        disconnectProfiles && activeSessionKeepsBluetooth != true && targetMatches && isBonded && !interrupted
}

object BluetoothCompatibilityPrefs {
    private const val PREFS = "bluetooth_compatibility"
    private const val KEY_KEEP_SYSTEM_BLUETOOTH = "keep_system_bluetooth"

    internal fun effectiveValue(saved: Boolean?, defaultValue: Boolean): Boolean = saved ?: defaultValue

    fun get(context: Context): BluetoothCompatibilityPolicy {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = if (preferences.contains(KEY_KEEP_SYSTEM_BLUETOOTH)) {
            preferences.getBoolean(KEY_KEEP_SYSTEM_BLUETOOTH, false)
        } else {
            null
        }
        return BluetoothCompatibilityPolicy(
            effectiveValue(saved, context.resources.getBoolean(R.bool.keep_system_bluetooth_default)),
        )
    }

    fun setKeepSystemBluetooth(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEEP_SYSTEM_BLUETOOTH, enabled)
            .apply()
    }
}
