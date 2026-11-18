package io.github.hyperboost.gamespace

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object SystemStats {
    private const val MB = 1024L * 1024L

    private fun memInfo(ctx: Context): ActivityManager.MemoryInfo {
        val am = ctx.getSystemService(ActivityManager::class.java)
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    }

    fun availMb(ctx: Context): Long = memInfo(ctx).availMem / MB
    fun totalMb(ctx: Context): Long = memInfo(ctx).totalMem / MB

    /** Suhu baterai (°C). NaN jika tidak tersedia. */
    fun batteryTempC(ctx: Context): Float {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val t = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (t < 0) Float.NaN else t / 10f
    }
}
