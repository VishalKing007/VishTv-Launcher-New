package com.vishal.vishietv.boost

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import com.vishal.vishietv.data.AppRepository
import com.vishal.vishietv.data.DeviceStats

/**
 * One-click RAM saaf. Android ki ijazat ke andar rehkar:
 *  1) jo processes/services abhi chal rahe hain, unhe ikattha karta hai,
 *  2) install kiye hue saare apps bhi list me jodta hai,
 *  3) har killable third-party package par killBackgroundProcesses()
 *     chalata hai (KILL_BACKGROUND_PROCESSES permission).
 *
 * Nahi chhedta: khud Vishie TV ko, system apps ko, aur AppRepository
 * ki rakhne-wali list (bade streaming apps, Play Store) ko.
 *
 * Sachchai: "recents" (haal ke apps) ki list Android khud sambhalta
 * hai. Bina system/signature permission ke kisi doosre app ki recent
 * entry hatane ka koi jaayaz API API 26 par hai hi nahi
 * (ActivityManager.removeTask signature permission maangta hai, aur
 * AppTask.finishAndRemoveTask sirf apne tasks par chalta hai) — isliye
 * hum jhootha dawa nahi karte. "Band" ka matlab yahan asal wala hai:
 * background process khatam + RAM sach me khaali. Kuch system services
 * ko Android khud dobara chalu kar sakta hai; pehle/baad ke number
 * hamesha asli maape hue hain, kabhi badha-chadha kar nahi.
 */
object BoostManager {

    data class Result(
        val appsStopped: Int,
        val freeRamBeforeMb: Long,
        val freeRamAfterMb: Long
    )

    fun boost(context: Context): Result {
        val before = DeviceStats.read(context).freeRamMb
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = context.packageManager

        val targets = LinkedHashSet<String>()

        // Abhi chal rahi cheezein — jitna platform bina extra permission
        // ke dikhata hai (API 26 par aam app ko zyada tar apne hi dikhte
        // hain, isliye neeche wali installed list hi asal bharosa hai).
        try {
            am.runningAppProcesses?.forEach { proc ->
                proc.pkgList?.forEach { targets.add(it) }
            }
        } catch (e: Exception) {
            // Koi baat nahi — installed list se kaam chalega.
        }
        try {
            @Suppress("DEPRECATION")
            am.getRunningServices(200)?.forEach { svc ->
                targets.add(svc.service.packageName)
            }
        } catch (e: Exception) {
            // Upar ki tarah — aage badho.
        }

        // Install kiye hue saare apps.
        val installed = try {
            pm.getInstalledApplications(0)
        } catch (e: Exception) {
            emptyList()
        }
        for (app in installed) targets.add(app.packageName)

        var stopped = 0
        for (pkg in targets) {
            if (pkg == context.packageName) continue
            if (pkg in AppRepository.KEEP_SYSTEM) continue
            val info = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (e: Exception) {
                null
            }
            if (info != null &&
                info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            ) {
                continue
            }
            try {
                am.killBackgroundProcesses(pkg)
                stopped++
            } catch (e: Exception) {
                // Kuch apps band nahi hote — chup-chaap aage badho.
            }
        }

        // Band hue process ka RAM wapas aane me ek pal lagta hai.
        try {
            Thread.sleep(600)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        val after = DeviceStats.read(context).freeRamMb
        return Result(stopped, before, after)
    }
}
