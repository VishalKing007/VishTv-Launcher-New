package com.vishal.vishietv.ui

import android.content.Context
import android.graphics.Bitmap
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
import com.vishal.vishietv.data.Wallpapers

/**
 * Wallpaper wali tile — AppTile jaisi hi bhasha: 16:9 card, naam neeche.
 * Jo wallpaper laga hua hai us par safed kinari + "✓ Laga hua" nishaan.
 * "+" wali khaali tile photo jodne ke liye.
 */
class WallpaperTile(context: Context) : LinearLayout(context) {

    private val cardBg = GradientDrawable()
    private val borderBg = GradientDrawable()
    private val card = FrameLayout(context)
    private val imageView = ImageView(context)
    private val plusView = TextView(context)
    private val badge = TextView(context)
    private val labelView = TextView(context)
    private var active = false

    init {
        isFocusable = true
        isClickable = true
        orientation = VERTICAL
        clipToPadding = false
        clipChildren = false

        cardBg.gradientType = GradientDrawable.LINEAR_GRADIENT
        cardBg.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        cardBg.colors = intArrayOf(0xFF27344A.toInt(), 0xFF1B2434.toInt())
        cardBg.cornerRadius = dp(14).toFloat()
        cardBg.setStroke(dp(1), 0xFF2E3D55.toInt())
        card.background = cardBg
        card.clipToPadding = false
        card.clipChildren = false
        card.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(CARD_HEIGHT_DP))
        addView(card)

        // Bitmap pehle se hi tile ke size me gol kinaron ke saath kata
        // aata hai (Wallpapers.roundedCover), isliye FIT_XY theek hai.
        imageView.scaleType = ImageView.ScaleType.FIT_XY
        imageView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        card.addView(imageView)

        plusView.gravity = Gravity.CENTER
        plusView.setTextColor(0xFFC7D3E0.toInt())
        plusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
        plusView.text = "+"
        plusView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        plusView.visibility = View.GONE
        card.addView(plusView)

        badge.text = "✓ Laga hua"
        badge.setTextColor(Color.WHITE)
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        badge.setTypeface(Typeface.DEFAULT_BOLD)
        badge.setPadding(dp(9), dp(4), dp(9), dp(4))
        val badgeBg = GradientDrawable()
        badgeBg.setColor(0xCC1D9E75.toInt())
        badgeBg.cornerRadius = dp(999).toFloat()
        badge.background = badgeBg
        val badgeParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
        badgeParams.topMargin = dp(8)
        badgeParams.rightMargin = dp(8)
        badge.layoutParams = badgeParams
        badge.visibility = View.GONE
        card.addView(badge)

        // Kinari wali parat SABSE UPAR — photo card ke upar bhi focus ki
        // safed ring hamesha dikhe (photo cardBg ki stroke ko dhak deti hai).
        borderBg.setColor(Color.TRANSPARENT)
        borderBg.cornerRadius = dp(14).toFloat()
        borderBg.setStroke(dp(1), 0xFF2E3D55.toInt())
        val border = View(context)
        border.background = borderBg
        border.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        card.addView(border)

        labelView.setTextColor(0xFFE8EEF5.toInt())
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        labelView.gravity = Gravity.CENTER
        labelView.maxLines = 1
        labelView.ellipsize = android.text.TextUtils.TruncateAt.END
        val labelParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        labelParams.topMargin = dp(7)
        labelView.layoutParams = labelParams
        addView(labelView)

        setOnFocusChangeListener { _, hasFocus ->
            applyFocusVisual(hasFocus, animate = true)
        }
    }

    /** Focus ki safed kinari; laga-hua wallpaper bina focus ke bhi hari kinari rakhta hai. */
    fun applyFocusVisual(focused: Boolean, animate: Boolean) {
        when {
            focused -> {
                borderBg.setStroke(dp(3), Color.WHITE)
                card.elevation = dp(14).toFloat()
                setScale(1.06f, animate)
            }
            active -> {
                borderBg.setStroke(dp(3), 0xFF3DDC97.toInt())
                card.elevation = 0f
                setScale(1f, animate)
            }
            else -> {
                borderBg.setStroke(dp(1), 0xFF2E3D55.toInt())
                card.elevation = 0f
                setScale(1f, animate)
            }
        }
    }

    private fun setScale(target: Float, animate: Boolean) {
        if (animate) {
            animate().scaleX(target).scaleY(target)
                .setDuration(150)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            scaleX = target
            scaleY = target
        }
    }

    fun bindPhoto(
        bitmap: Bitmap?, label: String, tagValue: String, isActive: Boolean,
        onClick: () -> Unit, onLongClick: (() -> Unit)? = null
    ) {
        tag = tagValue
        active = isActive
        labelView.text = label
        plusView.visibility = View.GONE
        if (bitmap != null) {
            imageView.setImageBitmap(
                Wallpapers.roundedCover(
                    bitmap, dp(TILE_WIDTH_DP), dp(CARD_HEIGHT_DP), dp(14).toFloat()
                )
            )
            imageView.visibility = View.VISIBLE
        } else {
            imageView.visibility = View.GONE
        }
        badge.visibility = if (isActive) View.VISIBLE else View.GONE
        applyFocusVisual(false, animate = false)
        setOnClickListener { onClick() }
        if (onLongClick != null) {
            setOnLongClickListener {
                onLongClick()
                true
            }
        } else {
            setOnLongClickListener(null)
            isLongClickable = false
        }
    }

    /** Photo baad me load ho to tile par laga do (photo browser ki thumbnails ke liye). */
    fun updateBitmap(bitmap: Bitmap?) {
        if (bitmap == null) return
        imageView.setImageBitmap(
            Wallpapers.roundedCover(
                bitmap, dp(TILE_WIDTH_DP), dp(CARD_HEIGHT_DP), dp(14).toFloat()
            )
        )
        imageView.visibility = View.VISIBLE
    }

    /** "+ Wallpaper jodo" wali khaali tile. */
    fun bindAdd(tagValue: String, onClick: () -> Unit) {
        tag = tagValue
        active = false
        labelView.text = "Wallpaper jodo"
        imageView.visibility = View.GONE
        plusView.visibility = View.VISIBLE
        badge.visibility = View.GONE
        applyFocusVisual(false, animate = false)
        setOnClickListener { onClick() }
        setOnLongClickListener(null)
        isLongClickable = false
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()

    companion object {
        const val TILE_WIDTH_DP = 264
        const val CARD_HEIGHT_DP = 150
    }
}
