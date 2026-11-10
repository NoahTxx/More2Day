package com.example.util

import android.content.Context
import com.example.data.DailyDatabase
import com.example.usage.engine.UsageWindowBounds
import com.example.usage.model.MeasurementStatus
import com.example.usage.model.UsageDiagnosticsReport
import com.example.usage.repository.UsageTrackingRepository
import com.example.usage.source.AndroidUsageEventSource
import java.time.ZoneId

/**
 * Result of screen time evaluation for a target window `[startTime, endTime)`.
 *
 * @param totalScreenTimeMillis Primary metric: sum of qualified foreground usage across trackable apps (in ms).
 * @param appUsageMap Per-package qualified foreground duration in ms (unrounded).
 * @param unlockedDeviceTimeMillis Reconstructed unlocked interactive device duration in ms (`SCREEN_INTERACTIVE && !KEYGUARD_SHOWN`).
 * @param interactiveDeviceTimeMillis Reconstructed interactive device duration in ms (including lockscreen interaction).
 * @param hourlyAppForegroundMs Distribution of qualified app foreground time across local hours `0..23`.
 * @param status Explicit measurement status (`VALID_USAGE`, `VALID_ZERO`, `PERMISSION_MISSING`, `TEMPORARILY_UNAVAILABLE`, `HISTORY_INCOMPLETE`).
 * @param diagnostics Optional diagnostic report containing raw event counts, reconstructed intervals, and exclusions.
 */
data class ScreenTimeResult(
    val totalScreenTimeMillis: Long,
    val appUsageMap: Map<String, Long>,
    val unlockedDeviceTimeMillis: Long = totalScreenTimeMillis,
    val interactiveDeviceTimeMillis: Long = unlockedDeviceTimeMillis,
    val hourlyAppForegroundMs: Map<Int, Long> = emptyMap(),
    val status: MeasurementStatus = if (totalScreenTimeMillis > 0L) MeasurementStatus.VALID_USAGE else MeasurementStatus.VALID_ZERO,
    val diagnostics: UsageDiagnosticsReport? = null
)

object ScreenTimeCalculator {

    fun getTodayStartMillis(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        return UsageWindowBounds.getTodayWindowUntilNow(nowMs, zoneId).startEpochMs
    }

    fun getWeekStartMillis(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        return UsageWindowBounds.getRollingWeekWindowUntilNow(nowMs, zoneId).startEpochMs
    }

    /**
     * Evaluates screen time in the half-open interval `[startTime, endTime)` using deterministic
     * event reconstruction (`UsageStatsManager.queryEvents`) and idempotent Room persistence.
     */
    fun getScreenTimeData(
        context: Context,
        startTime: Long,
        endTime: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ScreenTimeResult {
        if (!PermissionUtils.hasUsageStatsPermission(context)) {
            return ScreenTimeResult(
                totalScreenTimeMillis = 0L,
                appUsageMap = emptyMap(),
                unlockedDeviceTimeMillis = 0L,
                interactiveDeviceTimeMillis = 0L,
                hourlyAppForegroundMs = emptyMap(),
                status = MeasurementStatus.PERMISSION_MISSING,
                diagnostics = null
            )
        }

        val db = DailyDatabase.getInstance(context)
        val repository = UsageTrackingRepository(
            usageTrackingDao = db.usageTrackingDao(),
            eventSource = AndroidUsageEventSource(context.applicationContext)
        )

        val reconstruction = repository.evaluateAndSyncWindow(
            windowStartMs = startTime,
            windowEndMs = endTime,
            zoneId = zoneId,
            lookbackMs = UsageWindowBounds.DEFAULT_LOOKBACK_MS,
            projectOngoingAtWindowEnd = true
        )

        val maxPossible = (endTime - startTime).coerceAtLeast(0L)
        val clampedAppUsage = reconstruction.appForegroundDurationMap.mapValues { (_, dur) ->
            dur.coerceIn(0L, maxPossible)
        }
        val totalAppForeground = clampedAppUsage.values.sum().coerceIn(0L, maxPossible)
        val unlockedDevice = reconstruction.unlockedDeviceDurationMs.coerceIn(0L, maxPossible)
        val interactiveDevice = reconstruction.interactiveDeviceDurationMs.coerceIn(0L, maxPossible)

        return ScreenTimeResult(
            totalScreenTimeMillis = totalAppForeground,
            appUsageMap = clampedAppUsage,
            unlockedDeviceTimeMillis = unlockedDevice,
            interactiveDeviceTimeMillis = interactiveDevice,
            hourlyAppForegroundMs = reconstruction.hourlyAppForegroundMs,
            status = reconstruction.status,
            diagnostics = reconstruction.diagnostics
        )
    }

    /**
     * Formats a duration in milliseconds for UI display only (never used for intermediate aggregation).
     * Rounds to the nearest minute for durations >= 60s, and shows `< 1 Min.` for 1..59_999 ms.
     */
    fun formatDurationForDisplay(millis: Long): String {
        if (millis <= 0L) return "0 Min."
        if (millis < 60_000L) return "< 1 Min."
        val roundedMinutes = ((millis + 30_000L) / 60_000L)
        val hours = roundedMinutes / 60L
        val remMin = roundedMinutes % 60L
        return when {
            hours > 0L && remMin > 0L -> "$hours Std. $remMin Min."
            hours > 0L -> "$hours Std. 0 Min."
            else -> "$remMin Min."
        }
    }
}
