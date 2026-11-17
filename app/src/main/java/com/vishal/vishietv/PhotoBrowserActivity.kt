package com.vishal.vishietv

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.vishal.vishietv.data.PhotoScanner
import com.vishal.vishietv.data.TvStore
import com.vishal.vishietv.data.Wallpapers
import com.vishal.vishietv.ui.WallpaperTile
import java.io.File
import java.util.concurrent.Executors

/**
 * Vishie TV ka apna photo browser — TV ke andar ke storage aur lagi
 * hui USB drive ko ghumkar photos dikhata hai. Ye isliye bana kyunki
 * BRAVIA jaise TVon me system file picker (DocumentsUI) hota hi nahi
 * aur ACTION_OPEN_DOCUMENT "No application can handle this action"
 * kehkar band ho jata tha. Ab koi bahari app nahi chahiye.
 */
class PhotoBrowserActivity : Activity() {

    private val executor = Executors.newFixedThreadPool(3)
    private val thumbCache = LruCache<String, Bitmap>(48)
    private lateinit var rootView: FrameLayout
    private lateinit var contentBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootView = FrameLayout(this)
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0A1424.toInt(), 0xFF080D16.toInt())
        )
        rootView.background = bg
        setContentView(rootView)

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.clipChildren = false

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.clipChildren = false
        column.clipToPadding = false
        column.setPadding(dp(56), dp(34), dp(56), dp(30))

        val title = TextView(this)
        title.text = "PHOTO CHUNO"
        title.setTextColor(0xFFF5F7FA.toInt())
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        title.setTypeface(Typeface.DEFAULT_BOLD)
        title.letterSpacing = 0.08f
        column.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "TV ya USB se ek photo chuno — wahi tumhara wallpaper ban jayegi."
        subtitle.setTextColor(0xFF9FB3C8.toInt())
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        val subParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        subParams.topMargin = dp(6)
        subtitle.layoutParams = subParams
        column.addView(subtitle)

        contentBox = LinearLayout(this)
        contentBox.orientation = LinearLayout.VERTICAL
        contentBox.clipChildren = false
        contentBox.clipToPadding = false
        val boxParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        boxParams.topMargin = dp(22)
        contentBox.layoutParams = boxParams
        column.addView(contentBox)

        scroll.addView(column)
        rootView.addView(scroll)

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startScan()
        } else {
            showLoading("Storage ki permission ka intezaar hai…")
            try {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_PERM
                )
            } catch (e: Exception) {
                showDenied()
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERM) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScan()
        } else {
            showDenied()
        }
    }

    // ---------- Halatein (loading / mana / khaali / grid) ----------

    private fun showLoading(message: String) {
        contentBox.removeAllViews()
        contentBox.tag = "browser_loading"
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(4), dp(26), dp(4), dp(26))

        val spinner = ProgressBar(this)
        spinner.layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        row.addView(spinner)

        val text = TextView(this)
        text.text = message
        text.setTextColor(0xFFC7D3E0.toInt())
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        val textParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        textParams.leftMargin = dp(16)
        text.layoutParams = textParams
        row.addView(text)
        contentBox.addView(row)
    }

    private fun showDenied() {
        contentBox.removeAllViews()
        contentBox.tag = "browser_denied"
        contentBox.addView(messageText("Permission chahiye"))
        contentBox.addView(
            detailText(
                "Photos dikhane ke liye storage padhne ki permission lagegi. " +
                    "Bina iske TV aur USB ki photos nahi dikhengi — koi photo app se bahar nahi jati."
            )
        )
        val retry = wideButton(
            "Phir se koshish", "Permission ka dabba phir khulega"
        ) {
            try {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_PERM
                )
                showLoading("Storage ki permission ka intezaar hai…")
            } catch (e: Exception) {
                Toast.makeText(this, "Permission nahi khuli", Toast.LENGTH_SHORT).show()
            }
        }
        retry.tag = "perm_retry"
        val params = LinearLayout.LayoutParams(dp(430), ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(18)
        retry.layoutParams = params
        contentBox.addView(retry)
        contentBox.post { rootView.findViewWithTag<View>("perm_retry")?.requestFocus() }
    }

    private fun showEmpty() {
        contentBox.removeAllViews()
        contentBox.tag = "browser_empty"
        contentBox.addView(messageText("Koi photo nahi mili"))
        contentBox.addView(
            detailText("Koi photo nahi mili — USB lagakar dekho, ya TV storage me koi photo rakhkar phir aao.")
        )
        addSystemPickerOption()
        val ok = wideButton("Theek hai", "Wapas wallpaper wali screen par") { finish() }
        ok.tag = "empty_ok"
        val params = LinearLayout.LayoutParams(dp(430), ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(18)
        ok.layoutParams = params
        contentBox.addView(ok)
        contentBox.post { rootView.findViewWithTag<View>("empty_ok")?.requestFocus() }
    }

    // ---------- Scan aur grid ----------

    private fun startScan() {
        showLoading("Photos dhoondh rahe hain…")
        executor.execute {
            val volumes = PhotoScanner.volumes(this)
            val photos = try {
                PhotoScanner.scan(this)
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (photos.isEmpty()) showEmpty() else showGrid(volumes, photos)
            }
        }
    }

    private fun showGrid(
        volumes: List<PhotoScanner.Volume>, photos: List<PhotoScanner.Photo>
    ) {
        contentBox.removeAllViews()
        contentBox.tag = "browser_grid"
        var index = 0
        var firstTag: String? = null
        for (vol in volumes) {
            val mine = photos.filter { it.volumeRoot == vol.root }
            if (mine.isEmpty()) continue
            contentBox.addView(sectionTitle(vol.label.uppercase()))
            for (chunk in mine.chunked(COLUMNS)) {
                val rowLayout = LinearLayout(this)
                rowLayout.orientation = LinearLayout.HORIZONTAL
                rowLayout.clipChildren = false
                rowLayout.clipToPadding = false
                rowLayout.setPadding(dp(12), dp(10), dp(12), dp(10))
                for (photo in chunk) {
                    val tile = WallpaperTile(this)
                    val lp = LinearLayout.LayoutParams(
                        dp(WallpaperTile.TILE_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    lp.rightMargin = dp(16)
                    tile.layoutParams = lp
                    val tagValue = "photo_$index"
                    if (firstTag == null) firstTag = tagValue
                    index++
                    tile.bindPhoto(
                        null,
                        photo.file.nameWithoutExtension.ifBlank { photo.file.name },
                        tagValue, isActive = false,
                        onClick = { addFromFile(photo.file) }
                    )
                    rowLayout.addView(tile)
                    loadThumb(tile, photo.file)
                }
                contentBox.addView(rowLayout)
            }
        }
        addSystemPickerOption()
        val focusTarget = firstTag
        contentBox.post {
            focusTarget?.let { rootView.findViewWithTag<View>(it)?.requestFocus() }
        }
    }

    private fun loadThumb(tile: WallpaperTile, file: File) {
        val key = file.absolutePath
        thumbCache.get(key)?.let {
            tile.updateBitmap(it)
            return
        }
        executor.execute {
            val bmp = decodeThumb(file, dp(WallpaperTile.TILE_WIDTH_DP))
            if (bmp != null) {
                thumbCache.put(key, bmp)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) tile.updateBitmap(bmp)
                }
            }
        }
    }

    private fun decodeThumb(file: File, reqW: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && sample < 64) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return try {
            BitmapFactory.decodeFile(file.path, opts)
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Photo jodna ----------

    private fun addFromFile(file: File) {
        if (TvStore.customWallpapers(this).size >= TvStore.MAX_CUSTOM_WALLPAPERS) {
            Toast.makeText(
                this,
                "${TvStore.MAX_CUSTOM_WALLPAPERS} wallpaper ho gaye hain — pehle ek hatao (wallpaper screen me tile par OK dabaye rakho)",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        Toast.makeText(this, "Photo aa rahi hai…", Toast.LENGTH_SHORT).show()
        executor.execute {
            val name = Wallpapers.importPhotoFile(this, file)
            runOnUiThread {
                if (name == null) {
                    Toast.makeText(
                        this, "Ye photo nahi khuli — koi aur photo try karo",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    TvStore.addCustomWallpaper(this, name)
                    TvStore.setWallpaperMode(this, "custom:$name")
                    finishWithAdded(name)
                }
            }
        }
    }

    private fun finishWithAdded(name: String) {
        Toast.makeText(this, "Wallpaper jud gaya aur lag bhi gaya", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ADDED, name))
        finish()
    }

    // ---------- System picker (sirf jab TV me koi app use sambhal sake) ----------

    private fun systemPickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "image/*"
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
    }

    private fun systemPickerAvailable(): Boolean {
        return try {
            packageManager.queryIntentActivities(systemPickerIntent(), 0).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun addSystemPickerOption() {
        if (!systemPickerAvailable()) return
        val button = wideButton(
            "System picker", "TV ke file app se photo chuno"
        ) { openSystemPicker() }
        button.tag = "sys_picker"
        val params = LinearLayout.LayoutParams(dp(430), ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(22)
        button.layoutParams = params
        contentBox.addView(button)
    }

    private fun openSystemPicker() {
        if (TvStore.customWallpapers(this).size >= TvStore.MAX_CUSTOM_WALLPAPERS) {
            Toast.makeText(
                this,
                "${TvStore.MAX_CUSTOM_WALLPAPERS} wallpaper ho gaye hain — pehle ek hatao",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        try {
            startActivityForResult(systemPickerIntent(), REQ_SYS_PICK)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this, "Is TV par system picker nahi hai — upar wali photos me se chuno",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "File picker nahi khula", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SYS_PICK || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Ijazat na mili to bhi abhi copy ho jayegi — photo app ke paas aa jati hai.
        }
        Toast.makeText(this, "Photo aa rahi hai…", Toast.LENGTH_SHORT).show()
        executor.execute {
            val name = Wallpapers.importPhoto(this, uri)
            runOnUiThread {
                if (name == null) {
                    Toast.makeText(
                        this, "Ye photo nahi khuli — koi aur photo try karo",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    TvStore.addCustomWallpaper(this, name)
                    TvStore.setWallpaperMode(this, "custom:$name")
                    finishWithAdded(name)
                }
            }
        }
    }

    // ---------- Design ke chhote tukde (WallpaperActivity jaisi hi bhasha) ----------

    private fun messageText(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(0xFFF5F7FA.toInt())
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        tv.setTypeface(Typeface.DEFAULT_BOLD)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(14)
        tv.layoutParams = params
        return tv
    }

    private fun detailText(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(0xFF9FB3C8.toInt())
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(8)
        tv.layoutParams = params
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

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()

    companion object {
        const val EXTRA_ADDED = "added_file"
        private const val REQ_PERM = 801
        private const val REQ_SYS_PICK = 802
        private const val COLUMNS = 4
    }
}
