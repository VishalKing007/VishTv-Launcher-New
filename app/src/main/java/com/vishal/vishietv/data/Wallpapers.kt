package com.vishal.vishietv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Wallpaper ka saara intezam: 4 ready-made look (code se bane hue, koi
 * file nahi chahiye) aur Vishal ke khud ke jode hue photo — app ki
 * private jagah me chhoti karke rakhe hue, taaki USB hata bhi do to
 * wallpaper bana rahe.
 */
object Wallpapers {

    data class Preset(
        val naam: String,
        val base: IntArray,
        val glowA: Int,
        val glowB: Int
    )

    val PRESETS = listOf(
        Preset(
            "Midnight Aurora",
            intArrayOf(0xFF050914.toInt(), 0xFF0B1E33.toInt(), 0xFF04060C.toInt()),
            0xFF0E9F8A.toInt(), 0xFF2563EB.toInt()
        ),
        Preset(
            "Sunset Dunes",
            intArrayOf(0xFF140806.toInt(), 0xFF33150C.toInt(), 0xFF0D0505.toInt()),
            0xFFF59E0B.toInt(), 0xFFDC2626.toInt()
        ),
        Preset(
            "Deep Ocean",
            intArrayOf(0xFF020A14.toInt(), 0xFF06283F.toInt(), 0xFF01060C.toInt()),
            0xFF0EA5E9.toInt(), 0xFF0D9488.toInt()
        ),
        Preset(
            "Carbon",
            intArrayOf(0xFF0B0B0D.toInt(), 0xFF17181C.toInt(), 0xFF070708.toInt()),
            0xFF4B5563.toInt(), 0xFF1F6FEB.toInt()
        )
    )

    // ---------- Ready-made looks ----------

    fun presetBitmap(preset: Preset, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, w * 0.3f, h.toFloat(),
            preset.base, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        // Do badi halki roshniyan — theme background jaisi hi bhasha.
        paint.shader = RadialGradient(
            w * 0.82f, -h * 0.10f, w * 0.58f,
            withAlpha(preset.glowA, 120), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = RadialGradient(
            w * 0.08f, h * 1.05f, w * 0.52f,
            withAlpha(preset.glowB, 95), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        // Ek aur beech-wali naram chamak, taaki beech khaali na lage.
        paint.shader = RadialGradient(
            w * 0.55f, h * 0.42f, w * 0.34f,
            withAlpha(preset.glowA, 42), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    // ---------- Khud ke photo ----------

    fun customDir(context: Context): File =
        File(context.filesDir, "wallpapers").apply { mkdirs() }

    fun customFile(context: Context, fileName: String): File =
        File(customDir(context), fileName)

    /**
     * Picker se chuni photo ko app ki private jagah me copy karke
     * zyada se zyada 1920x1080 tak chhota karke rakhta hai.
     * Safal ho to file ka naam, na ho to null.
     */
    fun importPhoto(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) > 1920 || bounds.outHeight / (sample * 2) > 1080) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val name = storeBitmap(context, decoded)
        decoded.recycle()
        return name
    }

    /**
     * Photo browser se chuni file ko jodna — picker wala hi raasta,
     * bas source Uri ki jagah seedhi file hai.
     */
    fun importPhotoFile(context: Context, source: File): String? {
        if (!source.isFile || !source.canRead()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) > 1920 || bounds.outHeight / (sample * 2) > 1080) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = try {
            BitmapFactory.decodeFile(source.path, opts)
        } catch (e: Exception) {
            null
        } ?: return null

        val name = storeBitmap(context, decoded)
        decoded.recycle()
        return name
    }

    /** Decode hui tasveer ko chhota karke private jagah me JPEG rakh deta hai. */
    private fun storeBitmap(context: Context, decoded: Bitmap): String? {
        val scaled = scaleDownTo(decoded, 1920, 1080)
        val name = "wp_${System.currentTimeMillis()}.jpg"
        return try {
            FileOutputStream(customFile(context, name)).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (scaled !== decoded) scaled.recycle()
            name
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleDownTo(src: Bitmap, maxW: Int, maxH: Int): Bitmap {
        if (src.width <= maxW && src.height <= maxH) return src
        val ratio = minOf(maxW.toFloat() / src.width, maxH.toFloat() / src.height)
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    /** File se bitmap — thumbnail ke liye chhota sample, background ke liye poora. */
    fun loadBitmap(context: Context, fileName: String, reqW: Int, reqH: Int): Bitmap? {
        val file = customFile(context, fileName)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return try {
            BitmapFactory.decodeFile(file.path, opts)
        } catch (e: Exception) {
            null
        }
    }

    fun delete(context: Context, fileName: String): Boolean =
        try {
            customFile(context, fileName).delete()
        } catch (e: Exception) {
            false
        }

    // ---------- Home par lagana ----------

    /** Wallpaper mode ke hisaab se asal tasveer; "theme" mode par null. */
    fun resolveBitmap(context: Context, mode: String, w: Int, h: Int): Bitmap? {
        return when {
            mode.startsWith("preset:") -> {
                val index = mode.removePrefix("preset:").toIntOrNull()
                val preset = index?.let { PRESETS.getOrNull(it) } ?: return null
                presetBitmap(preset, w, h)
            }
            mode.startsWith("custom:") -> {
                val name = mode.removePrefix("custom:")
                loadBitmap(context, name, w, h)
            }
            else -> null
        }
    }

    /**
     * Thumbnail ke liye: tasveer ko tile ke size me center-crop karke
     * gol kinaron ke saath kaat deta hai — kinare paar-darshi rehte
     * hain, isliye har renderer par card gol hi dikhta hai.
     */
    fun roundedCover(src: Bitmap, w: Int, h: Int, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val path = Path().apply {
            addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(path)
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = (src.width * scale).toInt()
        val dh = (src.height * scale).toInt()
        val left = (w - dw) / 2
        val top = (h - dh) / 2
        val paint = Paint().apply { isFilterBitmap = true }
        canvas.drawBitmap(src, null, Rect(left, top, left + dw, top + dh), paint)
        return out
    }

    /**
     * Tasveer ko poori screen par center-crop karke bichhata hai aur
     * upar halka kaala pardah daalta hai — tiles aur text saaf padhe
     * jayein. Neeche ki taraf pardah thoda aur gehra, jahan labels hain.
     */
    fun cover(src: Bitmap, w: Int, h: Int, scrimAlpha: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = (src.width * scale).toInt()
        val dh = (src.height * scale).toInt()
        val left = (w - dw) / 2
        val top = (h - dh) / 2
        val paint = Paint().apply { isFilterBitmap = true }
        canvas.drawBitmap(src, null, Rect(left, top, left + dw, top + dh), paint)
        canvas.drawColor(Color.argb(scrimAlpha, 0, 0, 0))
        val shade = Paint()
        shade.shader = LinearGradient(
            0f, h * 0.45f, 0f, h.toFloat(),
            Color.TRANSPARENT, Color.argb(110, 0, 0, 0), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shade)
        return out
    }
}
