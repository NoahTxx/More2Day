package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object DateUtils {
    private val keyFormatThreadLocal = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
    private val keyFormat: SimpleDateFormat get() = keyFormatThreadLocal.get()!!

    // Common display formatters cached per-thread to avoid allocations
    private val monthDayEnThreadLocal = ThreadLocal.withInitial { SimpleDateFormat("MMMM d", Locale.US) }
    private val monthDayDeThreadLocal = ThreadLocal.withInitial { SimpleDateFormat("d. MMMM", Locale.GERMAN) }
    private val fullEnThreadLocal = ThreadLocal.withInitial { SimpleDateFormat("EEEE, MMMM d", Locale.US) }
    private val fullDeThreadLocal = ThreadLocal.withInitial { SimpleDateFormat("EEEE, d. MMMM", Locale.GERMAN) }
    private val shortEnThreadLocal = ThreadLocal.withInitial { SimpleDateFormat("EEE, MMM d", Locale.US) }
    private val shortDeThreadLocal = ThreadLocal.withInitial { SimpleDateFormat("EE, d. MMM", Locale.GERMAN) }

    @Volatile
    private var cachedTodayDayMillis: Long = 0L
    @Volatile
    private var cachedTodayKey: String = ""
    @Volatile
    private var cachedZoneIdStr: String = ""

    private val isoDayOfWeekCache = ConcurrentHashMap<String, Int>(64)
    private val displayDateCache = ConcurrentHashMap<String, String>(64)
    private val shortDisplayCache = ConcurrentHashMap<String, String>(64)
    private val daysOfWeekShortCache = ConcurrentHashMap<String, String>(64)
    private val dayNumberCache = ConcurrentHashMap<String, String>(64)
    private val parsedDateCache = ConcurrentHashMap<String, java.util.Date>(256)
    private val daysInWeekCache = ConcurrentHashMap<String, List<String>>(256)

    private val daysEn = arrayOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    private val daysDe = arrayOf("MO", "DI", "MI", "DO", "FR", "SA", "SO")

    fun getTodayKey(): String {
        val now = System.currentTimeMillis()
        val zoneId = java.time.ZoneId.systemDefault()
        if (now - cachedTodayDayMillis in 0..5000L && cachedTodayKey.isNotEmpty() && cachedZoneIdStr == zoneId.id) {
            return cachedTodayKey
        }
        val key = com.example.usage.engine.UsageWindowBounds.getDateKeyForEpochMs(now, zoneId)
        cachedTodayKey = key
        cachedTodayDayMillis = now
        cachedZoneIdStr = zoneId.id
        return key
    }

    fun isToday(dateKey: String): Boolean {
        return dateKey == getTodayKey()
    }

    private fun parseDateSafe(dateKey: String): java.util.Date? {
        parsedDateCache[dateKey]?.let { return it }
        return try {
            val d = keyFormat.parse(dateKey)
            if (d != null) parsedDateCache.putIfAbsent(dateKey, d)
            d
        } catch (e: Exception) {
            null
        }
    }

    fun formatDisplayDate(dateKey: String, language: AppLanguage = AppLanguage.DE): String {
        val isTodayDate = isToday(dateKey)
        val cacheKey = "$dateKey-$language-$isTodayDate"
        displayDateCache[cacheKey]?.let { return it }

        return try {
            val date = parseDateSafe(dateKey) ?: return dateKey
            val isEn = language == AppLanguage.EN
            val result = if (isTodayDate) {
                if (isEn) {
                    "TODAY, " + monthDayEnThreadLocal.get().format(date).uppercase()
                } else {
                    "HEUTE, " + monthDayDeThreadLocal.get().format(date).uppercase()
                }
            } else {
                if (isEn) {
                    fullEnThreadLocal.get().format(date).uppercase()
                } else {
                    fullDeThreadLocal.get().format(date).uppercase()
                }
            }
            displayDateCache[cacheKey] = result
            result
        } catch (e: Exception) {
            dateKey
        }
    }

    fun formatShortDisplay(dateKey: String, language: AppLanguage = AppLanguage.DE): String {
        val cacheKey = "$dateKey-$language"
        shortDisplayCache[cacheKey]?.let { return it }

        return try {
            val date = parseDateSafe(dateKey) ?: return dateKey
            val result = if (language == AppLanguage.EN) shortEnThreadLocal.get().format(date).uppercase()
            else shortDeThreadLocal.get().format(date).uppercase()
            shortDisplayCache[cacheKey] = result
            result
        } catch (e: Exception) {
            dateKey
        }
    }

    fun getDayOfWeek(dateKey: String, language: AppLanguage = AppLanguage.DE): String {
        val iso = getIsoDayOfWeek(dateKey)
        if (iso in 1..7) {
            return if (language == AppLanguage.EN) daysEn[iso - 1] else daysDe[iso - 1]
        }
        return ""
    }

    fun getDayNumber(dateKey: String): String {
        dayNumberCache[dateKey]?.let { return it }
        val result = if (dateKey.length >= 10) {
            val d1 = dateKey[8]
            val d2 = dateKey[9]
            if (d1 == '0') d2.toString() else "${d1}${d2}"
        } else {
            dateKey
        }
        dayNumberCache[dateKey] = result
        return result
    }

    fun getPastDays(daysCount: Int): List<String> {
        val list = ArrayList<String>(daysCount)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(daysCount - 1))
        for (i in 0 until daysCount) {
            list.add(keyFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return list
    }

    fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    fun getIsoDayOfWeek(dateKey: String): Int {
        isoDayOfWeekCache[dateKey]?.let { return it }

        return try {
            val date = parseDateSafe(dateKey) ?: return 1
            val cal = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                time = date
            }
            val day = cal.get(Calendar.DAY_OF_WEEK)
            val res = if (day == Calendar.SUNDAY) 7 else day - 1
            isoDayOfWeekCache[dateKey] = res
            res
        } catch (e: Exception) {
            1
        }
    }

    fun getDaysInSameWeek(dateKey: String): List<String> {
        daysInWeekCache[dateKey]?.let { return it }

        return try {
            val date = parseDateSafe(dateKey) ?: return listOf(dateKey)
            val cal = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                time = date
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            val list = ArrayList<String>(7)
            for (i in 0 until 7) {
                list.add(keyFormat.format(cal.time))
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            val res = list.toList()
            daysInWeekCache[dateKey] = res
            res
        } catch (e: Exception) {
            listOf(dateKey)
        }
    }

    fun formatDaysOfWeekShort(daysOfWeekCsv: String, language: AppLanguage = AppLanguage.DE): String {
        val cacheKey = "$daysOfWeekCsv-$language"
        daysOfWeekShortCache[cacheKey]?.let { return it }

        val days = daysOfWeekCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
        val result = if (days.size == 7) {
            if (language == AppLanguage.EN) "Daily" else "Täglich"
        } else if (days == listOf(1, 2, 3, 4, 5)) {
            if (language == AppLanguage.EN) "Weekdays" else "Werktage"
        } else if (days == listOf(6, 7)) {
            if (language == AppLanguage.EN) "Weekend" else "Wochenende"
        } else {
            val namesEn = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
            val namesDe = mapOf(1 to "Mo", 2 to "Di", 3 to "Mi", 4 to "Do", 5 to "Fr", 6 to "Sa", 7 to "So")
            val nameMap = if (language == AppLanguage.EN) namesEn else namesDe
            days.mapNotNull { nameMap[it] }.joinToString(", ")
        }
        daysOfWeekShortCache[cacheKey] = result
        return result
    }

    fun daysBetween(fromKey: String, toKey: String): Int {
        if (fromKey == toKey) return 0
        return try {
            com.example.usage.engine.UsageWindowBounds.daysBetween(fromKey, toKey)
        } catch (e: Exception) {
            0
        }
    }

    fun addDays(dateKey: String, days: Int): String {
        if (days == 0) return dateKey
        return try {
            com.example.usage.engine.UsageWindowBounds.addDays(dateKey, days)
        } catch (e: Exception) {
            dateKey
        }
    }

    fun getRemainingDaysInWeek(dateKey: String): Int {
        val isoDay = getIsoDayOfWeek(dateKey) // 1..7 (1=Mon, 7=Sun)
        return (7 - isoDay + 1).coerceIn(1, 7)
    }

    fun isIntervalTaskDue(
        intervalDays: Int,
        anchorDateKey: String,
        lastCompletionDateKey: String?,
        queryDateKey: String
    ): Boolean {
        val safeInterval = intervalDays.coerceAtLeast(1)
        if (!lastCompletionDateKey.isNullOrBlank()) {
            val nextDue = addDays(lastCompletionDateKey, safeInterval)
            return queryDateKey >= nextDue
        }
        val anchor = if (anchorDateKey.isNotBlank()) anchorDateKey else queryDateKey
        return queryDateKey >= anchor
    }

    fun getDaysUntilIntervalDue(
        intervalDays: Int,
        anchorDateKey: String,
        lastCompletionDateKey: String?,
        queryDateKey: String
    ): Int {
        val safeInterval = intervalDays.coerceAtLeast(1)
        val nextDue = if (!lastCompletionDateKey.isNullOrBlank()) {
            addDays(lastCompletionDateKey, safeInterval)
        } else {
            if (anchorDateKey.isNotBlank()) anchorDateKey else queryDateKey
        }
        return daysBetween(queryDateKey, nextDue)
    }

    fun getEffectiveWeeklyTarget(
        targetDaysPerWeek: Int,
        createdAtMillis: Long,
        queryDateKey: String
    ): Int {
        if (createdAtMillis <= 0L) return targetDaysPerWeek
        val createdKey = getDateKeyFromMillis(createdAtMillis)
        val queryWeekDays = getDaysInSameWeek(queryDateKey)
        if (queryWeekDays.contains(createdKey)) {
            val createdIsoDay = getIsoDayOfWeek(createdKey)
            val daysAvailable = (7 - createdIsoDay + 1).coerceIn(1, 7)
            return minOf(targetDaysPerWeek, daysAvailable)
        }
        return targetDaysPerWeek
    }

    fun isTaskMandatoryOnDate(
        frequencyType: String,
        daysOfWeek: String,
        targetDaysPerWeek: Int,
        intervalDays: Int,
        intervalAnchorDate: String,
        lastCompletionDateKey: String?,
        dateKey: String,
        weeklyCompletedCount: Int,
        isCompletedOnDate: Boolean,
        createdAtMillis: Long = 0L
    ): Boolean {
        return when (frequencyType) {
            "DAILY" -> true
            "WEEKDAYS" -> {
                val isoDay = getIsoDayOfWeek(dateKey)
                daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.contains(isoDay)
            }
            "INTERVAL" -> {
                isIntervalTaskDue(intervalDays, intervalAnchorDate, lastCompletionDateKey, dateKey)
            }
            "WEEKLY" -> {
                val effectiveTarget = getEffectiveWeeklyTarget(targetDaysPerWeek, createdAtMillis, dateKey)
                if (weeklyCompletedCount >= effectiveTarget) {
                    false
                } else {
                    val remainingDays = getRemainingDaysInWeek(dateKey)
                    val needed = effectiveTarget - weeklyCompletedCount
                    remainingDays <= needed
                }
            }
            else -> true
        }
    }

    fun isTaskDueOnDate(
        frequencyType: String,
        daysOfWeek: String,
        targetDaysPerWeek: Int,
        dateKey: String,
        weeklyCompletedCount: Int = 0,
        isCompletedOnDate: Boolean = false,
        intervalDays: Int = 1,
        intervalAnchorDate: String = "",
        lastCompletionDateKey: String? = null,
        createdAtMillis: Long = 0L
    ): Boolean {
        return when (frequencyType) {
            "WEEKDAYS" -> {
                val isoDay = getIsoDayOfWeek(dateKey)
                daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.contains(isoDay)
            }
            "WEEKLY" -> {
                val effectiveTarget = getEffectiveWeeklyTarget(targetDaysPerWeek, createdAtMillis, dateKey)
                val isGoalMet = weeklyCompletedCount >= effectiveTarget
                val remainingDays = getRemainingDaysInWeek(dateKey)
                val needed = effectiveTarget - weeklyCompletedCount
                val isMandatory = !isGoalMet && needed > 0 && remainingDays <= needed
                isMandatory || isCompletedOnDate
            }
            "INTERVAL" -> {
                isIntervalTaskDue(intervalDays, intervalAnchorDate, lastCompletionDateKey, dateKey) || isCompletedOnDate
            }
            else -> true
        }
    }

    fun getDateKeyFromMillis(millis: Long): String {
        return try {
            keyFormat.format(java.util.Date(millis))
        } catch (e: Exception) {
            getTodayKey()
        }
    }

    fun wasTaskCreatedOnOrBefore(createdAtMillis: Long, dateKey: String): Boolean {
        if (createdAtMillis <= 0L) return true
        val createdKey = getDateKeyFromMillis(createdAtMillis)
        if (createdKey.isBlank()) return true
        return createdKey <= dateKey
    }
}
