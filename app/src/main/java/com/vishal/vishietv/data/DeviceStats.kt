package com.vishal.vishietv.data

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs

/** TV ki asli haalat — RAM aur storage, bina kisi andaze ke. */
object DeviceStats {

    data class Stats(
        val totalRamMb: Long,
        val freeRamMb: Long,
        val totalStorageGb: Double,
        val freeStorageGb: Double
    )

    fun read(context: Context): Stats {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val totalRam = mem.totalMem / (1024 * 1024)
        val freeRam = mem.availMem / (1024 * 1024)

        val fs = StatFs(Environment.getDataDirectory().path)
        val totalStorage = fs.totalBytes / 1_000_000_000.0
        val freeStorage = fs.availableBytes / 1_000_000_000.0
        return Stats(totalRam, freeRam, totalStorage, freeStorage)
    }
}
