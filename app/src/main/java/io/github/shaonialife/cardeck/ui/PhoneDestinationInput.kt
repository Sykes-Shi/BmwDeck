package io.github.shaonialife.cardeck.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import io.github.shaonialife.cardeck.R
import io.github.shaonialife.cardeck.launcher.DestinationInputTarget
import io.github.shaonialife.cardeck.launcher.VirtualLauncher
import io.github.shaonialife.cardeck.util.Bg

/** Use the phone IME when the installed keyboard cannot render on the map display. */
internal class PhoneDestinationInput(private val host: Activity) {
    private var dialog: AlertDialog? = null
    private var owner: DestinationInputTarget? = null

    fun dismissIfStale(current: DestinationInputTarget?) {
        if (owner !== current) dialog?.dismiss()
    }

    fun open() {
        if (dialog?.isShowing == true) return
        val target = VirtualLauncher.currentDestinationInput() ?: return
        val builder = AlertDialog.Builder(host, android.R.style.Theme_Material_Dialog_Alert)
        val input = EditText(builder.context).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            filters = arrayOf(InputFilter.LengthFilter(128))
            setHint(R.string.phone_destination_hint)
        }
        val inset = (24 * host.resources.displayMetrics.density).toInt()
        val frame = FrameLayout(builder.context).apply {
            setPadding(inset, inset / 2, inset, 0)
            addView(input, FrameLayout.LayoutParams(-1, -2))
        }
        val sheet = builder.setTitle(R.string.phone_destination_entry).setView(frame)
            .setPositiveButton(R.string.phone_destination_submit, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        owner = target
        dialog = sheet
        sheet.setOnDismissListener {
            if (dialog === sheet) {
                owner = null
                dialog = null
            }
        }
        sheet.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        sheet.setOnShowListener {
            val button = sheet.getButton(AlertDialog.BUTTON_POSITIVE)
            fun submit() {
                val text = input.text.toString().trim()
                if (text.isEmpty() || !button.isEnabled) return
                button.isEnabled = false
                // The map remains the owner of result selection and route confirmation.
                Bg.run("phone-place-search") {
                    val sent = VirtualLauncher.searchFromPhone(target, text)
                    host.runOnUiThread {
                        if (!host.isDestroyed && !host.isFinishing && dialog === sheet) {
                            if (sent) sheet.dismiss() else {
                                Toast.makeText(host, R.string.phone_destination_failed, Toast.LENGTH_LONG).show()
                                button.isEnabled = true
                            }
                        }
                    }
                }
            }
            button.setOnClickListener { submit() }
            input.setOnEditorActionListener { _, action, event ->
                val search = action == EditorInfo.IME_ACTION_SEARCH ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
                if (search) submit()
                search
            }
            input.requestFocus()
            input.post {
                if (sheet.isShowing) (host.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        sheet.show()
    }
}
