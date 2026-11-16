package io.github.shaonialife.cardeck.launcher

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import io.github.shaonialife.cardeck.R

/** Uses a scoped theme so Android and third-party apps keep their own display preferences. */
internal class ProjectedPalette(context: Context, val day: Boolean) {
    private val styled = if (day) ContextThemeWrapper(context, R.style.CarPaletteDay) else context
    private fun color(attr: Int): Int = TypedValue().also { check(styled.theme.resolveAttribute(attr, it, true)) }.data
    val surface = color(R.attr.deckSurface)
    val text = color(R.attr.deckText)
    val secondary = color(R.attr.deckSecondary)
    val accent = color(R.attr.deckAccent)
    val onAccent = color(R.attr.deckOnAccent)
    private val track = ColorStateList.valueOf(
        if (day) ColorUtils.blendARGB(secondary, surface, 0.65f) else ColorUtils.setAlphaComponent(secondary, 85),
    )

    fun background(view: View, drawable: Int) {
        view.background = styled.getDrawable(drawable)
    }

    fun appCell(cell: View, label: TextView) {
        background(cell, R.drawable.bg_app_cell_focus)
        background(label, R.drawable.bg_app_label)
        label.setTextColor(text)
    }

    private val rotaryState = intArrayOf(android.R.attr.state_activated)
    private val keyboardState = intArrayOf(android.R.attr.state_focused)
    private val ripple = ColorStateList.valueOf(color(R.attr.deckRipple))

    fun focusContent(normal: Int, nativeFocus: Boolean = false): ColorStateList =
        ColorStateList(arrayOf(if (nativeFocus) keyboardState else rotaryState, intArrayOf()), intArrayOf(onAccent, normal))

    /** The selected surface follows the original control's outline; only the dialog uses native focus. */
    fun focusButton(view: View, idle: Int, cornerDp: Int, nativeFocus: Boolean = false) {
        val shape = GradientDrawable().apply {
            if (cornerDp == 0) this.shape = GradientDrawable.OVAL
            else cornerRadius = cornerDp * view.resources.displayMetrics.density
            setColor(accent)
        }
        view.defaultFocusHighlightEnabled = false
        view.background = StateListDrawable().apply {
            addState(if (nativeFocus) keyboardState else rotaryState, RippleDrawable(ripple, shape, null))
            addState(intArrayOf(), styled.getDrawable(idle))
        }
    }

    private fun focusCover(view: View) {
        val density = view.resources.displayMetrics.density
        view.defaultFocusHighlightEnabled = false
        view.foreground = StateListDrawable().apply {
            addState(rotaryState, GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(Color.TRANSPARENT)
                setStroke(kotlin.math.round(3 * density).toInt().coerceAtLeast(1), accent)
            })
        }
    }

    /** Called when the theme changes or a new media view is attached, never on the playback tick. */
    fun media(view: View) {
        when (view.id) {
            R.id.dashCover -> focusCover(view)
            R.id.carMediaRoot -> view.setBackgroundColor(surface)
            R.id.carMediaCoverFrame, R.id.dashCoverFrame, R.id.dashPhoneNavigationNotice ->
                background(view, R.drawable.bg_car_media_surface)
            R.id.carMediaBack, R.id.carMediaOpenOriginal, R.id.dashOpenPlayer, R.id.dashBaiduDestination ->
                focusButton(view, R.drawable.bg_car_media_button, 12)
            R.id.dashBaiduCruise -> focusButton(view, R.drawable.bg_car_media_cruise, 20)
            R.id.dashPlayPause, R.id.carMediaPlayPause -> focusButton(view, R.drawable.bg_car_media_play, 0)
            R.id.dashPrev, R.id.dashNext, R.id.carMediaPrevious, R.id.carMediaNext ->
                focusButton(view, R.drawable.bg_car_media_skip, 0)
        }
        when (view) {
            is TextView -> {
                // Anonymous headings and subtitles in both layout orientations retain their XML role.
                val muted = view.getTag(R.id.carMutedTextRole) as? Boolean ?: (
                    view.currentTextColor == view.context.getColor(R.color.car_text_muted)
                ).also { view.setTag(R.id.carMutedTextRole, it) }
                val normal = if (muted) secondary else text
                when (view.id) {
                    R.id.dashBaiduCruise, R.id.carMediaOpenOriginal, R.id.dashOpenPlayer, R.id.dashBaiduDestination ->
                        view.setTextColor(focusContent(normal))
                    else -> view.setTextColor(normal)
                }
                if (view.id == R.id.dashBaiduCruise) view.compoundDrawableTintList = focusContent(accent)
            }
            is ImageView -> when (view.id) {
                R.id.dashPlayPause, R.id.carMediaPlayPause -> view.imageTintList = focusContent(accent)
                R.id.dashPrev, R.id.dashNext, R.id.carMediaPrevious, R.id.carMediaNext, R.id.carMediaBack ->
                    view.imageTintList = focusContent(text)
            }
            is ProgressBar -> {
                view.progressTintList = ColorStateList.valueOf(accent)
                view.progressBackgroundTintList = track
            }
            is ViewGroup -> view.children.forEach(::media)
        }
    }
}
