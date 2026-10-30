package com.example.ui

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DailyApplication
import com.example.data.DailyRepository
import com.example.data.DailyTask
import com.example.data.TaskCompletion
import com.example.data.TaskWithCompletion
import com.example.reminder.ReminderManager
import com.example.util.AppLanguage
import com.example.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Immutable
data class StreakStats(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalDays: Int = 0,
    val isTodaySecured: Boolean = false
)

@Immutable
data class HabitDayStatus(
    val dateKey: String,
    val dayLabel: String,
    val dayNumber: String,
    val isCompleted: Boolean,
    val isToday: Boolean,
    val isDue: Boolean
)

@Immutable
data class HabitStats(
    val task: DailyTask,
    val currentStreak: Int,
    val longestStreak: Int,
    val totalCompletions: Int,
    val totalCountSum: Long,
    val monthlyAdherencePercent: Int,
    val monthlyCompletedDays: Int,
    val monthlyDueDays: Int,
    val isTodayCompleted: Boolean,
    val isTodayDue: Boolean,
    val completedDateSet: Set<String>,
    val recent7Days: List<HabitDayStatus>
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class DailyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DailyRepository = (application as DailyApplication).repository

    private val _selectedDateKey = MutableStateFlow(DateUtils.getTodayKey())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _selectedHabitIdForStats = MutableStateFlow<Long?>(null)
    val selectedHabitIdForStats: StateFlow<Long?> = _selectedHabitIdForStats.asStateFlow()

    fun selectHabitForStats(taskId: Long?) {
        _selectedHabitIdForStats.value = taskId
    }

    val allActiveTasks: StateFlow<List<DailyTask>> = repository.getAllActiveTasks()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<DailyTask>> = repository.getAllTasks()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCompletions: StateFlow<List<TaskCompletion>> = repository.getAllCompletions()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habitStatsList: StateFlow<List<HabitStats>> = combine(
        allActiveTasks,
        allCompletions
    ) { tasks, completions ->
        tasks to completions
    }
        .distinctUntilChanged()
        .debounce(100L)
        .map { (tasks, completions) ->
            withContext(Dispatchers.Default) {
                calculateHabitStatsList(tasks, completions)
            }
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasksWithCompletions: StateFlow<List<TaskWithCompletion>> = _selectedDateKey
        .flatMapLatest { dateKey ->
            repository.getTasksWithCompletions(dateKey)
        }
        .combine(_selectedCategory) { list, category ->
            if (category == null) list
            else list.filter { isCategoryEquivalent(it.task.category, category) }
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCompletionDates: StateFlow<List<String>> = repository.getAllCompletionDates()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fullyCompletedDates: StateFlow<List<String>> = repository.getFullyCompletedDates()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streakStats: StateFlow<StreakStats> = fullyCompletedDates.map { dates ->
        withContext(Dispatchers.Default) {
            calculateFullStreakStats(dates)
        }
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakStats())

    val currentStreak: StateFlow<Int> = streakStats.map { it.currentStreak }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val routinesResetInfoDismissed: StateFlow<Boolean> =
        com.example.util.AppSettings.getInstance(application).routinesResetInfoDismissed

    fun dismissRoutinesResetInfo() {
        com.example.util.AppSettings.getInstance(getApplication()).dismissRoutinesResetInfo()
    }

    fun selectDate(dateKey: String) {
        _selectedDateKey.value = dateKey
    }

    fun selectCategory(category: String?) {
        val current = _selectedCategory.value
        _selectedCategory.value = if (current != null && category != null && isCategoryEquivalent(current, category)) null else category
    }

    fun toggleTask(taskId: Long) {
        if (!DateUtils.isToday(_selectedDateKey.value)) return
        viewModelScope.launch {
            repository.toggleTaskCompletion(taskId, _selectedDateKey.value)
        }
    }

    fun toggleTaskActive(task: DailyTask) {
        viewModelScope.launch {
            val updated = task.copy(isArchived = !task.isArchived)
            repository.updateTask(updated)
            val context = getApplication<Application>()
            if (updated.isArchived) {
                ReminderManager.cancelReminder(context, updated.id)
            } else if (updated.reminderEnabled) {
                ReminderManager.scheduleReminder(context, updated)
            }
        }
    }

    fun incrementCounter(taskId: Long, amount: Int) {
        if (!DateUtils.isToday(_selectedDateKey.value)) return
        viewModelScope.launch {
            repository.incrementTaskCounter(taskId, _selectedDateKey.value, amount)
        }
    }

    fun setCounter(taskId: Long, count: Int) {
        if (!DateUtils.isToday(_selectedDateKey.value)) return
        viewModelScope.launch {
            repository.setTaskCounter(taskId, _selectedDateKey.value, count)
        }
    }

    fun saveTask(task: DailyTask) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            if (task.id == 0L) {
                val newId = repository.insertTask(task)
                val savedTask = task.copy(id = newId)
                if (savedTask.reminderEnabled) {
                    ReminderManager.scheduleReminder(context, savedTask)
                }
            } else {
                repository.updateTask(task)
                if (task.reminderEnabled) {
                    ReminderManager.scheduleReminder(context, task)
                } else {
                    ReminderManager.cancelReminder(context, task.id)
                }
            }
        }
    }

    fun deleteTask(task: DailyTask) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            ReminderManager.cancelReminder(context, task.id)
            repository.deleteTask(task)
        }
    }

    private fun calculateFullStreakStats(completionDates: List<String>): StreakStats {
        if (completionDates.isEmpty()) return StreakStats()

        val dateSet = completionDates.toSet()
        val todayKey = DateUtils.getTodayKey()
        val isTodaySecured = dateSet.contains(todayKey)

        // 1. Current streak calculation
        var checkKey = todayKey
        var currentStreak = 0

        if (!dateSet.contains(checkKey)) {
            // Check yesterday
            checkKey = DateUtils.addDays(checkKey, -1)
            if (dateSet.contains(checkKey)) {
                while (dateSet.contains(checkKey)) {
                    currentStreak++
                    checkKey = DateUtils.addDays(checkKey, -1)
                }
            }
        } else {
            while (dateSet.contains(checkKey)) {
                currentStreak++
                checkKey = DateUtils.addDays(checkKey, -1)
            }
        }

        // 2. Longest streak calculation across all historic completion dates
        val sortedDates = completionDates.distinct().sorted()
        var longestStreak = 0
        var tempStreak = 0
        var prevDateStr: String? = null

        for (dStr in sortedDates) {
            if (prevDateStr == null) {
                tempStreak = 1
            } else {
                val diffDays = DateUtils.daysBetween(prevDateStr, dStr)
                if (diffDays == 1) {
                    tempStreak++
                } else if (diffDays > 1) {
                    tempStreak = 1
                }
            }
            if (tempStreak > longestStreak) {
                longestStreak = tempStreak
            }
            prevDateStr = dStr
        }

        if (currentStreak > longestStreak) {
            longestStreak = currentStreak
        }

        return StreakStats(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalDays = dateSet.size,
            isTodaySecured = isTodaySecured
        )
    }

    private data class HabitStatsCacheKey(
        val task: DailyTask,
        val completions: List<TaskCompletion>,
        val todayKey: String
    )
    private val habitStatsCache = java.util.concurrent.ConcurrentHashMap<Long, Pair<HabitStatsCacheKey, HabitStats>>()

    internal fun calculateHabitStatsList(
        tasks: List<DailyTask>,
        completions: List<TaskCompletion>
    ): List<HabitStats> {
        val todayKey = DateUtils.getTodayKey()
        val completionsByTask = completions.groupBy { it.taskId }

        val activeIds = tasks.map { it.id }.toSet()
        habitStatsCache.keys.retainAll(activeIds)

        val cal = Calendar.getInstance()
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthPrefix = SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
        val monthDates = (1..daysInMonth).map { String.format(Locale.US, "%s-%02d", monthPrefix, it) }
        val recent7DateKeys = DateUtils.getPastDays(7)

        return tasks.map { task ->
            val taskComps = completionsByTask[task.id] ?: emptyList()

            // Skip recalculation if task definition and completions have not changed
            val cached = habitStatsCache[task.id]
            if (cached != null && cached.first.task == task && cached.first.completions == taskComps && cached.first.todayKey == todayKey) {
                return@map cached.second
            }

            // Completion valid if count >= targetCount for counter, or count > 0 for standard
            val validCompletions = taskComps.filter { comp ->
                if (task.isCounter) comp.count >= task.targetCount else comp.count > 0
            }
            val completedDateSet = validCompletions.map { it.dateKey }.toSet()
            val totalCompletions = completedDateSet.size

            val totalCountSum: Long = if (task.isCounter) {
                taskComps.sumOf { it.count.toLong() }
            } else {
                totalCompletions.toLong()
            }

            // Today status
            val todayComp = taskComps.firstOrNull { it.dateKey == todayKey }
            val isTodayCompleted = if (task.isCounter) {
                (todayComp?.count ?: 0) >= task.targetCount
            } else {
                todayComp != null && todayComp.count > 0
            }

            val lastCompBeforeToday = validCompletions
                .filter { it.dateKey < todayKey }
                .maxOfOrNull { it.dateKey }

            val currentWeekDays = DateUtils.getDaysInSameWeek(todayKey)
            val weeklyCompletedCountThisWeek = validCompletions.count { currentWeekDays.contains(it.dateKey) }

            val isTodayDue = DateUtils.isTaskDueOnDate(
                frequencyType = task.frequencyType,
                daysOfWeek = task.daysOfWeek,
                targetDaysPerWeek = task.targetDaysPerWeek,
                dateKey = todayKey,
                weeklyCompletedCount = weeklyCompletedCountThisWeek,
                isCompletedOnDate = isTodayCompleted,
                intervalDays = task.intervalDays,
                intervalAnchorDate = task.intervalAnchorDate,
                lastCompletionDateKey = lastCompBeforeToday,
                createdAtMillis = task.createdAt
            )

            // Monthly stats
            val monthlyCompletedDays = monthDates.count { completedDateSet.contains(it) }
            val monthlyDueDays = monthDates.count { dKey ->
                val dWeekDays = DateUtils.getDaysInSameWeek(dKey)
                val dWeekCount = validCompletions.count { dWeekDays.contains(it.dateKey) }
                DateUtils.isTaskDueOnDate(
                    frequencyType = task.frequencyType,
                    daysOfWeek = task.daysOfWeek,
                    targetDaysPerWeek = task.targetDaysPerWeek,
                    dateKey = dKey,
                    weeklyCompletedCount = dWeekCount,
                    isCompletedOnDate = completedDateSet.contains(dKey),
                    createdAtMillis = task.createdAt
                )
            }.coerceAtLeast(1)

            val monthlyAdherencePercent = ((monthlyCompletedDays * 100) / monthlyDueDays).coerceIn(0, 100)

            // Recent 7 days (including today)
            val recent7 = recent7DateKeys.map { dKey ->
                val isDone = completedDateSet.contains(dKey)
                val isTod = dKey == todayKey
                val dWeekDays = DateUtils.getDaysInSameWeek(dKey)
                val dWeekCount = validCompletions.count { dWeekDays.contains(it.dateKey) }
                val due = DateUtils.isTaskDueOnDate(
                    frequencyType = task.frequencyType,
                    daysOfWeek = task.daysOfWeek,
                    targetDaysPerWeek = task.targetDaysPerWeek,
                    dateKey = dKey,
                    weeklyCompletedCount = dWeekCount,
                    isCompletedOnDate = isDone,
                    createdAtMillis = task.createdAt
                )
                val dayLabel = DateUtils.getDayOfWeek(dKey, AppLanguage.DE)
                val dayNum = DateUtils.getDayNumber(dKey)
                HabitDayStatus(
                    dateKey = dKey,
                    dayLabel = dayLabel,
                    dayNumber = dayNum,
                    isCompleted = isDone,
                    isToday = isTod,
                    isDue = due
                )
            }

            // Calculate current streak and longest streak for this habit
            val (currentStreak, longestStreak) = calculateHabitStreak(task, completedDateSet, todayKey)

            val computedStats = HabitStats(
                task = task,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                totalCompletions = totalCompletions,
                totalCountSum = totalCountSum,
                monthlyAdherencePercent = monthlyAdherencePercent,
                monthlyCompletedDays = monthlyCompletedDays,
                monthlyDueDays = monthlyDueDays,
                isTodayCompleted = isTodayCompleted,
                isTodayDue = isTodayDue,
                completedDateSet = completedDateSet,
                recent7Days = recent7
            )
            habitStatsCache[task.id] = Pair(HabitStatsCacheKey(task, taskComps, todayKey), computedStats)
            computedStats
        }
    }

    private fun calculateHabitStreak(
        task: DailyTask,
        completedDates: Set<String>,
        todayKey: String
    ): Pair<Int, Int> {
        if (completedDates.isEmpty()) return Pair(0, 0)
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        when (task.frequencyType) {
            "WEEKDAYS" -> {
                val allowedDays = task.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                if (allowedDays.isEmpty()) return calculateDailyHabitStreak(completedDates, todayKey)

                // Current streak on scheduled weekdays
                var curStreak = 0
                var checkKey = todayKey
                for (i in 0..365) {
                    val isoDay = DateUtils.getIsoDayOfWeek(checkKey)
                    if (allowedDays.contains(isoDay)) {
                        val isDone = completedDates.contains(checkKey)
                        if (checkKey == todayKey && !isDone) {
                            // Today is due but not done yet, continue looking backwards
                        } else if (isDone) {
                            curStreak++
                        } else {
                            break
                        }
                    }
                    checkKey = DateUtils.addDays(checkKey, -1)
                }

                // Longest streak on scheduled weekdays
                val sorted = completedDates.sorted()
                var longest = 0
                if (sorted.isNotEmpty()) {
                    var startKey = sorted.first()
                    var temp = 0
                    while (startKey <= todayKey) {
                        val isoDay = DateUtils.getIsoDayOfWeek(startKey)
                        if (allowedDays.contains(isoDay)) {
                            if (completedDates.contains(startKey)) {
                                temp++
                                if (temp > longest) longest = temp
                            } else {
                                temp = 0
                            }
                        }
                        startKey = DateUtils.addDays(startKey, 1)
                    }
                }
                return Pair(curStreak, maxOf(longest, curStreak))
            }
            "WEEKLY" -> {
                return calculateWeeklyHabitStreak(task, completedDates)
            }
            "INTERVAL" -> {
                return calculateIntervalHabitStreak(task, completedDates, todayKey)
            }
            else -> {
                return calculateDailyHabitStreak(completedDates, todayKey)
            }
        }
    }

    private fun calculateWeeklyHabitStreak(
        task: DailyTask,
        completedDates: Set<String>
    ): Pair<Int, Int> {
        if (completedDates.isEmpty()) return Pair(0, 0)
        val completionsByWeek = mutableMapOf<String, Int>()

        for (dStr in completedDates) {
            val weekKey = DateUtils.getDaysInSameWeek(dStr).first()
            completionsByWeek[weekKey] = (completionsByWeek[weekKey] ?: 0) + 1
        }

        val todayKey = DateUtils.getTodayKey()
        val curWeekKey = DateUtils.getDaysInSameWeek(todayKey).first()
        val curWeekComps = completionsByWeek[curWeekKey] ?: 0
        val curWeekTarget = DateUtils.getEffectiveWeeklyTarget(task.targetDaysPerWeek, task.createdAt, todayKey)

        var currentStreak = 0
        var checkKey = curWeekKey

        if (curWeekComps >= curWeekTarget) {
            currentStreak = 1
            checkKey = DateUtils.addDays(checkKey, -7)
        } else {
            checkKey = DateUtils.addDays(checkKey, -7)
        }

        while (true) {
            val createdWeekKey = if (task.createdAt > 0) DateUtils.getDaysInSameWeek(DateUtils.getDateKeyFromMillis(task.createdAt)).first() else "0000-00-00"
            if (checkKey < createdWeekKey) {
                break
            }
            val count = completionsByWeek[checkKey] ?: 0
            val weekTarget = DateUtils.getEffectiveWeeklyTarget(task.targetDaysPerWeek, task.createdAt, checkKey)
            if (count >= weekTarget) {
                currentStreak++
                checkKey = DateUtils.addDays(checkKey, -7)
            } else {
                break
            }
        }

        return Pair(currentStreak, currentStreak)
    }

    private fun calculateIntervalHabitStreak(
        task: DailyTask,
        completedDates: Set<String>,
        todayKey: String
    ): Pair<Int, Int> {
        if (completedDates.isEmpty()) return Pair(0, 0)
        val sortedDates = completedDates.sorted()
        val lastDate = sortedDates.last()
        val daysSinceLast = DateUtils.daysBetween(lastDate, todayKey)
        val safeInterval = task.intervalDays.coerceAtLeast(1)

        var currentStreak = 0
        if (daysSinceLast <= safeInterval) {
            currentStreak = 1
            for (i in sortedDates.size - 1 downTo 1) {
                val curr = sortedDates[i]
                val prev = sortedDates[i - 1]
                val diff = DateUtils.daysBetween(prev, curr)
                if (diff <= safeInterval + 1) {
                    currentStreak++
                } else {
                    break
                }
            }
        }

        var longest = currentStreak
        var temp = 1
        for (i in 1 until sortedDates.size) {
            val diff = DateUtils.daysBetween(sortedDates[i - 1], sortedDates[i])
            if (diff <= safeInterval + 1) {
                temp++
                if (temp > longest) longest = temp
            } else {
                temp = 1
            }
        }

        return Pair(currentStreak, maxOf(longest, currentStreak))
    }

    private fun calculateDailyHabitStreak(
        completedDates: Set<String>,
        todayKey: String
    ): Pair<Int, Int> {
        var checkKey = todayKey
        var currentStreak = 0

        if (!completedDates.contains(checkKey)) {
            // Check yesterday
            checkKey = DateUtils.addDays(checkKey, -1)
            if (completedDates.contains(checkKey)) {
                while (completedDates.contains(checkKey)) {
                    currentStreak++
                    checkKey = DateUtils.addDays(checkKey, -1)
                }
            }
        } else {
            while (completedDates.contains(checkKey)) {
                currentStreak++
                checkKey = DateUtils.addDays(checkKey, -1)
            }
        }

        // Longest streak
        val sortedDates = completedDates.distinct().sorted()
        var longestStreak = 0
        var tempStreak = 0
        var prevDateStr: String? = null

        for (dStr in sortedDates) {
            if (prevDateStr == null) {
                tempStreak = 1
            } else {
                val diffDays = DateUtils.daysBetween(prevDateStr, dStr)
                if (diffDays == 1) {
                    tempStreak++
                } else if (diffDays > 1) {
                    tempStreak = 1
                }
            }
            if (tempStreak > longestStreak) {
                longestStreak = tempStreak
            }
            prevDateStr = dStr
        }

        if (currentStreak > longestStreak) {
            longestStreak = currentStreak
        }

        return Pair(currentStreak, longestStreak)
    }

    companion object {
        fun normalizeCategory(category: String): String = when (category.trim().lowercase()) {
            "gesundheit", "health" -> "health"
            "sport", "workout" -> "workout"
            "fokus", "focus" -> "focus"
            "supplements", "supplemente" -> "supplements"
            "routine", "routinen", "routines" -> "routine"
            else -> category.trim().lowercase()
        }

        fun isCategoryEquivalent(categoryA: String?, categoryB: String?): Boolean {
            if (categoryA == null || categoryB == null) return categoryA == categoryB
            if (categoryA.equals(categoryB, ignoreCase = true)) return true
            return normalizeCategory(categoryA) == normalizeCategory(categoryB)
        }
    }
}
