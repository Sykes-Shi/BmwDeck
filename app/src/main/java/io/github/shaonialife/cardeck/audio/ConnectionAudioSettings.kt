package io.github.shaonialife.cardeck.audio

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build

internal enum class ConnectionSound(val key: String) {
    ASK("ask"), SILENT("silent"), PLAY("play");
}

internal data class ConnectionAudioSettings(
    val sound: ConnectionSound = ConnectionSound.ASK,
    val wholeScreen: Boolean = false,
) {
    fun save(context: Context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("connectSoundPreference", sound.key)
            .putBoolean("connectSoundFullDisplay", sound == ConnectionSound.PLAY && wholeScreen)
            .apply()
    }

    companion object {
        fun read(context: Context): ConnectionAudioSettings {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            return decode(prefs.getString("connectSoundPreference", null), prefs.getBoolean("connectSoundFullDisplay", false))
        }

        internal fun decode(value: String?, wholeScreen: Boolean): ConnectionAudioSettings {
            val sound = ConnectionSound.entries.firstOrNull { it.key == value } ?: ConnectionSound.ASK
            return ConnectionAudioSettings(sound, sound == ConnectionSound.PLAY && wholeScreen)
        }

        fun consentIntent(manager: MediaProjectionManager, wholeScreen: Boolean): Intent =
            if (wholeScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else {
                manager.createScreenCaptureIntent()
            }
    }
}
