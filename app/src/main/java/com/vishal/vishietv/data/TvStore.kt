package com.vishal.vishietv.data

import android.content.Context

/**
 * Vishie TV ki saari local pasand — SharedPreferences me, bilkul offline.
 * Pasandida apps, chhupaye hue apps, haal hi me khule apps, aur theme.
 */
object TvStore {

    private const val PREFS = "vishie_tv_store"
    private const val KEY_FAVS = "fav_pkgs"
    private const val KEY_SEEDED = "favs_seeded"
    private const val KEY_HIDDEN = "hidden_entries" // "pkg|label" entries
    private const val KEY_RECENTS = "recent_pkgs"   // "|" se juda kram
    private const val KEY_THEME = "theme_id"
    private const val KEY_WALLPAPER = "wallpaper_mode" // "theme" | "preset:<i>" | "custom:<file>"
    private const val KEY_CUSTOM_WPS = "custom_wallpapers" // "|" se juda kram, file ke naam

    /** Khud ke jode hue wallpaper — isse zyada nahi, TV halka rahe. */
    const val MAX_CUSTOM_WALLPAPERS = 12

    /** Pehli baar ke liye mashhoor streaming apps, agar TV par hon to. */
    private val SEED_PRIORITY = listOf(
        "com.google.android.youtube.tv",
        "com.google.android.youtube",
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.hotstar.android",
        "com.sonyliv",
        "com.spotify.tv.android",
        "com.spotify.music"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Pasandida ----------

    fun favourites(context: Context, allApps: List<TvApp>): List<TvApp> {
        val p = prefs(context)
        if (!p.getBoolean(KEY_SEEDED, false)) {
            val byPkg = allApps.associateBy { it.packageName }
            val seeded = SEED_PRIORITY.mapNotNull { byPkg[it] }
                .ifEmpty { allApps.take(4) }
                .map { it.packageName }
            p.edit()
                .putStringSet(KEY_FAVS, seeded.toSet())
                .putBoolean(KEY_SEEDED, true)
                .apply()
        }
        val saved = p.getStringSet(KEY_FAVS, emptySet()) ?: emptySet()
        val byPkg = allApps.associateBy { it.packageName }
        val seededOrder = SEED_PRIORITY.filter { it in saved }
        val rest = allApps.filter { it.packageName in saved && it.packageName !in seededOrder }
            .map { it.packageName }
        return (seededOrder + rest).mapNotNull { byPkg[it] }
    }

    fun isFavourite(context: Context, packageName: String): Boolean =
        prefs(context).getStringSet(KEY_FAVS, emptySet())?.contains(packageName) == true

    fun toggleFavourite(context: Context, packageName: String) {
        val p = prefs(context)
        val current = (p.getStringSet(KEY_FAVS, emptySet()) ?: emptySet()).toMutableSet()
        if (!current.add(packageName)) current.remove(packageName)
        p.edit().putStringSet(KEY_FAVS, current).apply()
    }

    // ---------- Chhupaye apps ----------

    /** pkg -> label, taaki bina load kiye bhi unhide list dikh sake. */
    fun hiddenApps(context: Context): Map<String, String> {
        val raw = prefs(context).getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
        return raw.mapNotNull { entry ->
            val i = entry.indexOf('|')
            if (i <= 0) null else entry.substring(0, i) to entry.substring(i + 1)
        }.toMap()
    }

    fun hide(context: Context, app: TvApp) {
        val p = prefs(context)
        val current = (p.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()).toMutableSet()
        current.removeAll { it.startsWith(app.packageName + "|") }
        current.add(app.packageName + "|" + app.label)
        p.edit().putStringSet(KEY_HIDDEN, current).apply()
    }

    fun unhide(context: Context, packageName: String) {
        val p = prefs(context)
        val current = (p.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()).toMutableSet()
        current.removeAll { it == packageName || it.startsWith("$packageName|") }
        p.edit().putStringSet(KEY_HIDDEN, current).apply()
    }

    // ---------- Haal hi me khule ----------

    fun recordLaunch(context: Context, packageName: String) {
        val p = prefs(context)
        val list = recentPackages(context).toMutableList()
        list.remove(packageName)
        list.add(0, packageName)
        p.edit().putString(KEY_RECENTS, list.take(8).joinToString("|")).apply()
    }

    fun recentPackages(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_RECENTS, "") ?: ""
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun recentApps(context: Context, allApps: List<TvApp>): List<TvApp> {
        val byPkg = allApps.associateBy { it.packageName }
        return recentPackages(context).mapNotNull { byPkg[it] }
    }

    // ---------- Theme ----------

    fun theme(context: Context): Int = prefs(context).getInt(KEY_THEME, 0)

    fun setTheme(context: Context, id: Int) {
        prefs(context).edit().putInt(KEY_THEME, id).apply()
    }

    // ---------- Wallpaper ----------

    /** Kaunsa background laga hai: "theme", "preset:<index>" ya "custom:<fileName>". */
    fun wallpaperMode(context: Context): String =
        prefs(context).getString(KEY_WALLPAPER, "theme") ?: "theme"

    fun setWallpaperMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_WALLPAPER, mode).apply()
    }

    /** Khud ke jode hue wallpaperon ke file-naam, jude hue kram me. */
    fun customWallpapers(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_CUSTOM_WPS, "") ?: ""
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun addCustomWallpaper(context: Context, fileName: String) {
        val list = customWallpapers(context).toMutableList()
        list.remove(fileName)
        list.add(fileName)
        prefs(context).edit()
            .putString(KEY_CUSTOM_WPS, list.take(MAX_CUSTOM_WALLPAPERS).joinToString("|"))
            .apply()
    }

    fun removeCustomWallpaper(context: Context, fileName: String) {
        val list = customWallpapers(context).toMutableList()
        list.remove(fileName)
        prefs(context).edit().putString(KEY_CUSTOM_WPS, list.joinToString("|")).apply()
        // Jo wallpaper laga hua tha wahi hat gaya — theme look par wapas.
        if (wallpaperMode(context) == "custom:$fileName") {
            setWallpaperMode(context, "theme")
        }
    }
}
