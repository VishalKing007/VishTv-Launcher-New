package com.vishal.vishietv

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.vishal.vishietv.boost.BoostManager
import com.vishal.vishietv.data.AppRepository
import com.vishal.vishietv.data.DeviceStats
import com.vishal.vishietv.data.TvApp
import com.vishal.vishietv.data.TvStore
import com.vishal.vishietv.data.Wallpapers
import com.vishal.vishietv.ui.AppTile
import com.vishal.vishietv.ui.ToolTile
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Vishie TV — Sony Bravia 2015 (Android 8) ke liye halka TV launcher.
 * Upar ghadi + RAM/Storage ki asli haalat; Pasandida, Haal hi me khule,
 * Saare Apps (faltu system apps chhupe hue), aur TV Tools ki lambi row.
 * Screen-fit ke liye kinare par overscan-safe jagah chhodi gayi hai —
 * purane TV kinare kaat dete hain. Saara kaam remote (D-pad) se.
 */
class MainActivity : Activity() {

    private data class TvTheme(
        val naam: String,
        val base: IntArray,
        val glowA: Int,
        val glowB: Int,
        val accent: Int
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val uiHandler = Handler(Looper.getMainLooper())

    private lateinit var rootView: FrameLayout
    private lateinit var logoMark: View
    private lateinit var blobTop: View
    private lateinit var blobBottom: View
    private lateinit var content: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var ramChip: TextView
    private lateinit var storageChip: TextView
    private lateinit var loadingView: TextView

    private var allApps: List<TvApp> = emptyList()
    private var boosting = false
    private var launchedSinceBuild = false

    // Wallpaper: kaunsa background abhi laga hai, taaki har resume par
    // bekaar dobara na banana pade.
    private var appliedBgKey: String? = null
    private var bgBitmap: Bitmap? = null

    // Boost panel ki haalat.
    private var boostOverlay: FrameLayout? = null
    private var boostSweep: ObjectAnimator? = null
    private var boostOkButton: TextView? = null
    private var boostStatusView: TextView? = null
    private var boostSweepTrack: FrameLayout? = null
    private var boostResultBox: LinearLayout? = null
    private var boostFreedView: TextView? = null
    private var boostDetailView: TextView? = null
    private val boostAutoDismiss = Runnable { dismissBoostPanel() }

    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            uiHandler.postDelayed(this, 20_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildRoot())
        loadApps()
        clockTick.run()
    }

    override fun onResume() {
        super.onResume()
        applyBackground()
        refreshChips()
        if (launchedSinceBuild) {
            launchedSinceBuild = false
            buildSections()
        }
    }

