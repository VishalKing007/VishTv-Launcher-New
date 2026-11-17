package com.vishal.vishietv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Looper
import android.view.View
import com.vishal.vishietv.boost.BoostManager
import com.vishal.vishietv.data.AppRepository
import com.vishal.vishietv.data.TvApp
import com.vishal.vishietv.data.TvStore
import com.vishal.vishietv.data.Wallpapers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * v1.2 ki visual QA: (1) apni photo wala home, (2) wallpaper chunne ki
 * screen — "Mere Wallpaper" me ek judaa hua test photo ke saath,
 * (3) boost ka result panel (asli layout, number render ke liye stub),
 * (4) purana theme home — regression check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class VishieTvV12ShotsTest {

    private fun fakeApps(): List<TvApp> = listOf(
        TvApp("com.google.android.youtube.tv", ".MainActivity", "YouTube", null),
        TvApp("com.netflix.ninja", ".MainActivity", "Netflix", null),
        TvApp("com.amazon.amazonvideo.livingroom", ".MainActivity", "Prime Video", null),
        TvApp("com.hotstar.android", ".MainActivity", "Hotstar", null),
        TvApp("com.sonyliv", ".MainActivity", "Sony LIV", null),
        TvApp("com.spotify.music", ".MainActivity", "Spotify", null),
        TvApp("com.mxtech.videoplayer.ad", ".MainActivity", "MX Player", null),
        TvApp("com.jio.media.ondemand", ".MainActivity", "JioCinema", null),
        TvApp("com.graymatrix.did", ".MainActivity", "ZEE5", null),
        TvApp("com.google.android.apps.youtube.music", ".MainActivity", "YT Music", null),
        TvApp("com.google.android.apps.nbu.files", ".MainActivity", "Files", null),
        TvApp("com.android.tv.settings", ".MainActivity", "TV Settings", null)
    )

    /** Ek rang-birangi "photo" banakar app ki wallpaper wali jagah me rakhta hai. */
    private fun seedCustomWallpaper(context: Context): String {
        val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        // Shaam ka aasmaan
        paint.shader = LinearGradient(
            0f, 0f, 0f, 540f,
            intArrayOf(0xFF2B1055.toInt(), 0xFF7597DE.toInt(), 0xFFFF9A5A.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 960f, 540f, paint)
        // Sooraj
        paint.shader = null
        paint.color = 0xFFFFD166.toInt()
        canvas.drawCircle(660f, 330f, 62f, paint)
        // Pahaadiyaan
        paint.color = 0xFF1D2B1D.toInt()
        val hills = android.graphics.Path().apply {
            moveTo(0f, 540f)
            lineTo(0f, 420f)
            lineTo(240f, 350f)
            lineTo(470f, 440f)
            lineTo(700f, 370f)
            lineTo(960f, 450f)
            lineTo(960f, 540f)
            close()
        }
        canvas.drawPath(hills, paint)

        val name = "wp_test_photo.jpg"
        FileOutputStream(Wallpapers.customFile(context, name)).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bmp.recycle()
        TvStore.addCustomWallpaper(context, name)
        return name
    }

    @Test
    fun homeWithCustomWallpaper() {
        val app = RuntimeEnvironment.getApplication()
        val name = seedCustomWallpaper(app)
        TvStore.setWallpaperMode(app, "custom:$name")
        AppRepository.overrideApps = fakeApps()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val decor = controller.get().window.decorView
        layout(decor)
        focusTag(decor, "com.google.android.youtube.tv")
        layout(decor)
        save(decor, "1_home_wallpaper.png")
        controller.close()
        AppRepository.overrideApps = null
    }

    @Test
    fun wallpaperPicker() {
        val app = RuntimeEnvironment.getApplication()
        val name = seedCustomWallpaper(app)
        TvStore.setWallpaperMode(app, "custom:$name")
        val controller = Robolectric.buildActivity(WallpaperActivity::class.java).setup()
        val decor = controller.get().window.decorView
        layout(decor)
        focusTag(decor, "wp_custom_$name")
        layout(decor)
        save(decor, "2_wallpaper_picker.png")
        controller.close()
    }

    @Test
    fun boostResultPanel() {
        AppRepository.overrideApps = fakeApps()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val decor = activity.window.decorView
        layout(decor)
        // Asli panel, asli layout — sirf number render ke liye stub hain.
        activity.showBoostResultPanel(BoostManager.Result(9, 412, 768))
        settle()
        layout(decor)
        activity.applyBoostOkFocus(true)
        layout(decor)
        save(decor, "3_boost_result.png")
        controller.close()
        AppRepository.overrideApps = null
    }

    @Test
    fun homeThemeRegression() {
        AppRepository.overrideApps = fakeApps()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val decor = controller.get().window.decorView
        layout(decor)
        focusTag(decor, "com.google.android.youtube.tv")
        layout(decor)
        save(decor, "4_home_theme.png")
        controller.close()
        AppRepository.overrideApps = null
    }

    // ---------- madad ----------

    private var forcedView: View? = null

    private fun layout(root: View) {
        val w = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        val h = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        root.measure(w, h)
        root.layout(0, 0, 1920, 1080)
    }

    private fun settle() {
        Robolectric.getForegroundThreadScheduler().advanceBy(500, TimeUnit.MILLISECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun focusTag(root: View, tag: String) {
        settle()
        forcedView?.let { applyVisual(it, false) }
        val target = root.findViewWithTag<View>(tag)
        check(target != null) { "Tile nahi mili: $tag" }
        applyVisual(target, true)
        forcedView = target
        settle()
    }

    private fun applyVisual(view: View, focused: Boolean) {
        when (view) {
            is com.vishal.vishietv.ui.AppTile -> view.applyFocusVisual(focused, animate = false)
            is com.vishal.vishietv.ui.ToolTile -> view.applyFocusVisual(focused, animate = false)
            is com.vishal.vishietv.ui.WallpaperTile -> view.applyFocusVisual(focused, animate = false)
        }
    }

    private fun save(root: View, name: String) {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        root.draw(canvas)
        val dir = File("../shots-v12")
        dir.mkdirs()
        FileOutputStream(File(dir, name)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }
}
