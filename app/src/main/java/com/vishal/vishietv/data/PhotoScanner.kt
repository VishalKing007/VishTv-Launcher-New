package com.vishal.vishietv.data

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

/**
 * TV aur USB me photo dhoondhne wala — BRAVIA jaise TVon me system file
 * picker (DocumentsUI) hota hi nahi, isliye Vishie TV apne andar hi
 * storage ghumkar photos la deta hai. Koi hidden API nahi: har volume
 * ki jad (root) getExternalFilesDirs wale jaane-maane raste se milti
 * hai, naam StorageManager deta hai.
 */
object PhotoScanner {

    data class Volume(val root: File, val label: String)

    data class Photo(val file: File, val volumeRoot: File, val volumeLabel: String)

    /** Test ke liye nakli volumes (AppRepository.overrideApps ki tarah). */
    var overrideVolumes: List<Volume>? = null

    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp")
    private const val MAX_DEPTH = 6
    private const val MAX_RESULTS = 600

    /** Saare storage volumes — andar wala storage + lagi hui USB drives. */
    fun volumes(context: Context): List<Volume> {
        overrideVolumes?.let { return it }
        val roots = LinkedHashMap<String, Volume>()
        fun add(root: File?, fallbackLabel: String) {
            if (root == null) return
            val canon = try {
                root.canonicalFile
            } catch (e: Exception) {
                root
            }
            if (roots.containsKey(canon.path)) return
            roots[canon.path] = Volume(canon, labelFor(context, canon) ?: fallbackLabel)
        }

        // TV ka apna storage.
        add(Environment.getExternalStorageDirectory(), "TV storage")

        // Har volume par app ka folder hota hai:
        // <root>/Android/data/<pkg>/files — wahan se <root> pehchan lo.
        // USB drive bhi isi se mil jati hai.
        try {
            context.getExternalFilesDirs(null)?.forEach { dir ->
                if (dir == null) return@forEach
                var f: File? = dir
                var steps = 0
                while (f != null && f.name != "Android" && steps < 8) {
                    f = f.parentFile
                    steps++
                }
                add(f?.parentFile, "USB")
            }
        } catch (e: Exception) {
            // Koi volume ajeeb ho to baaki to mil hi jayenge.
        }

        // Teesra rasta: /storage ke neeche dikhai dene wali asal drives.
        try {
            File("/storage").listFiles()?.forEach { child ->
                if (child.isDirectory && child.name != "emulated" &&
                    child.name != "self" && child.name != "runtime"
                ) {
                    add(child, "USB")
                }
            }
        } catch (e: Exception) {
            // Upar wale raste kaafi hain.
        }
        return roots.values.toList()
    }

    /** Volume ka manushya-naam — jaise "USB DRIVE" ya "Internal shared storage". */
    private fun labelFor(context: Context, root: File): String? {
        return try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                ?: return null
            sm.getStorageVolume(root)?.getDescription(context)?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            null
        }
    }

    /** Saari photos, sabse nayi pehle. Background thread par chalao. */
    fun scan(context: Context): List<Photo> {
        val out = ArrayList<Photo>()
        for (vol in volumes(context)) {
            if (!vol.root.canRead()) continue
            walk(vol.root, vol, 0, out)
            if (out.size >= MAX_RESULTS) break
        }
        return out.sortedByDescending { it.file.lastModified() }.take(MAX_RESULTS)
    }

    private fun walk(dir: File, vol: Volume, depth: Int, out: ArrayList<Photo>) {
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return
        for (child in children) {
            if (out.size >= MAX_RESULTS) return
            if (child.isDirectory) {
                // Chhupi hui folders aur Android/data ke andar nahi ghusna.
                if (child.name.startsWith(".")) continue
                if (child.name == "data" && dir.name == "Android") continue
                if (depth < MAX_DEPTH) walk(child, vol, depth + 1, out)
            } else if (child.isFile && !child.name.startsWith(".")) {
                val ext = child.extension.lowercase()
                if (ext in EXTENSIONS) {
                    out.add(Photo(child, vol.root, vol.label))
                }
            }
        }
    }
}