    override fun onBackPressed() {
        // Boost panel khula ho to Back usi ko band kare, launcher ko nahi.
        if (boostOverlay != null) {
            dismissBoostPanel()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(clockTick)
        executor.shutdownNow()
        super.onDestroy()
    }

    // ---------- Theme / background ----------

    private fun currentTheme(): TvTheme =
        THEMES[TvStore.theme(this).coerceIn(0, THEMES.lastIndex)]

    private fun makeBackground(theme: TvTheme): Bitmap {
        val w = resources.displayMetrics.widthPixels.coerceAtLeast(960)
        val h = resources.displayMetrics.heightPixels.coerceAtLeast(540)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            theme.base, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        // Do halki rangat wali roshni — upar-daye aur neeche-baye.
        paint.shader = RadialGradient(
            w * 0.88f, -h * 0.12f, w * 0.55f,
            withAlpha(theme.glowA, 90), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = RadialGradient(
            w * 0.02f, h * 1.08f, w * 0.5f,
            withAlpha(theme.glowB, 70), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /**
     * Home ka background: agar wallpaper laga hai (apni photo ya
     * ready-made look) to wahi, warna theme wala rang-biranga look.
     * Photo par halka pardah padta hai taaki tiles saaf dikhen.
     * Key yaad rakhta hai — bina badlaav ke dobara nahi banata.
     */
    private fun applyBackground(force: Boolean = false) {
        if (!::rootView.isInitialized) return
        val theme = currentTheme()
        val mode = TvStore.wallpaperMode(this)
        val key = "$mode|${TvStore.theme(this)}"
        if (!force && key == appliedBgKey) return

        val w = resources.displayMetrics.widthPixels.coerceAtLeast(960)
        val h = resources.displayMetrics.heightPixels.coerceAtLeast(540)
        var bmp: Bitmap? = null
        if (mode != "theme") {
            val raw = Wallpapers.resolveBitmap(this, mode, w, h)
            if (raw != null) {
                // Photo par zyada pardah, ready-made look par halka —
                // wo pehle se hi gehre rangon me bane hain.
                val scrim = if (mode.startsWith("custom:")) 88 else 34
                bmp = Wallpapers.cover(raw, w, h, scrim)
                if (bmp !== raw && mode.startsWith("custom:")) raw.recycle()
            } else {
                // File gayab ho gayi — chup-chaap theme par wapas.
                TvStore.setWallpaperMode(this, "theme")
            }
        }
        if (bmp == null) bmp = makeBackground(theme)

        val old = bgBitmap
        bgBitmap = bmp
        appliedBgKey = "${TvStore.wallpaperMode(this)}|${TvStore.theme(this)}"
        rootView.background = BitmapDrawable(resources, bmp)
        if (old != null && old !== bmp) old.recycle()
    }

    private fun markDrawable(theme: TvTheme): GradientDrawable {
        val d = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(theme.glowA, theme.glowB)
        )
        d.cornerRadius = dp(8).toFloat()
        return d
    }

    // ---------- UI banana ----------

    private fun radialBlob(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.gradientType = GradientDrawable.RADIAL_GRADIENT
        d.gradientRadius = dp(340).toFloat()
        d.colors = intArrayOf(withAlpha(color, 115), Color.TRANSPARENT)
        return d
    }

    /** tvOS wali ambient roshni — jo app focus me, peeche usi ke rang ki halki chamak. */
    private fun setAmbient(colorTop: Int, colorBottom: Int) {
        if (!::blobTop.isInitialized) return
        blobTop.background = radialBlob(colorTop)
        blobBottom.background = radialBlob(colorBottom)
    }

    private fun buildRoot(): View {
        val theme = currentTheme()
        rootView = FrameLayout(this)
        applyBackground()

        blobTop = View(this)
        blobTop.background = radialBlob(theme.glowA)
        val btParams = FrameLayout.LayoutParams(dp(680), dp(680))
        btParams.gravity = Gravity.TOP or Gravity.END
        btParams.topMargin = dp(-260)
        btParams.rightMargin = dp(-220)
        blobTop.layoutParams = btParams
        rootView.addView(blobTop)

        blobBottom = View(this)
        blobBottom.background = radialBlob(theme.glowB)
        val bbParams = FrameLayout.LayoutParams(dp(640), dp(640))
        bbParams.gravity = Gravity.BOTTOM or Gravity.START
        bbParams.bottomMargin = dp(-240)
        bbParams.leftMargin = dp(-220)
        blobBottom.layoutParams = bbParams
        rootView.addView(blobBottom)

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        // Overscan-safe padding: purana TV kinare kaat deta hai.
        column.setPadding(dp(56), dp(34), dp(56), dp(26))

        column.addView(buildTopBar(theme))

        scrollView = ScrollView(this)
        scrollView.isFillViewport = true
        scrollView.isVerticalScrollBarEnabled = false
        scrollView.overScrollMode = View.OVER_SCROLL_NEVER
        scrollView.clipChildren = false
        val scrollParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        )
        scrollParams.topMargin = dp(10)
        scrollView.layoutParams = scrollParams

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.clipChildren = false
        content.clipToPadding = false

        loadingView = sectionBodyText("Apps load ho rahe hain…")
        content.addView(loadingView)

        scrollView.addView(content)
        column.addView(scrollView)
        rootView.addView(column)
        return rootView
    }

    private fun buildTopBar(theme: TvTheme): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        logoMark = View(this)
        logoMark.background = markDrawable(theme)
        logoMark.layoutParams = LinearLayout.LayoutParams(dp(14), dp(30))
        bar.addView(logoMark)

        val logo = TextView(this)
        logo.text = "VISHIE TV"
        logo.setTextColor(0xFFF5F7FA.toInt())
        logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27f)
        logo.setTypeface(Typeface.DEFAULT_BOLD)
        logo.letterSpacing = 0.10f
        val logoParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        logoParams.leftMargin = dp(12)
        logo.layoutParams = logoParams
        bar.addView(logo)

        val spacer = View(this)
        spacer.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        bar.addView(spacer)

        ramChip = chip("RAM …")
        bar.addView(ramChip)

        storageChip = chip("Storage …")
        val stParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        stParams.leftMargin = dp(10)
        storageChip.layoutParams = stParams
        bar.addView(storageChip)

        val clockBox = LinearLayout(this)
        clockBox.orientation = LinearLayout.VERTICAL
        clockBox.gravity = Gravity.END
        val clockParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        clockParams.leftMargin = dp(22)
        clockBox.layoutParams = clockParams

        clockView = TextView(this)
        clockView.setTextColor(Color.WHITE)
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        clockView.setTypeface(Typeface.DEFAULT_BOLD)
        clockView.gravity = Gravity.END
        clockBox.addView(clockView)

        dateView = TextView(this)
        dateView.setTextColor(0xFF9FB3C8.toInt())
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        dateView.gravity = Gravity.END
        clockBox.addView(dateView)

        bar.addView(clockBox)
        return bar
    }

