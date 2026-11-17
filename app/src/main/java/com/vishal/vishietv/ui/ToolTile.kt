package com.vishal.vishietv.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/**
 * TV Tools ki tile — Boost, Storage, Wi-Fi jaise kaam. App tile jaisi hi
 * focus chamak, par andar badi heading + chhoti si detail.
 */
class ToolTile(context: Context, private val accentColor: Int) : LinearLayout(context) {

    private val bg = GradientDrawable()
    private val titleView = TextView(context)
    private val descView = TextView(context)
    private var focusCallback: (() -> Unit)? = null

    init {
        isFocusable = true
        isClickable = true
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(12), dp(14), dp(12))
        clipToPadding = false
        clipChildren = false

        bg.gradientType = GradientDrawable.LINEAR_GRADIENT
        bg.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        bg.colors = intArrayOf(0xFF27344A.toInt(), 0xFF1B2434.toInt())
        bg.cornerRadius = dp(16).toFloat()
        bg.setStroke(dp(1), 0xFF2E3D55.toInt())
        background = bg

        val bar = TextView(context)
        bar.background = GradientDrawable().apply {
            setColor(accentColor)
            cornerRadius = dp(3).toFloat()
        }
        val barParams = LayoutParams(dp(34), dp(5))
        bar.layoutParams = barParams
        addView(bar)

        titleView.setTextColor(0xFFF2F5F9.toInt())
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        titleView.setTypeface(Typeface.DEFAULT_BOLD)
        val titleParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        titleParams.topMargin = dp(10)
        titleView.layoutParams = titleParams
        addView(titleView)

        descView.setTextColor(0xFF9FB3C8.toInt())
        descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        descView.maxLines = 1
        descView.ellipsize = android.text.TextUtils.TruncateAt.END
        val descParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        descParams.topMargin = dp(3)
        descView.layoutParams = descParams
        addView(descView)

        setOnFocusChangeListener { _, hasFocus ->
            applyFocusVisual(hasFocus, animate = true)
            if (hasFocus) focusCallback?.invoke()
        }
    }

    /** AppTile ki tarah hi — asli focus par animation ke saath, test me bina. */
    fun applyFocusVisual(focused: Boolean, animate: Boolean) {
        if (focused) {
            bg.setStroke(dp(3), Color.WHITE)
            elevation = dp(16).toFloat()
            if (animate) {
                animate().scaleX(1.10f).scaleY(1.10f)
                    .setDuration(160)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                scaleX = 1.10f
                scaleY = 1.10f
            }
        } else {
            bg.setStroke(dp(1), 0xFF2E3D55.toInt())
            elevation = 0f
            if (animate) {
                animate().scaleX(1f).scaleY(1f)
                    .setDuration(140)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                scaleX = 1f
                scaleY = 1f
            }
        }
    }

    fun bind(
        title: String, desc: String, tagValue: String,
        onClick: () -> Unit, onFocusGained: (() -> Unit)? = null
    ) {
        tag = tagValue
        titleView.text = title
        descView.text = desc
        focusCallback = onFocusGained
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
}
