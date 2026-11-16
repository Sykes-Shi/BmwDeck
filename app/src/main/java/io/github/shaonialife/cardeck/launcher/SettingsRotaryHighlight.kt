package io.github.shaonialife.cardeck.launcher

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.children

/** Temporary car-screen styling. A settings panel can rebind colors or the background on confirm. */
internal class SettingsRotaryHighlight(val target: View, palette: ProjectedPalette) :
    ViewTreeObserver.OnPreDrawListener {
    private val background = GradientDrawable().apply {
        cornerRadius = 10 * target.resources.displayMetrics.density
        setColor(palette.accent)
    }
    // A panel can also set this exact color. Use a distinct list to recognize our own paint.
    private val ink = ColorStateList(arrayOf(intArrayOf()), intArrayOf(palette.onAccent))
    private var restingBackground: Drawable? = target.background
    private val restingText = mutableMapOf<TextView, ColorStateList>()
    private val back = (target as? TextView)?.takeIf { it.compoundDrawables.any { icon -> icon != null } }
    private val backTint = back?.compoundDrawableTintList
    private val radio = target as? RadioButton
    private val radioTint = radio?.buttonTintList
    private val backgroundTint = target.backgroundTintList
    private val nativeHighlight = target.defaultFocusHighlightEnabled
    private val observer = target.viewTreeObserver

    init {
        fun collect(view: View) {
            if (view is TextView) restingText[view] = view.textColors
            if (view is ViewGroup) view.children.forEach(::collect)
        }
        collect(target)
        target.defaultFocusHighlightEnabled = false
        target.backgroundTintList = null
        back?.compoundDrawableTintList = ink
        radio?.buttonTintList = ink
        onPreDraw()
        observer.addOnPreDrawListener(this)
    }

    override fun onPreDraw(): Boolean {
        if (target.background !== background) {
            restingBackground = target.background
            replaceBackground(background)
        }
        for (entry in restingText.entries) {
            val view = entry.key
            if (view.textColors !== ink) {
                entry.setValue(view.textColors)
                view.setTextColor(ink)
            }
        }
        return true
    }

    private fun replaceBackground(drawable: Drawable?) {
        val left = target.paddingLeft
        val top = target.paddingTop
        val right = target.paddingRight
        val bottom = target.paddingBottom
        target.background = drawable
        if (target.paddingLeft != left || target.paddingTop != top ||
            target.paddingRight != right || target.paddingBottom != bottom
        ) target.setPadding(left, top, right, bottom)
    }

    fun clear() {
        if (observer.isAlive) observer.removeOnPreDrawListener(this)
        else if (target.viewTreeObserver.isAlive) target.viewTreeObserver.removeOnPreDrawListener(this)
        if (target.background === background) replaceBackground(restingBackground)
        target.backgroundTintList = backgroundTint
        target.defaultFocusHighlightEnabled = nativeHighlight
        back?.compoundDrawableTintList = backTint
        radio?.buttonTintList = radioTint
        for ((view, normal) in restingText) {
            if (view.textColors === ink) view.setTextColor(normal)
        }
    }
}