    private fun chip(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(0xFFE8EEF5.toInt())
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        tv.setTypeface(Typeface.DEFAULT_BOLD)
        tv.setPadding(dp(14), dp(6), dp(14), dp(6))
        val bg = GradientDrawable()
        bg.setColor(0xFF31435F.toInt())
        bg.cornerRadius = dp(999).toFloat()
        tv.background = bg
        return tv
    }

    private fun sectionTitle(text: String): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(24)
        params.bottomMargin = dp(6)
        row.layoutParams = params

        val barView = View(this)
        val barBg = GradientDrawable()
        barBg.setColor(currentTheme().accent)
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

    private fun sectionBodyText(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(0xFF9FB3C8.toInt())
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(30)
        tv.layoutParams = params
        return tv
    }

    private fun horizontalRow(): Pair<HorizontalScrollView, LinearLayout> {
        val hScroll = HorizontalScrollView(this)
        hScroll.isHorizontalScrollBarEnabled = false
        hScroll.clipChildren = false
        hScroll.clipToPadding = false
        hScroll.overScrollMode = View.OVER_SCROLL_NEVER
        hScroll.setPadding(dp(14), dp(14), dp(14), dp(14))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.clipChildren = false
        row.clipToPadding = false
        hScroll.addView(row)
        return hScroll to row
    }

    // ---------- Sections ----------

