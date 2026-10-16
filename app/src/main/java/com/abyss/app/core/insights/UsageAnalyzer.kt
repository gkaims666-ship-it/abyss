package com.abyss.app.core.insights

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

/**
 * Looks at how the device has been used to estimate wake/sleep times and to
 * decide whether the user went to bed late. Feeds the smart-alarm rules.
 *
 * Requires "Usage access" (PACKAGE_USAGE_STATS), a special permission the user
 * grants from the Permissions hub. Without it, methods return nulls and the
 * smart rules fall back to their manual baselines.
 */
class UsageAnalyzer(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Timestamp (ms) of the last time the screen was interacted with before [before]. */
    fun lastInteractionBefore(before: Long): Long? {
        if (!hasUsageAccess()) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = before - 24 * 60 * 60 * 1000L
        val events = usm.queryEvents(start, before)
        val e = UsageEvents.Event()
        var last = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (e.timeStamp > last) last = e.timeStamp
            }
        }
        return last.takeIf { it > 0 }
    }

    /** First interaction after [after] on the same day — an estimate of wake time. */
    fun firstInteractionAfter(after: Long, windowHours: Int = 8): Long? {
        if (!hasUsageAccess()) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = after + windowHours * 60 * 60 * 1000L
        val events = usm.queryEvents(after, end)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                return e.timeStamp
            }
        }
        return null
    }

    /**
     * True when the user was still active past [lateHour] (local) last night —
     * the signal the smart alarm uses to raise volume.
     */
    fun wentToBedLate(now: Long = System.currentTimeMillis(), lateHour: Int = 1): Boolean {
        val last = lastInteractionBefore(now) ?: return false
        val cal = Calendar.getInstance().apply { timeInMillis = last }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        // Late = active between lateHour (e.g. 01:00) and ~5am, i.e. deep night.
        return h in lateHour..4
    }

    /** Human-readable estimate of last night's bedtime, or null. */
    fun estimatedBedtime(now: Long = System.currentTimeMillis()): Long? = lastInteractionBefore(now)
}
