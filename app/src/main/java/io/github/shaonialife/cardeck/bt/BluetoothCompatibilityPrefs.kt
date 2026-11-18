package io.github.shaonialife.cardeck.bt

import android.content.Context

/** User-selected compatibility behavior, snapshotted when a new connection starts. */
data class BluetoothCompatibilityPolicy(val keepSystemBluetooth: Boolean = false) {
    val disconnectProfiles: Boolean get() = !keepSystemBluetooth
    val probeSelectedDevice: Boolean get() = keepSystemBluetooth
}

object BluetoothCompatibilityPrefs {
    private const val PREFS = "bluetooth_compatibility"
    private const val KEY_KEEP_SYSTEM_BLUETOOTH = "keep_system_bluetooth"

    fun get(context: Context): BluetoothCompatibilityPolicy =
        BluetoothCompatibilityPolicy(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_KEEP_SYSTEM_BLUETOOTH, false),
        )

    fun setKeepSystemBluetooth(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEEP_SYSTEM_BLUETOOTH, enabled)
            .apply()
    }
}
