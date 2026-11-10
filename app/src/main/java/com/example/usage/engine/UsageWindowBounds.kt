package com.example.usage.engine

import com.example.usage.model.TimeInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Pure time-window and calendar calculation helper using [LocalDate] and [ZoneId].
 *
 * Rules:
 * - Intervals are always half-open `[startEpochMs, endEpochMs)`.
 * - Calendar days are never assumed to be a fixed 24 hours (handles 23h spring-forward and 25h fall-back DST transitions).
 * - Timezone rule: Evaluations use the explicitly supplied [ZoneId] (defaulting to [ZoneId.systemDefault] at call site),
 *   and persist the zoneId string alongside reconstructed intervals and checkpoints.
 */
object UsageWindowBounds {

    private val ISO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Default lookback duration (6 hours) before windowStartMs to establish initial screen/keyguard/activity state. */
    const val DEFAULT_LOOKBACK_MS: Long = 6L * 60L * 60L * 1000L

    fun parseDateKey(dateKey: String): LocalDate {
        return LocalDate.parse(dateKey, ISO_DATE_FORMATTER)
    }

    fun formatDateKey(localDate: LocalDate): String {
        return localDate.format(ISO_DATE_FORMATTER)
    }

    fun getDateKeyForEpochMs(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val localDate = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
        return formatDateKey(localDate)
    }

    /**
     * Returns the exact half-open interval `[startOfDayMs, startOfNextDayMs)` for the given [localDate] in [zoneId].
     * On DST spring-forward days this is 23 hours; on fall-back days this is 25 hours.
     */
    fun getDayWindow(localDate: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): TimeInterval {
        val startMs = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMs = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return TimeInterval(startEpochMs = startMs, endEpochMs = endMs)
    }

    fun getDayWindow(dateKey: String, zoneId: ZoneId = ZoneId.systemDefault()): TimeInterval {
        return getDayWindow(parseDateKey(dateKey), zoneId)
    }

    /**
     * Returns `[startOfTodayMs, nowMs)` for the current day in [zoneId].
     */
    fun getTodayWindowUntilNow(nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): TimeInterval {
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val startMs = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val safeEnd = maxOf(startMs, nowMs)
        return TimeInterval(startEpochMs = startMs, endEpochMs = safeEnd)
    }

    /**
     * Returns the 7-day rolling window `[startOfDay(today - 6 days), nowMs)` in [zoneId].
     */
    fun getRollingWeekWindowUntilNow(nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): TimeInterval {
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val startDay = today.minusDays(6)
        val startMs = startDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val safeEnd = maxOf(startMs, nowMs)
        return TimeInterval(startEpochMs = startMs, endEpochMs = safeEnd)
    }

    /**
     * Exact calendar day difference between two `yyyy-MM-dd` keys, immune to 23h/25h DST shifts.
     */
    fun daysBetween(fromDateKey: String, toDateKey: String): Int {
        if (fromDateKey == toDateKey) return 0
        val d1 = parseDateKey(fromDateKey)
        val d2 = parseDateKey(toDateKey)
        return ChronoUnit.DAYS.between(d1, d2).toInt()
    }

    /**
     * Adds [days] calendar days to `yyyy-MM-dd` dateKey using [LocalDate].
     */
    fun addDays(dateKey: String, days: Int): String {
        if (days == 0) return dateKey
        return formatDateKey(parseDateKey(dateKey).plusDays(days.toLong()))
    }

    /**
     * Splits a half-open interval `[startEpochMs, endEpochMs)` across local hour buckets (0..23) in [zoneId].
     * Accumulates milliseconds into [targetHourlyMap].
     */
    fun accumulateHourlyDistribution(
        startEpochMs: Long,
        endEpochMs: Long,
        zoneId: ZoneId,
        targetHourlyMap: MutableMap<Int, Long>
    ) {
        if (endEpochMs <= startEpochMs) return
        var cursorMs = startEpochMs
        while (cursorMs < endEpochMs) {
            val zdt = Instant.ofEpochMilli(cursorMs).atZone(zoneId)
            val hour = zdt.hour
            val nextHourStartInstant = zdt.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()
            val nextBoundaryMs = minOf(endEpochMs, nextHourStartInstant.toEpochMilli())
            val sliceMs = (nextBoundaryMs - cursorMs).coerceAtLeast(0L)
            if (sliceMs > 0L) {
                targetHourlyMap[hour] = (targetHourlyMap[hour] ?: 0L) + sliceMs
            }
            cursorMs = if (nextBoundaryMs > cursorMs) nextBoundaryMs else cursorMs + 1L
        }
    }
}
