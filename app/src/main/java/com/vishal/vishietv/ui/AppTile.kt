package com.vishal.vishietv.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.vishal.vishietv.data.TvApp

/**
 * tvOS (Apple TV) style app tile: bada rang-biranga card, beech me bada
 * icon ya akshar, aur naam card ke NEECHE — bilkul Apple TV ki tarah.
 * Focus milte hi card halka sa bada hota hai, safed kinari chamakti hai,
 * aur peeche ki ambient roshni usi app ke rang ki ho jati hai (MainActivity
 * onFocusGained se). Animation sirf scale + stroke — 2015 hardware ke liye.
 */
class AppTile(context: Context) : LinearLayout(context) {

    private val cardBg = GradientDrawable()
    private val card = FrameLayout(context)
    private val iconView = ImageView(context)
    private val letterView = TextView(context)
    private val labelView = TextView(context)

    init {
        isFocusable = true
        isClickable = true
        orientation = VERTICAL
        clipToPadding = false
        clipChildren = false

        cardBg.gradientType = GradientDrawable.LINEAR_GRADIENT
        cardBg.orientation = GradientDrawable.Orientation.TL_BR
        cardBg.colors = intArrayOf(0xFF27344A.toInt(), 0xFF1B2434.toInt())
        cardBg.cornerRadius = dp(18).toFloat()
        cardBg.setStroke(dp(1), 0xFF2E3D55.toInt())
        card.background = cardBg
        card.clipToPadding = false
        card.clipChildren = false
        card.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(110))
        addView(card)

        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        val iconSize = dp(68)
        iconView.layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        card.addView(iconView)

        letterView.gravity = Gravity.CENTER
        letterView.setTextColor(0xEBFFFFFF.toInt())
        letterView.setTypeface(Typeface.DEFAULT_BOLD)
        letterView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 42f)
        letterView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        card.addView(letterView)

        labelView.setTextColor(0xFFE8EEF5.toInt())
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        labelView.gravity = Gravity.CENTER
        labelView.maxLines = 1
        labelView.ellipsize = android.text.TextUtils.TruncateAt.END
        val labelParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        labelParams.topMargin = dp(8)
        labelView.layoutParams = labelParams
        addView(labelView)

        setOnFocusChangeListener { _, hasFocus ->
            applyFocusVisual(hasFocus, animate = true)
            if (hasFocus) focusCallback?.invoke()
        }
    }

    private var focusCallback: (() -> Unit)? = null

    /** Card ki unchai — Pasandida row me badi, grid me thodi chhoti. */
    fun setCardHeight(heightDp: Int) {
        card.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(heightDp))
    }

    /**
     * Focus wali chamak. Asli TV par focus listener ise animation ke saath
     * bulata hai; screenshot tests window-focus nahi de paate, isliye wahi
     * antim haalat bina animation ke bhi lagayi ja sakti hai.
     */
    fun applyFocusVisual(focused: Boolean, animate: Boolean) {
        if (focused) {
            cardBg.setStroke(dp(3), Color.WHITE)
            card.elevation = dp(16).toFloat()
            if (animate) {
                animate().scaleX(1.08f).scaleY(1.08f)
                    .setDuration(160)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                scaleX = 1.08f
                scaleY = 1.08f
            }
        } else {
            cardBg.setStroke(dp(1), 0xFF2E3D55.toInt())
            card.elevation = 0f
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

    fun bindApp(
        app: TvApp,
        onClick: (TvApp) -> Unit,
        onLongClick: (TvApp) -> Unit,
        onFocusGained: (() -> Unit)? = null
    ) {
        tag = app.packageName
        labelView.text = app.label
        val (c1, c2) = gradientFor(app.packageName)
        cardBg.colors = intArrayOf(c1, c2)
        if (app.icon != null) {
            iconView.setImageDrawable(app.icon)
            iconView.visibility = View.VISIBLE
            letterView.visibility = View.GONE
        } else {
            iconView.visibility = View.GONE
            letterView.visibility = View.VISIBLE
            letterView.text = app.label.trim().firstOrNull()?.uppercase() ?: "?"
        }
        focusCallback = onFocusGained
        setOnClickListener { onClick(app) }
        setOnLongClickListener {
            onLongClick(app)
            true
        }
    }

    companion object {
        private val GRADIENTS = listOf(
            0xFF5C6BC0.toInt() to 0xFF7E57C2.toInt(),
            0xFF26A69A.toInt() to 0xFF66BB6A.toInt(),
            0xFFEF5350.toInt() to 0xFFFF7043.toInt(),
            0xFFAB47BC.toInt() to 0xFFEC407A.toInt(),
            0xFF42A5F5.toInt() to 0xFF29B6F6.toInt(),
            0xFFFFA726.toInt() to 0xFFFF7043.toInt(),
            0xFF66BB6A.toInt() to 0xFF26A69A.toInt(),
            0xFFEC407A.toInt() to 0xFFAB47BC.toInt(),
            0xFF8D6E63.toInt() to 0xFFFFA726.toInt(),
            0xFF78909C.toInt() to 0xFF42A5F5.toInt()
        )

        /** Har app ka apna do-ranga gradient — tile aur ambient roshni ke liye. */
        fun gradientFor(packageName: String): Pair<Int, Int> =
            GRADIENTS[(packageName.hashCode() and Int.MAX_VALUE) % GRADIENTS.size]
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
}
