package com.vishal.vishietv.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** Ek TV app ka halka model — sirf utna data jitna ek tile ko chahiye. */
data class TvApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable?
)

/**
 * TV par maujood launchable apps ki list. Pehle LEANBACK (TV) apps, phir
 * aam LAUNCHER apps, package ke hisaab se dedupe karke.
 */
object AppRepository {

    /** Sirf screenshot tests ke liye hook; asal app me hamesha null rehta hai. */
    @Volatile
    var overrideApps: List<TvApp>? = null

    /**
     * Kuch system apps pehle se TV me hote hain par kaam ke nahi hote —
     * Vishal ne kaha wo na dikhen. Par bade streaming apps aksar factory se
     * system banke aate hain, isliye ye rakhne-wali list alag hai: system
     * app sirf tabhi dikhega jab is list me ho. Khud install kiye apps
     * hamesha dikhenge. Haath se chhupaye apps (TvStore) kabhi nahi dikhenge.
     */
    val KEEP_SYSTEM = setOf(
        "com.google.android.youtube.tv",
        "com.google.android.youtube",
        "com.netflix.ninja",
        "com.netflix.mediaclient",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.hotstar.android",
        "com.sonyliv",
        "com.spotify.tv.android",
        "com.spotify.music",
        "com.graymatrix.did",
        "com.jio.media.ondemand",
        "com.android.vending"
    )

    fun loadApps(context: Context): List<TvApp> {
        overrideApps?.let { return it }
        val pm = context.packageManager
        val hidden = TvStore.hiddenApps(context).keys
        val found = LinkedHashMap<String, TvApp>()
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (category in categories) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val results = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }
            for (info in results) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName || found.containsKey(pkg)) continue
                if (pkg in hidden) continue
                val flags = info.activityInfo.applicationInfo?.flags ?: 0
                val isSystem = flags and (
                    android.content.pm.ApplicationInfo.FLAG_SYSTEM or
                        android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                    ) != 0
                if (isSystem && pkg !in KEEP_SYSTEM) continue
                val label = runCatching { info.loadLabel(pm).toString() }
                    .getOrDefault(pkg.substringAfterLast('.'))
                if (label.isBlank()) continue
                val icon = runCatching { info.loadIcon(pm) }.getOrNull()
                found[pkg] = TvApp(pkg, info.activityInfo.name, label, icon)
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    fun launchIntent(pm: PackageManager, app: TvApp): Intent? {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Agar leanback entry point na ho to aam launcher wala try karo.
        if (pm.resolveActivity(intent, 0) == null) {
            intent.removeCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return if (pm.resolveActivity(intent, 0) != null) intent else null
    }
}
