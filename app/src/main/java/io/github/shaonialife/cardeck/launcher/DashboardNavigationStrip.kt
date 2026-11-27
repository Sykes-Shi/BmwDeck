package io.github.shaonialife.cardeck.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Adapts to the music card itself as the map divider moves within the same display. */
class DashboardNavigationStrip(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    private data class Action(val label: TextView, val icon: Drawable?)

    private lateinit var actions: List<Action>
    private var iconsShown: Boolean? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        val iconSize = dp(18)
        actions = children.filterIsInstance<TextView>().map { label ->
            Action(label, label.compoundDrawablesRelative[0]?.mutate()?.apply {
                setBounds(0, 0, iconSize, iconSize)
            })
        }.toList()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val divider = dividerDrawable?.intrinsicWidth ?: 0
        fun rowWidth(withIcons: Boolean): Int = divider + actions.sumOf { action ->
            val label = action.label
            val text = ceil(label.paint.measureText(label.text.toString())).toInt()
            val icon = if (withIcons && action.icon != null) dp(18) + label.compoundDrawablePadding else 0
            maxOf(label.minWidth, text + icon + dp(if (withIcons) 24 else 8))
        }

        val unconstrained = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
        val wide = unconstrained || width >= rowWidth(true)
        val direction = if (wide || width >= rowWidth(false)) HORIZONTAL else VERTICAL
        if (orientation != direction) orientation = direction
        val showIcons = wide || direction == VERTICAL && width >= dp(110)
        if (iconsShown != showIcons) {
            val inset = dp(if (showIcons) 12 else 4)
            actions.forEach { (label, icon) ->
                label.setPaddingRelative(inset, 0, inset, 0)
                label.setCompoundDrawablesRelative(if (showIcons) icon else null, null, null, null)
            }
            iconsShown = showIcons
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
