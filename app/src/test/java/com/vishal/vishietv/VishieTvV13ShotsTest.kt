package com.vishal.vishietv

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Looper
import android.view.View
import com.vishal.vishietv.data.PhotoScanner
import com.vishal.vishietv.data.TvStore
import com.vishal.vishietv.data.Wallpapers
import com.vishal.vishietv.ui.WallpaperTile
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
 * v1.3 ki visual QA: apna photo browser — (1) do nakli volumes (TV
 * storage + USB) se bhara grid, (2) khaali haalat ("koi photo nahi
 * mili"), (3) wallpaper screen ka regression. Asli TV par USB scan
 * code se verify hai; yahan layout asal files ke saath render hota hai.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class VishieTvV13ShotsTest {

    /** Ek rang-birangi nakli photo file banata hai. */
    private fun makePhoto(file: File, top: Int, bottom: Int, sun: Int, modified: Long) {
        file.parentFile?.mkdirs()
        val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, 0f, 360f, top, bottom, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 640f, 360f, paint)
        paint.shader = null
        paint.color = sun
        canvas.drawCircle(430f, 210f, 46f, paint)
        paint.color = 0xFF14231A.toInt()
        val hills = android.graphics.Path().apply {
            moveTo(0f, 360f)
            lineTo(0f, 280f)
            lineTo(210f, 225f)
            lineTo(420f, 300f)
            lineTo(640f, 250f)
            lineTo(640f, 360f)
            close()
        }
        canvas.drawPath(hills, paint)
        val format = if (file.extension.lowercase() == "png") {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
        FileOutputStream(file).use { out -> bmp.compress(format, 90, out) }
        bmp.recycle()
        file.setLastModified(modified)
    }

    private fun seedVolumes(app: Context) {
        val base = File(app.cacheDir, "fake_volumes")
        val tvDir = File(base, "tv")
        val usbDir = File(base, "usb")

        // TV storage: do photos (ek subfolder me), sabse purani aakhir me.
        makePhoto(
            File(tvDir, "IMG_2024.jpg"),
            0xFF2B1055.toInt(), 0xFFFF9A5A.toInt(), 0xFFFFD166.toInt(), 2_000L
        )
        makePhoto(
            File(tvDir, "Pictures/parivar.png"),
            0xFF0B3B5E.toInt(), 0xFF7FD1E8.toInt(), 0xFFFFFFFF.toInt(), 1_000L
        )
        // USB: sabse nayi photo + kuch kachra jo nahi dikhna chahiye.
        makePhoto(
            File(usbDir, "DCIM/goa_sunset.jpg"),
            0xFF5E1055.toInt(), 0xFFFF6B4A.toInt(), 0xFFFFE066.toInt(), 3_000L
        )
        File(usbDir, "readme.txt").apply {
            parentFile?.mkdirs()
            writeText("ye photo nahi hai")
        }
        makePhoto(
            File(usbDir, ".hidden/secret.jpg"),
            0xFF111111.toInt(), 0xFF333333.toInt(), 0xFF666666.toInt(), 4_000L
        )

        PhotoScanner.overrideVolumes = listOf(
            PhotoScanner.Volume(tvDir, "TV storage"),
            PhotoScanner.Volume(usbDir, "USB")
        )
    }

    @Test
    fun photoBrowserGrid() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        seedVolumes(app)
        val controller = Robolectric.buildActivity(PhotoBrowserActivity::class.java).setup()
        val decor = controller.get().window.decorView
        val first = awaitTag(decor, "photo_0")
        // .hidden wali photo aur txt file nahi dikhne chahiye: kul 3 tiles.
        check(decor.findViewWithTag<View>("photo_2") != null) { "Teesri photo tile nahi mili" }
        check(decor.findViewWithTag<View>("photo_3") == null) { "Chhupi/txt file bhi dikh gayi" }
        // Thumbnails ke liye thoda intezaar, phir focus wala look.
        settle()
        layout(decor)
        (first as? WallpaperTile)?.applyFocusVisual(true, animate = false)
        layout(decor)
        save(decor, "1_browser_grid.png")
        controller.close()
        PhotoScanner.overrideVolumes = null
    }

    @Test
    fun photoBrowserEmpty() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        val emptyDir = File(app.cacheDir, "fake_volumes/empty_usb").apply { mkdirs() }
        PhotoScanner.overrideVolumes = listOf(PhotoScanner.Volume(emptyDir, "USB"))
        val controller = Robolectric.buildActivity(PhotoBrowserActivity::class.java).setup()
        val decor = controller.get().window.decorView
        awaitTag(decor, "browser_empty")
        val ok = decor.findViewWithTag<View>("empty_ok")
        check(ok != null) { "Khaali haalat ka button nahi mila" }
        settle()
        layout(decor)
        save(decor, "2_browser_empty.png")
        controller.close()
        PhotoScanner.overrideVolumes = null
    }

    @Test
    fun wallpaperScreenRegression() {
        val app = RuntimeEnvironment.getApplication()
        // Ek juda hua custom wallpaper seed karo (v1.2 wala hi jhalk).
        val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, 0f, 540f,
            intArrayOf(0xFF2B1055.toInt(), 0xFF7597DE.toInt(), 0xFFFF9A5A.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 960f, 540f, paint)
        val name = "wp_test_photo.jpg"
        FileOutputStream(Wallpapers.customFile(app, name)).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bmp.recycle()
        TvStore.addCustomWallpaper(app, name)
        TvStore.setWallpaperMode(app, "custom:$name")

        val controller = Robolectric.buildActivity(WallpaperActivity::class.java).setup()
        val decor = controller.get().window.decorView
        layout(decor)
        focusTag(decor, "wp_custom_$name")
        layout(decor)
        save(decor, "3_wallpaper_screen.png")
        controller.close()
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

    /** Background scan ke liye poll karo — asal thread chalte hain (Robolectric). */
    private fun awaitTag(root: View, tag: String): View {
        repeat(160) {
            shadowOf(Looper.getMainLooper()).idle()
            root.findViewWithTag<View>(tag)?.let { return it }
            Thread.sleep(50)
        }
        error("Tag nahi mila: $tag")
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
        val dir = File("../shots-v13")
        dir.mkdirs()
        FileOutputStream(File(dir, name)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }
}