    private fun buildSections(focusPackage: String? = null) {
        content.removeAllViews()
        val favs = TvStore.favourites(this, allApps)

        if (favs.isNotEmpty()) {
            content.addView(sectionTitle("PASANDIDA APPS"))
            val (hScroll, row) = horizontalRow()
            for (app in favs) row.addView(appTile(app))
            content.addView(hScroll)
        }

        val recents = TvStore.recentApps(this, allApps)
        if (recents.isNotEmpty()) {
            content.addView(sectionTitle("HAAL HI ME KHULE"))
            val (hScroll, row) = horizontalRow()
            for (app in recents) row.addView(appTile(app))
            content.addView(hScroll)
        }

        content.addView(sectionTitle("SAARE APPS (${allApps.size})"))
        val tileW = dp(156)
        val gap = dp(10)
        for (rowApps in allApps.chunked(5)) {
            val rowLayout = LinearLayout(this)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            rowLayout.clipChildren = false
            rowLayout.clipToPadding = false
            rowLayout.setPadding(dp(8), dp(8), dp(8), dp(8))
            for (app in rowApps) {
                val tile = appTile(app, widthDp = 156, cardHeightDp = 100)
                val lp = LinearLayout.LayoutParams(tileW, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.rightMargin = gap
                tile.layoutParams = lp
                rowLayout.addView(tile)
            }
            content.addView(rowLayout)
        }

        content.addView(sectionTitle("TV TOOLS"))
        val (toolsScroll, toolsRow) = horizontalRow()
        toolsRow.addView(toolTile("RAM Saaf", "Saare apps band karo", 0xFFFFB84D.toInt(), "tool_boost") { runBoost() })
        toolsRow.addView(toolTile("Wallpaper", "Apni photo lagao", 0xFF00ACC1.toInt(), "tool_wallpaper") {
            try {
                startActivity(Intent(this, WallpaperActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Wallpaper screen nahi khuli", Toast.LENGTH_SHORT).show()
            }
        })
        toolsRow.addView(toolTile("Storage", "Jagah ki jaankari", 0xFF42A5F5.toInt(), "tool_storage") {
            openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        })
        toolsRow.addView(toolTile("Wi-Fi", "Network settings", 0xFF26A69A.toInt(), "tool_wifi") {
            openSettings(Settings.ACTION_WIFI_SETTINGS)
        })
        toolsRow.addView(toolTile("Bluetooth", "Remote aur devices", 0xFF5C6BC0.toInt(), "tool_bluetooth") {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
        })
        toolsRow.addView(toolTile("Picture", "Tasveer ki settings", 0xFFEC407A.toInt(), "tool_picture") {
            openSettings(Settings.ACTION_DISPLAY_SETTINGS)
        })
        toolsRow.addView(toolTile("Sound", "Awaaz ki settings", 0xFF66BB6A.toInt(), "tool_sound") {
            openSettings(Settings.ACTION_SOUND_SETTINGS)
        })
        toolsRow.addView(toolTile("Network", "IP aur connection", 0xFF29B6F6.toInt(), "tool_network") {
            showNetworkInfo()
        })
        toolsRow.addView(toolTile("Themes", "Rang badlo", 0xFFAB47BC.toInt(), "tool_themes") {
            showThemePicker()
        })
        toolsRow.addView(toolTile("Apps", "Apps manage karo", 0xFF8D6E63.toInt(), "tool_apps") {
            openSettings(Settings.ACTION_APPLICATION_SETTINGS)
        })
        toolsRow.addView(toolTile("Hidden", "Chhupaye apps wapas", 0xFF78909C.toInt(), "tool_hidden") {
            showHiddenApps()
        })
        toolsRow.addView(toolTile("About", "TV ki jaankari", 0xFFEF5350.toInt(), "tool_about") {
            openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS)
        })
        toolsRow.addView(toolTile("Settings", "Saari settings", 0xFFFFA726.toInt(), "tool_settings") {
            openSettings(Settings.ACTION_SETTINGS)
        })
        content.addView(toolsScroll)

        content.post {
            val target: View? = if (focusPackage != null) {
                content.findViewWithTag(focusPackage)
            } else {
                favs.firstOrNull()?.let { content.findViewWithTag<View>(it.packageName) }
                    ?: allApps.firstOrNull()?.let { content.findViewWithTag<View>(it.packageName) }
            }
            target?.requestFocus()
        }
    }

    private fun appTile(app: TvApp, widthDp: Int = 196, cardHeightDp: Int = 118): AppTile {
        val tile = AppTile(this)
        val lp = LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.rightMargin = dp(14)
        tile.layoutParams = lp
        tile.setCardHeight(cardHeightDp)
        tile.bindApp(app, ::launchApp, ::showAppMenu) {
            val (a, b) = AppTile.gradientFor(app.packageName)
            setAmbient(a, b)
        }
        return tile
    }

    private fun toolTile(
        title: String, desc: String, accent: Int, tagValue: String, onClick: () -> Unit
    ): ToolTile {
        val tile = ToolTile(this, accent)
        val lp = LinearLayout.LayoutParams(dp(168), dp(104))
        lp.rightMargin = dp(12)
        tile.layoutParams = lp
        tile.bind(title, desc, tagValue, onClick) {
            setAmbient(accent, currentTheme().glowB)
        }
        return tile
    }

    // ---------- Kaam ----------

    private fun loadApps(focusPackage: String? = null) {
        if (AppRepository.overrideApps != null) {
            allApps = AppRepository.loadApps(this)
            buildSections(focusPackage)
            refreshChips()
            return
        }
        executor.execute {
            val apps = AppRepository.loadApps(this)
            runOnUiThread {
                allApps = apps
                buildSections(focusPackage)
                refreshChips()
            }
        }
    }

    private fun launchApp(app: TvApp) {
        val intent = AppRepository.launchIntent(packageManager, app)
        if (intent == null) {
            Toast.makeText(this, "${app.label} nahi khula", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            TvStore.recordLaunch(this, app.packageName)
            launchedSinceBuild = true
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "${app.label} nahi khula", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppMenu(app: TvApp) {
        val isFav = TvStore.isFavourite(this, app.packageName)
        val items = arrayOf(
            if (isFav) "Pasandida se hatao" else "Pasandida me jodo",
            "App chhupao",
            "App ki jaankari",
            "Uninstall"
        )
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        TvStore.toggleFavourite(this, app.packageName)
                        buildSections(focusPackage = app.packageName)
                    }
                    1 -> {
                        TvStore.hide(this, app)
                        Toast.makeText(
                            this, "${app.label} chhup gaya — wapas laane ke liye TV Tools me Hidden",
                            Toast.LENGTH_LONG
                        ).show()
                        loadApps()
                    }
                    2 -> try {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${app.packageName}")
                            )
                        )
                    } catch (e: Exception) {
                        Toast.makeText(this, "Nahi khula", Toast.LENGTH_SHORT).show()
                    }
                    3 -> try {
                        startActivity(
                            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                        )
                    } catch (e: Exception) {
                        Toast.makeText(this, "Nahi khula", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showHiddenApps() {
        val hidden = TvStore.hiddenApps(this)
        if (hidden.isEmpty()) {
            Toast.makeText(this, "Koi app chhupaya nahi hai", Toast.LENGTH_SHORT).show()
            content.findViewWithTag<View>("tool_hidden")?.requestFocus()
            return
        }
        val pkgs = hidden.keys.toList()
        val labels = pkgs.map { hidden[it] ?: it }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Chhupaye apps — wapas lane ke liye chuno")
            .setItems(labels) { _, which ->
                TvStore.unhide(this, pkgs[which])
                Toast.makeText(this, "${labels[which]} wapas aa gaya", Toast.LENGTH_SHORT).show()
                loadApps(focusPackage = "tool_hidden")
            }
            .setNegativeButton("Band karo", null)
            .show()
    }

    private fun showThemePicker() {
        val names = THEMES.map { it.naam }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Theme chuno")
            .setItems(names) { _, which ->
                TvStore.setTheme(this, which)
                // Theme chunna matlab theme look hi — wallpaper hat jata hai.
                TvStore.setWallpaperMode(this, "theme")
                val theme = currentTheme()
                applyBackground(force = true)
                logoMark.background = markDrawable(theme)
                setAmbient(theme.glowA, theme.glowB)
                buildSections(focusPackage = "tool_themes")
            }
            .show()
    }

    private fun showNetworkInfo() {
        val lines = mutableListOf<String>()
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val active: NetworkInfo? = try { cm.activeNetworkInfo } catch (e: Exception) { null }
        val connected = active?.isConnected == true
        var ip: String? = null
        if (!connected) {
            lines.add("Connection: band hai")
        } else {
            when (active?.type) {
                ConnectivityManager.TYPE_WIFI -> {
                    lines.add("Connection: Wi-Fi")
                    val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                    val info = try { wm.connectionInfo } catch (e: Exception) { null }
                    val ssid = info?.ssid?.trim('"')
                    if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                        lines.add("Network: $ssid")
                    }
                    info?.let {
                        ip = formatIp(it.ipAddress)
                        if (it.linkSpeed > 0) lines.add("Speed: ${it.linkSpeed} Mbps")
                    }
                }
                ConnectivityManager.TYPE_ETHERNET -> lines.add("Connection: LAN (cable)")
                else -> lines.add("Connection: juda hua hai")
            }
        }
        if (ip == null) ip = firstIpv4()
        lines.add("IP address: ${ip ?: "nahi mila"}")
        AlertDialog.Builder(this)
            .setTitle("Network ki haalat")
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton("Theek hai", null)
            .show()
        content.findViewWithTag<View>("tool_network")?.requestFocus()
    }

    private fun formatIp(value: Int): String =
        "${value and 0xFF}.${value shr 8 and 0xFF}.${value shr 16 and 0xFF}.${value shr 24 and 0xFF}"

    private fun firstIpv4(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var found: String? = null
            while (interfaces.hasMoreElements() && found == null) {
                val addresses = interfaces.nextElement().inetAddresses
                while (addresses.hasMoreElements() && found == null) {
                    val addr = addresses.nextElement()
                    val host = addr.hostAddress
                    if (!addr.isLoopbackAddress && host != null && host.contains('.')) {
                        found = host
                    }
                }
            }
            found
        } catch (e: Exception) {
            null
        }
    }

    private fun runBoost() {
        if (boosting) return
        boosting = true
        showBoostPanel()
        executor.execute {
            val result = BoostManager.boost(this)
            runOnUiThread {
                boosting = false
                refreshChips()
                if (boostOverlay != null) {
                    fillBoostResult(result)
                } else {
                    showBoostResultPanel(result)
                }
            }
        }
    }

    /**
     * Boost ka apna panel — pehle sweep animation ke saath "ho raha hai",
     * phir asli pehle/baad ke number. Screenshot test bhi yahi asal
     * layout istemal karta hai (showBoostResultPanel se seedha result).
     */
    private fun showBoostPanel() {
        dismissBoostPanel()
        val overlay = FrameLayout(this)
        overlay.setBackgroundColor(0xB3000000.toInt())
        overlay.isFocusable = true
        overlay.isClickable = true
        overlay.tag = "boost_panel"

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(30), dp(26), dp(30), dp(26))
        val cardBg = GradientDrawable()
        cardBg.gradientType = GradientDrawable.LINEAR_GRADIENT
        cardBg.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        cardBg.colors = intArrayOf(0xFF27344A.toInt(), 0xFF1B2434.toInt())
        cardBg.cornerRadius = dp(20).toFloat()
        cardBg.setStroke(dp(1), 0xFF2E3D55.toInt())
        card.background = cardBg
        card.layoutParams = FrameLayout.LayoutParams(
            dp(560), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        )

        val title = TextView(this)
        title.text = "RAM SAAF"
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
        title.setTypeface(Typeface.DEFAULT_BOLD)
        title.letterSpacing = 0.10f
        card.addView(title)

        val status = TextView(this)
        status.text = "Apps band ho rahe hain…"
        status.setTextColor(0xFF9FB3C8.toInt())
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        val statusParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        statusParams.topMargin = dp(8)
        status.layoutParams = statusParams
        card.addView(status)
        boostStatusView = status

        // Sweep: track ke andar daudti hui roshni, jab tak kaam chale.
        val track = FrameLayout(this)
        val trackBg = GradientDrawable()
        trackBg.setColor(0xFF101725.toInt())
        trackBg.cornerRadius = dp(999).toFloat()
        track.background = trackBg
        val trackParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(10)
        )
        trackParams.topMargin = dp(18)
        track.layoutParams = trackParams
        val glow = View(this)
        val glowBg = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.TRANSPARENT, 0xFFFFB84D.toInt(), Color.TRANSPARENT
            )
        )
        glow.background = glowBg
        glow.layoutParams = FrameLayout.LayoutParams(dp(150), FrameLayout.LayoutParams.MATCH_PARENT)
        track.addView(glow)
        card.addView(track)
        boostSweepTrack = track

