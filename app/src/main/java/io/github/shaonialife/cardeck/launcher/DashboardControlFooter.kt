package io.github.shaonialife.cardeck.launcher

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import io.github.shaonialife.cardeck.R
import kotlin.math.roundToInt

/** Keeps controls in reach when the portrait map divider leaves little height for music. */
class DashboardControlFooter(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    private var compact = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val oneRow = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
            MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED &&
            MeasureSpec.getSize(heightMeasureSpec) < dp(350) &&
            MeasureSpec.getSize(widthMeasureSpec) >= dp(296)
        if (compact != oneRow) {
            compact = oneRow
            orientation = if (oneRow) HORIZONTAL else VERTICAL
            val transport = findViewById<View>(R.id.dashTransportActions).layoutParams as LayoutParams
            transport.width = if (oneRow) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT
            transport.topMargin = if (oneRow) 0 else dp(4)
            val commute = findViewById<View>(R.id.dashNavigationActions).layoutParams as LayoutParams
            commute.setMargins(dp(if (oneRow) 8 else 10), if (oneRow) 0 else dp(6),
                if (oneRow) 0 else dp(10), if (oneRow) 0 else dp(12))
            setPadding(0, 0, 0, if (oneRow) dp(6) else 0)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
