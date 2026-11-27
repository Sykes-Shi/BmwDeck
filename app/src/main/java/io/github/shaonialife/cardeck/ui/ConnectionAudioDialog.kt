package io.github.shaonialife.cardeck.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import io.github.shaonialife.cardeck.R
import io.github.shaonialife.cardeck.audio.ConnectionAudioSettings
import io.github.shaonialife.cardeck.audio.ConnectionSound

internal object ConnectionAudioDialog {
    fun show(
        activity: Activity,
        settings: ConnectionAudioSettings,
        editDefaults: Boolean,
        onAuthorize: ((Boolean) -> Unit)? = null,
        onApply: (ConnectionAudioSettings, Boolean) -> Unit,
    ) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_connection_audio, null)
        val group = view.findViewById<RadioGroup>(R.id.connectionAudioChoices)
        val wholeScreen = view.findViewById<CheckBox>(R.id.preferFullDisplay)
        val remember = view.findViewById<CheckBox>(R.id.rememberAudioChoice)
        val soundNote = view.findViewById<TextView>(R.id.soundSourceNote)
        val request = view.findViewById<View>(R.id.requestAudioPermission)
        val ask = view.findViewById<View>(R.id.askEachConnection)
        val apply = view.findViewById<TextView>(R.id.audioApply)
        ask.visibility = if (editDefaults) View.VISIBLE else View.GONE
        remember.visibility = if (editDefaults) View.GONE else View.VISIBLE
        apply.text = if (editDefaults) "保存" else "连接"

        fun updateOptions(id: Int) {
            val soundOn = id == R.id.connectWithSound
            soundNote.visibility = if (soundOn) View.VISIBLE else View.GONE
            wholeScreen.visibility = if (soundOn && Build.VERSION.SDK_INT >= 34) View.VISIBLE else View.GONE
            request.visibility = if (editDefaults && onAuthorize != null && id != R.id.connectWithoutSound) View.VISIBLE else View.GONE
        }

        group.setOnCheckedChangeListener { _, id -> updateOptions(id) }
        group.check(when (settings.sound) {
            ConnectionSound.PLAY -> R.id.connectWithSound
            ConnectionSound.SILENT -> R.id.connectWithoutSound
            ConnectionSound.ASK -> if (editDefaults) R.id.askEachConnection else R.id.connectWithoutSound
        })
        wholeScreen.isChecked = settings.wholeScreen
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        view.findViewById<View>(R.id.audioCancel).setOnClickListener { dialog.dismiss() }
        request.setOnClickListener {
            val fullDisplay = wholeScreen.visibility == View.VISIBLE && wholeScreen.isChecked
            dialog.dismiss()
            onAuthorize?.invoke(fullDisplay)
        }
        apply.setOnClickListener {
            val sound = when (group.checkedRadioButtonId) {
                R.id.connectWithSound -> ConnectionSound.PLAY
                R.id.connectWithoutSound -> ConnectionSound.SILENT
                else -> ConnectionSound.ASK
            }
            val selection = ConnectionAudioSettings(sound, sound == ConnectionSound.PLAY && wholeScreen.isChecked)
            dialog.dismiss()
            onApply(selection, editDefaults || remember.isChecked)
        }
        dialog.setOnShowListener { PanelWindow.apply(dialog, activity) }
        dialog.show()
    }
}