        // Result wala hissa — pehle chhupa, number aate hi dikhta hai.
        val resultBox = LinearLayout(this)
        resultBox.orientation = LinearLayout.VERTICAL
        resultBox.visibility = View.GONE
        val resultParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        resultParams.topMargin = dp(16)
        resultBox.layoutParams = resultParams

        val freed = TextView(this)
        freed.setTextColor(0xFF3DDC97.toInt())
        freed.setTextSize(TypedValue.COMPLEX_UNIT_SP, 31f)
        freed.setTypeface(Typeface.DEFAULT_BOLD)
        resultBox.addView(freed)
        boostFreedView = freed

        val detail = TextView(this)
        detail.setTextColor(0xFFE8EEF5.toInt())
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
        detail.setLineSpacing(0f, 1.25f)
        val detailParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        detailParams.topMargin = dp(8)
        detail.layoutParams = detailParams
        resultBox.addView(detail)
        boostDetailView = detail

        val note = TextView(this)
        note.text = "Kuch system apps ko Android khud dobara chalu kar sakta hai."
        note.setTextColor(0xFF7186A0.toInt())
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        val noteParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        noteParams.topMargin = dp(10)
        note.layoutParams = noteParams
        resultBox.addView(note)
        card.addView(resultBox)
        boostResultBox = resultBox

