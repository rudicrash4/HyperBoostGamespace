package io.github.hyperboost.gamespace

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

object UsageHelper {

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Paket yang terakhir di-resume dalam 10 menit terakhir, atau null jika tidak ada event. */
    fun foregroundPackage(ctx: Context): String? {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 10 * 60_000L, end)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
        }
        return last
    }
}
