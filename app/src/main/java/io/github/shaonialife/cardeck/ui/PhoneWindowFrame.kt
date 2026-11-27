package io.github.shaonialife.cardeck.ui

import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Paint
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import io.github.shaonialife.cardeck.R
import java.util.WeakHashMap

/** Only phone windows draw these surfaces. Android still owns the clock, notification icons and gestures. */
internal class PhoneWindowFrame private constructor(
    private val activity: AppCompatActivity,
    private val home: Boolean,
) : FrameLayout(activity) {
    private val paint = Paint()
    private var safe = Insets.NONE
    private var navigation = Insets.NONE
    private var topColor = Color.TRANSPARENT
    private var bottomColor = Color.TRANSPARENT
    private var baseColor = Color.TRANSPARENT

    init {
        setWillNotDraw(false)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        navigation = insets.getInsets(WindowInsets.Type.navigationBars())
        val ime = insets.getInsets(WindowInsets.Type.ime())
        setPadding(safe.left, safe.top, safe.right, maxOf(safe.bottom, ime.bottom))
        invalidate()
        return insets
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(baseColor)
        paint.color = topColor
        canvas.drawRect(0f, 0f, w, safe.top.toFloat(), paint)
        paint.color = bottomColor
        if (navigation.bottom > 0) canvas.drawRect(0f, h - navigation.bottom, w, h, paint)
        if (navigation.left > 0) canvas.drawRect(0f, 0f, navigation.left.toFloat(), h, paint)
        if (navigation.right > 0) canvas.drawRect(w - navigation.right, 0f, w, h, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        windows[this] = Unit
        updatePalette()
        requestApplyInsets()
    }

    override fun onDetachedFromWindow() {
        windows.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updatePalette()
        requestApplyInsets()
    }

    private fun updatePalette() {
        val dim = ScreenDimmer.isDimShown()
        baseColor = if (dim) Color.BLACK else context.getColor(R.color.phone_canvas)
        topColor = if (home && !dim) context.getColor(R.color.phone_masthead) else baseColor
        bottomColor = if (home && !dim) context.getColor(R.color.phone_card) else baseColor
        WindowCompat.getInsetsController(activity.window, this).apply {
            isAppearanceLightStatusBars = ColorUtils.calculateLuminance(topColor) > 0.5
            isAppearanceLightNavigationBars = ColorUtils.calculateLuminance(bottomColor) > 0.5
        }
        invalidate()
    }

    companion object {
        private val windows = WeakHashMap<PhoneWindowFrame, Unit>()

        /** Update exposed system strips when the existing phone dim overlay is shown or removed. */
        fun refreshDim() = windows.keys.toList().forEach { it.updatePalette() }

        fun install(activity: AppCompatActivity, @LayoutRes layout: Int, home: Boolean = false) {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.apply {
                statusBarColor = Color.TRANSPARENT
                navigationBarColor = Color.TRANSPARENT
                isStatusBarContrastEnforced = false
                isNavigationBarContrastEnforced = false
            }
            val frame = PhoneWindowFrame(activity, home)
            activity.layoutInflater.inflate(layout, frame, true)
            activity.setContentView(frame)
        }
    }
}