        val ok = TextView(this)
        ok.text = "Theek hai"
        ok.tag = "boost_ok"
        ok.setTextColor(Color.WHITE)
        ok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
        ok.setTypeface(Typeface.DEFAULT_BOLD)
        ok.gravity = Gravity.CENTER
        ok.setPadding(dp(28), dp(10), dp(28), dp(10))
        val okBg = GradientDrawable()
        okBg.setColor(0xFF31435F.toInt())
        okBg.cornerRadius = dp(999).toFloat()
        okBg.setStroke(dp(1), 0xFF3D5878.toInt())
        ok.background = okBg
        ok.isFocusable = true
        ok.isClickable = true
        ok.visibility = View.GONE
        val okParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        okParams.topMargin = dp(22)
        okParams.gravity = Gravity.CENTER_HORIZONTAL
        ok.layoutParams = okParams
        ok.setOnFocusChangeListener { _, hasFocus -> styleBoostOk(hasFocus, animate = true) }
        ok.setOnClickListener { dismissBoostPanel() }
        card.addView(ok)
        boostOkButton = ok

        overlay.addView(card)
        rootView.addView(overlay)
        boostOverlay = overlay

        track.post {
            val w = track.width
            if (w > 0 && boostOverlay === overlay) {
                boostSweep = ObjectAnimator.ofFloat(
                    glow, "translationX", -dp(150).toFloat(), w.toFloat()
                ).apply {
                    duration = 950
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
            }
        }
        overlay.requestFocus()
    }

