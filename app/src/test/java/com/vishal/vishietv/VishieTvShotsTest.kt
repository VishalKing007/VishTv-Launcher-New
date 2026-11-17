package com.vishal.vishietv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.ScrollView
import com.vishal.vishietv.data.AppRepository
import com.vishal.vishietv.data.TvApp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Visual QA: asli MainActivity ko 1920x1080 (FHD, xhdpi) par render karke
 * PNG screenshots leta hai — wahi technique jo Vishie/Mausam me chali thi.
 * Test me asli apps nahi hote, isliye mashhoor apps ke naam par letter-tiles
 * bante hain; asal TV par unke asli icons dikhenge.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class VishieTvShotsTest {

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

    private var forcedView: View? = null

    @Test
    fun captureShots() {
        AppRepository.overrideApps = fakeApps()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val decor = activity.window.decorView
        layout(decor)

        // Shot A — home, pehli pasandida tile par focus.
        focusTag(decor, "com.google.android.youtube.tv")
        layout(decor)
        save(decor, "a_home.png")

        // Shot B — apps grid, MX Player par focus (neeche scroll karke).
        val scroll = findScroll(decor)
        scroll.scrollTo(0, 700)
        layout(decor)
        focusTag(decor, "com.mxtech.videoplayer.ad")
        layout(decor)
        save(decor, "b_apps_grid.png")

        // Shot C — TV Tools, RAM Boost tile par focus.
        scroll.scrollTo(0, 4000)
        layout(decor)
        focusTag(decor, "tool_boost")
        layout(decor)
        save(decor, "c_tools.png")

        controller.close()
        AppRepository.overrideApps = null
    }

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

    /**
     * Robolectric ki window ko asli window-focus nahi milta, isliye
     * requestFocus() yahan kaam nahi karta (probe test se sabit). Asli TV par
     * focus listener yahi antim haalat animation ke saath banata hai — shot
     * ke liye wahi haalat seedha laga dete hain.
     */
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
        }
    }

    private fun findScroll(view: View): ScrollView {
        if (view is ScrollView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                try {
                    return findScroll(view.getChildAt(i))
                } catch (e: IllegalStateException) {
                    // is branch me nahi hai, aage dhundo
                }
            }
        }
        throw IllegalStateException("ScrollView nahi mila")
    }

    private fun save(root: View, name: String) {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        root.draw(canvas)
        val dir = File("../shots")
        dir.mkdirs()
        FileOutputStream(File(dir, name)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }
}
