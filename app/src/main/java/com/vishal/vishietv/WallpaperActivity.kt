package com.vishal.vishietv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.vishal.vishietv.data.TvStore
import com.vishal.vishietv.data.Wallpapers
import com.vishal.vishietv.ui.WallpaperTile

/**
 * Wallpaper ki dukaan: sabse upar "Mere Wallpaper" — Vishal apni photo
 * khud jod sakta hai (USB/storage se), tap karke laga sakta hai, OK
 * dabaye rakhkar hata sakta hai. Neeche ready-made looks, aur sabse
 * neeche purana theme look wapas laane ka rasta.
 * Saara kaam remote (D-pad) se; photo app ki private jagah me rehti hai.
 */
class WallpaperActivity : Activity() {

    private lateinit var rootView: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootView = FrameLayout(this)
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0A1424.toInt(), 0xFF080D16.toInt())
        )
        rootView.background = bg
        setContentView(rootView)
        rebuild()
    }

    // ---------- Screen banana ----------

    private fun rebuild(focusTag: String? = null) {
        rootView.removeAllViews()
        val mode = TvStore.wallpaperMode(this)

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.clipChildren = false

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.clipChildren = false
        column.clipToPadding = false
        // Home jaisi hi overscan-safe jagah.
        column.setPadding(dp(56), dp(34), dp(56), dp(30))

        val title = TextView(this)
        title.text = "WALLPAPER"
        title.setTextColor(0xFFF5F7FA.toInt())
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        title.setTypeface(Typeface.DEFAULT_BOLD)
        title.letterSpacing = 0.08f
        column.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "Apni photo lagao ya ready-made look chuno — home screen ka background badal jayega."
        subtitle.setTextColor(0xFF9FB3C8.toInt())
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        val subParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        subParams.topMargin = dp(6)
        subtitle.layoutParams = subParams
        column.addView(subtitle)

        // ----- Mere Wallpaper (sabse pehle, sabse khaas) -----
        column.addView(sectionTitle("MERE WALLPAPER"))
        val customs = TvStore.customWallpapers(this)
        val (customScroll, customRow) = horizontalRow()

        val addTile = WallpaperTile(this)
        addTile.layoutParams = tileParams()
        addTile.bindAdd("wp_add") { openPicker() }
        customRow.addView(addTile)

        customs.forEachIndexed { i, name ->
            val tile = WallpaperTile(this)
            tile.layoutParams = tileParams()
            val thumb = Wallpapers.loadBitmap(
                this, name, dp(WallpaperTile.TILE_WIDTH_DP), dp(WallpaperTile.CARD_HEIGHT_DP)
            )
            tile.bindPhoto(
                thumb, "Mera Wallpaper ${i + 1}", "wp_custom_$name",
                isActive = mode == "custom:$name",
                onClick = {
                    TvStore.setWallpaperMode(this, "custom:$name")
                    Toast.makeText(this, "Wallpaper lag gaya", Toast.LENGTH_SHORT).show()
                    rebuild(focusTag = "wp_custom_$name")
                },
                onLongClick = { confirmDelete(name) }
            )
            customRow.addView(tile)
        }
        column.addView(customScroll)

        if (customs.isEmpty()) {
            val hint = TextView(this)
            hint.text = "Abhi apna koi wallpaper nahi hai — \"+ Wallpaper jodo\" se pehli photo lagao. Tile par OK dabaye rakhne se wallpaper hat-ta hai."
            hint.setTextColor(0xFF7186A0.toInt())
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            val hintParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hintParams.topMargin = dp(2)
            hint.layoutParams = hintParams
            column.addView(hint)
        }

        // ----- Ready-made looks -----
        column.addView(sectionTitle("READY-MADE LOOKS"))
        val (presetScroll, presetRow) = horizontalRow()
        Wallpapers.PRESETS.forEachIndexed { i, preset ->
            val tile = WallpaperTile(this)
            tile.layoutParams = tileParams()
            val thumb = Wallpapers.presetBitmap(
                preset, dp(WallpaperTile.TILE_WIDTH_DP), dp(WallpaperTile.CARD_HEIGHT_DP)
            )
            tile.bindPhoto(
                thumb, preset.naam, "wp_preset_$i",
                isActive = mode == "preset:$i",
                onClick = {
                    TvStore.setWallpaperMode(this, "preset:$i")
                    Toast.makeText(this, "Wallpaper lag gaya", Toast.LENGTH_SHORT).show()
                    rebuild(focusTag = "wp_preset_$i")
                }
            )
            presetRow.addView(tile)
        }
        column.addView(presetScroll)

        // ----- Theme look wapas -----
        val themeButton = wideButton(
            if (mode == "theme") "Theme look — abhi yahi laga hai" else "Theme look wapas",
            "Rang-birangi theme wala purana background"
        ) {
            TvStore.setWallpaperMode(this, "theme")
            Toast.makeText(this, "Theme look wapas aa gaya", Toast.LENGTH_SHORT).show()
            rebuild(focusTag = "wp_theme_reset")
        }
        themeButton.tag = "wp_theme_reset"
        val tbParams = LinearLayout.LayoutParams(dp(430), ViewGroup.LayoutParams.WRAP_CONTENT)
        tbParams.topMargin = dp(26)
        themeButton.layoutParams = tbParams
        column.addView(themeButton)

        scroll.addView(column)
        rootView.addView(scroll)

        rootView.post {
            val wanted = focusTag ?: when {
                mode.startsWith("custom:") -> "wp_custom_" + mode.removePrefix("custom:")
                mode.startsWith("preset:") -> "wp_preset_" + mode.removePrefix("preset:")
                mode == "theme" -> "wp_theme_reset"
                else -> "wp_add"
            }
            (rootView.findViewWithTag<View>(wanted) ?: rootView.findViewWithTag<View>("wp_add"))
                ?.requestFocus()
        }
    }

    private fun tileParams(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(
            dp(WallpaperTile.TILE_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.rightMargin = dp(16)
        return lp
    }

    private fun sectionTitle(text: String): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(24)
        params.bottomMargin = dp(4)
        row.layoutParams = params

        val barView = View(this)
        val barBg = GradientDrawable()
        barBg.setColor(0xFF00ACC1.toInt())
        barBg.cornerRadius = dp(2).toFloat()
        barView.background = barBg
        barView.layoutParams = LinearLayout.LayoutParams(dp(4), dp(18))
        row.addView(barView)

        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(0xFFC7D3E0.toInt())
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
        tv.setTypeface(Typeface.DEFAULT_BOLD)
        tv.letterSpacing = 0.14f
        val tvParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        tvParams.leftMargin = dp(10)
        tv.layoutParams = tvParams
        row.addView(tv)
        return row
    }

    private fun horizontalRow(): Pair<HorizontalScrollView, LinearLayout> {
        val hScroll = HorizontalScrollView(this)
        hScroll.isHorizontalScrollBarEnabled = false
        hScroll.clipChildren = false
        hScroll.clipToPadding = false
        hScroll.overScrollMode = View.OVER_SCROLL_NEVER
        hScroll.setPadding(dp(12), dp(12), dp(12), dp(12))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.clipChildren = false
        row.clipToPadding = false
        hScroll.addView(row)
        return hScroll to row
    }

    private fun wideButton(titleText: String, descText: String, onClick: () -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(14), dp(20), dp(14))
        box.isFocusable = true
        box.isClickable = true
        val bg = GradientDrawable()
        bg.gradientType = GradientDrawable.LINEAR_GRADIENT
        bg.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        bg.colors = intArrayOf(0xFF27344A.toInt(), 0xFF1B2434.toInt())
        bg.cornerRadius = dp(16).toFloat()
        bg.setStroke(dp(1), 0xFF2E3D55.toInt())
        box.background = bg

        val title = TextView(this)
        title.text = titleText
        title.setTextColor(0xFFF2F5F9.toInt())
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        title.setTypeface(Typeface.DEFAULT_BOLD)
        box.addView(title)

        val desc = TextView(this)
        desc.text = descText
        desc.setTextColor(0xFF9FB3C8.toInt())
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        val descParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        descParams.topMargin = dp(3)
        desc.layoutParams = descParams
        box.addView(desc)

        box.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                bg.setStroke(dp(3), Color.WHITE)
                box.animate().scaleX(1.03f).scaleY(1.03f).setDuration(150).start()
            } else {
                bg.setStroke(dp(1), 0xFF2E3D55.toInt())
                box.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
        }
        box.setOnClickListener { onClick() }
        return box
    }

    // ---------- Photo jodna / hatana ----------

    private fun openPicker() {
        if (TvStore.customWallpapers(this).size >= TvStore.MAX_CUSTOM_WALLPAPERS) {
            Toast.makeText(
                this,
                "${TvStore.MAX_CUSTOM_WALLPAPERS} wallpaper ho gaye hain — pehle ek hatao (tile par OK dabaye rakho)",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        // BRAVIA jaise TVon me system file picker (DocumentsUI) hota hi
        // nahi — ACTION_OPEN_DOCUMENT "No application can handle this
        // action" kehta tha. Isliye apna hi photo browser kholo: koi
        // bahari app nahi chahiye, photos TV/USB se andar hi mil jati hain.
        startActivityForResult(Intent(this, PhotoBrowserActivity::class.java), REQ_BROWSE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_BROWSE || resultCode != RESULT_OK) return
        // Photo browser ne photo jodkar mode pehle hi set kar diya hai —
        // bas screen taaza karke nayi tile par focus le jao.
        val added = data?.getStringExtra(PhotoBrowserActivity.EXTRA_ADDED) ?: return
        rebuild(focusTag = "wp_custom_$added")
    }

    private fun confirmDelete(fileName: String) {
        AlertDialog.Builder(this)
            .setTitle("Wallpaper hatao?")
            .setMessage("Ye photo wallpaperon se hamesha ke liye hat jayegi.")
            .setPositiveButton("Hatao") { _, _ ->
                Wallpapers.delete(this, fileName)
                TvStore.removeCustomWallpaper(this, fileName)
                Toast.makeText(this, "Wallpaper hat gaya", Toast.LENGTH_SHORT).show()
                rebuild(focusTag = "wp_add")
            }
            .setNegativeButton("Rehne do", null)
            .show()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()

    private companion object {
        const val REQ_BROWSE = 701
    }
}