    private fun fillBoostResult(result: BoostManager.Result) {
        if (boostOverlay == null) return
        boostSweep?.cancel()
        boostSweep = null
        boostSweepTrack?.visibility = View.GONE
        boostStatusView?.text = "Ho gaya — TV halka ho gaya."
        val freed = result.freeRamAfterMb - result.freeRamBeforeMb
        boostFreedView?.text = if (freed > 0) {
            "+$freed MB khaali hua"
        } else {
            "RAM saaf ho gaya"
        }
        boostDetailView?.text =
            "${result.appsStopped} apps band kiye\n" +
                "RAM free: ${result.freeRamBeforeMb} MB → ${result.freeRamAfterMb} MB"
        boostResultBox?.visibility = View.VISIBLE
        boostOkButton?.visibility = View.VISIBLE
        boostOkButton?.requestFocus()
        uiHandler.removeCallbacks(boostAutoDismiss)
        uiHandler.postDelayed(boostAutoDismiss, 9_000)
    }

    /** Screenshot test ke liye: panel ko seedha result wali haalat me. */
    internal fun showBoostResultPanel(result: BoostManager.Result) {
        showBoostPanel()
        fillBoostResult(result)
    }

    private fun styleBoostOk(focused: Boolean, animate: Boolean) {
        val btn = boostOkButton ?: return
        val bg = btn.background as? GradientDrawable ?: return
        if (focused) {
            bg.setStroke(dp(3), Color.WHITE)
            if (animate) {
                btn.animate().scaleX(1.06f).scaleY(1.06f).setDuration(150).start()
            } else {
                btn.scaleX = 1.06f
                btn.scaleY = 1.06f
            }
        } else {
            bg.setStroke(dp(1), 0xFF3D5878.toInt())
            if (animate) {
                btn.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            } else {
                btn.scaleX = 1f
                btn.scaleY = 1f
            }
        }
    }

    /** Screenshot test focus ki antim haalat seedha lagata hai. */
    internal fun applyBoostOkFocus(focused: Boolean) = styleBoostOk(focused, animate = false)

    private fun dismissBoostPanel() {
        uiHandler.removeCallbacks(boostAutoDismiss)
        boostSweep?.cancel()
        boostSweep = null
        boostOverlay?.let { rootView.removeView(it) }
        boostOverlay = null
        boostOkButton = null
        boostStatusView = null
        boostSweepTrack = null
        boostResultBox = null
        boostFreedView = null
        boostDetailView = null
        if (::content.isInitialized) {
            content.findViewWithTag<View>("tool_boost")?.requestFocus()
        }
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "Settings nahi khuli", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshChips() {
        if (!::ramChip.isInitialized) return
        val stats = try {
            DeviceStats.read(this)
        } catch (e: Exception) {
            return
        }
        ramChip.text = "RAM free ${stats.freeRamMb} MB"
        storageChip.text = "Storage free ${"%.1f".format(stats.freeStorageGb)} GB"
    }

    private fun updateClock() {
        if (!::clockView.isInitialized) return
        val now = Date()
        clockView.text = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(now)
        dateView.text = SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH).format(now)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()

    companion object {
        private val THEMES = listOf(
            TvTheme(
                "Ocean",
                intArrayOf(0xFF0A1424.toInt(), 0xFF12233C.toInt(), 0xFF080D16.toInt()),
                0xFF2563EB.toInt(), 0xFF7C3AED.toInt(), 0xFF42A5F5.toInt()
            ),
            TvTheme(
                "Sunset",
                intArrayOf(0xFF180B12.toInt(), 0xFF33121F.toInt(), 0xFF100810.toInt()),
                0xFFF97316.toInt(), 0xFFEF4444.toInt(), 0xFFFFB84D.toInt()
            ),
            TvTheme(
                "Forest",
                intArrayOf(0xFF08130D.toInt(), 0xFF10301F.toInt(), 0xFF070F0A.toInt()),
                0xFF22C55E.toInt(), 0xFF0EA5E9.toInt(), 0xFF66BB6A.toInt()
            ),
            TvTheme(
                "Grape",
                intArrayOf(0xFF130B22.toInt(), 0xFF27124A.toInt(), 0xFF0C0817.toInt()),
                0xFFA855F7.toInt(), 0xFFEC4899.toInt(), 0xFFAB47BC.toInt()
            )
        )
    }
}
