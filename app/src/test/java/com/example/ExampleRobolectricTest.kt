package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.DailyApplication
import com.example.R
import com.example.data.DailyDatabase
import com.example.data.DailyTask
import com.example.data.TaskCompletion
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: DailyDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DailyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("More2Day", appName)
    }

    @Test
    fun testDailyTaskPersistenceAndDayReset() = runBlocking {
        val dao = db.dailyTaskDao()

        // 1. Insert daily task (e.g. Vitamin D3)
        val taskId = dao.insertTask(
            DailyTask(
                title = "Vitamin D3",
                category = "Supplements",
                reminderHour = 8,
                reminderMinute = 30,
                reminderEnabled = true
            )
        )

        // 2. Mark completed for today
        val todayKey = "2026-09-28"
        dao.insertCompletion(TaskCompletion(taskId = taskId, dateKey = todayKey))

        // Verify it is completed today
        val todayCompletion = dao.getCompletion(taskId, todayKey)
        assertNotNull(todayCompletion)

        // 3. Check tomorrow ("2026-09-29") - should be open (reset) without deleting today's completion!
        val tomorrowKey = "2026-09-29"
        val tomorrowCompletion = dao.getCompletion(taskId, tomorrowKey)
        assertNull(tomorrowCompletion)

        // Verify yesterday/today's completion is still intact for history/streaks
        val historyCompletion = dao.getCompletion(taskId, todayKey)
        assertNotNull(historyCompletion)

        // Test distinct dates query for streaks
        val allDates = dao.getAllCompletionDatesSync()
        assertEquals(1, allDates.size)
        assertTrue(allDates.contains("2026-09-28"))

        // Test fully completed dates: With 1 task and 1 completion, today is fully completed
        val fullyDone1 = dao.getFullyCompletedDatesSync()
        assertEquals(1, fullyDone1.size)
        assertEquals("2026-09-28", fullyDone1[0])

        // Add a second task that is NOT completed today
        val task2Id = dao.insertTask(
            DailyTask(title = "Wasser trinken", category = "Gesundheit")
        )
        // Now total active tasks = 2, but only 1 is completed today:
        val fullyDone2 = dao.getFullyCompletedDatesSync()
        // Should be empty because not all tasks were completed!
        assertEquals(0, fullyDone2.size)

        // Now complete the second task as well
        dao.insertCompletion(TaskCompletion(taskId = task2Id, dateKey = todayKey))
        val fullyDone3 = dao.getFullyCompletedDatesSync()
        // Now all 2 tasks are completed, so today qualifies for the streak!
        assertEquals(1, fullyDone3.size)
        assertEquals("2026-09-28", fullyDone3[0])
    }

    @Test
    fun testCounterTaskBehaviorAndZeroClamp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = com.example.data.DailyRepository(db.dailyTaskDao(), context)
        val todayKey = "2026-09-28"

        // 1. Create counter task (target 100 pushups)
        val taskId = repo.insertTask(
            DailyTask(
                title = "100 Liegestütze",
                category = "Sport",
                isCounter = true,
                targetCount = 100,
                unit = "Wdh."
            )
        )

        // 2. Initial state: count is 0
        val initialComp = db.dailyTaskDao().getCompletion(taskId, todayKey)
        assertNull(initialComp)

        // 3. Subtracting 10 when count is 0: MUST STAY 0, NOT COMPLETE OR OVERFLOW!
        val countAfterSub = repo.incrementTaskCounter(taskId, todayKey, -10)
        assertEquals(0, countAfterSub)
        val compAfterSub = db.dailyTaskDao().getCompletion(taskId, todayKey)
        assertNull(compAfterSub)

        // 4. Increment by 10
        val count1 = repo.incrementTaskCounter(taskId, todayKey, 10)
        assertEquals(10, count1)
        val comp1 = db.dailyTaskDao().getCompletion(taskId, todayKey)
        assertNotNull(comp1)
        assertEquals(10, comp1!!.count)

        // 5. Increment to full target (add 90)
        val count2 = repo.incrementTaskCounter(taskId, todayKey, 90)
        assertEquals(100, count2)

        // 6. Decrement by 10 back to 90
        val count3 = repo.incrementTaskCounter(taskId, todayKey, -10)
        assertEquals(90, count3)

        // 7. Decrement all the way down to 0
        val count4 = repo.incrementTaskCounter(taskId, todayKey, -90)
        assertEquals(0, count4)

        // 8. Subtracting again when 0 must stay 0
        val count5 = repo.incrementTaskCounter(taskId, todayKey, -10)
        assertEquals(0, count5)
    }

    @Test
    fun testStreakWidgetLayoutInflation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val views = android.widget.RemoteViews(context.packageName, R.layout.widget_streak_layout)
        val view = views.apply(context, null)
        assertNotNull(view)
        val countTv = view.findViewById<android.widget.TextView>(R.id.widget_streak_count)
        val topTv = view.findViewById<android.widget.TextView>(R.id.widget_streak_label_top)
        val bottomTv = view.findViewById<android.widget.TextView>(R.id.widget_streak_label_bottom)
        assertNotNull(countTv)
        assertNotNull(topTv)
        assertNotNull(bottomTv)

        // Test compact (1x1, 1x2) layout inflation
        val compactViews = android.widget.RemoteViews(context.packageName, R.layout.widget_streak_compact_layout)
        val compactView = compactViews.apply(context, null)
        assertNotNull(compactView)
        val compactCountTv = compactView.findViewById<android.widget.TextView>(R.id.widget_streak_count)
        assertNotNull(compactCountTv)

        // Test 2x2 layout inflation
        val views2x2 = android.widget.RemoteViews(context.packageName, R.layout.widget_streak_2x2_layout)
        val view2x2 = views2x2.apply(context, null)
        assertNotNull(view2x2)
        val count2x2 = view2x2.findViewById<android.widget.TextView>(R.id.widget_streak_count)
        val top2x2 = view2x2.findViewById<android.widget.TextView>(R.id.widget_streak_label_top)
        val bottom2x2 = view2x2.findViewById<android.widget.TextView>(R.id.widget_streak_label_bottom)
        assertNotNull(count2x2)
        assertNotNull(top2x2)
        assertNotNull(bottom2x2)
    }

    @Test
    fun testRoutinesResetInfoDismissal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appSettings = com.example.util.AppSettings.getInstance(context)

        // Initially not dismissed
        val initialDismissed = appSettings.routinesResetInfoDismissed.value
        assertEquals(false, initialDismissed)

        // Dismiss it
        appSettings.dismissRoutinesResetInfo()
        assertEquals(true, appSettings.routinesResetInfoDismissed.value)

        // Re-read settings instance to ensure persistence
        val reloadedSettings = com.example.util.AppSettings.getInstance(context)
        assertEquals(true, reloadedSettings.routinesResetInfoDismissed.value)
    }

    @Test
    fun testWeeklyAndWeekdayHabitPersistence() = runBlocking {
        val dao = db.dailyTaskDao()

        // 1. Weekly habit (e.g. 2x per week)
        val weeklyTaskId = dao.insertTask(
            DailyTask(
                title = "Sauna & Regeneration",
                category = "Gesundheit",
                frequencyType = "WEEKLY",
                targetDaysPerWeek = 2
            )
        )
        val loadedWeekly = dao.getTaskById(weeklyTaskId)
        assertNotNull(loadedWeekly)
        assertEquals("WEEKLY", loadedWeekly!!.frequencyType)
        assertEquals(2, loadedWeekly.targetDaysPerWeek)

        // 2. Specific weekdays habit (e.g. Monday, Wednesday, Friday: "1,3,5")
        val weekdayTaskId = dao.insertTask(
            DailyTask(
                title = "Gym Workout",
                category = "Sport",
                frequencyType = "WEEKDAYS",
                daysOfWeek = "1,3,5"
            )
        )
        val loadedWeekday = dao.getTaskById(weekdayTaskId)
        assertNotNull(loadedWeekday)
        assertEquals("WEEKDAYS", loadedWeekday!!.frequencyType)
        assertEquals("1,3,5", loadedWeekday.daysOfWeek)
    }

    @Test
    fun testDailyProgressWidgetLayoutInflation() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Compact (1x1)
        val compactViews = android.widget.RemoteViews(context.packageName, R.layout.widget_progress_compact_layout)
        val compactView = compactViews.apply(context, null)
        assertNotNull(compactView)
        val compactPercent = compactView.findViewById<android.widget.TextView>(R.id.widget_progress_compact_percent)
        val compactFraction = compactView.findViewById<android.widget.TextView>(R.id.widget_progress_compact_fraction)
        assertNotNull(compactPercent)
        assertNotNull(compactFraction)

        // 2. Wide (2x1)
        val wideViews = android.widget.RemoteViews(context.packageName, R.layout.widget_progress_wide_layout)
        val wideView = wideViews.apply(context, null)
        assertNotNull(wideView)
        val widePercent = wideView.findViewById<android.widget.TextView>(R.id.widget_progress_wide_percent)
        val wideText = wideView.findViewById<android.widget.TextView>(R.id.widget_progress_wide_text)
        val wideBar = wideView.findViewById<android.widget.ImageView>(R.id.widget_progress_wide_bar)
        assertNotNull(widePercent)
        assertNotNull(wideText)
        assertNotNull(wideBar)

        // 3. Large (2x2)
        val largeViews = android.widget.RemoteViews(context.packageName, R.layout.widget_progress_large_layout)
        val largeView = largeViews.apply(context, null)
        assertNotNull(largeView)
        val largeGauge = largeView.findViewById<android.widget.ImageView>(R.id.widget_progress_large_gauge)
        val largePercent = largeView.findViewById<android.widget.TextView>(R.id.widget_progress_large_percent)
        val largeStatus = largeView.findViewById<android.widget.TextView>(R.id.widget_progress_large_status)
        assertNotNull(largeGauge)
        assertNotNull(largePercent)
        assertNotNull(largeStatus)
    }

    @Test
    fun testWidgetBitmapUtils() {
        val circleBmp = com.example.widget.WidgetBitmapUtils.createColoredCircleBitmap(24, android.graphics.Color.RED)
        assertNotNull(circleBmp)
        assertEquals(24, circleBmp.width)
        assertEquals(24, circleBmp.height)

        val gaugeBmp = com.example.widget.WidgetBitmapUtils.createCircularProgressBitmap(100, 0.75f, 8f, android.graphics.Color.RED)
        assertNotNull(gaugeBmp)
        assertEquals(100, gaugeBmp.width)
        assertEquals(100, gaugeBmp.height)

        val barBmp = com.example.widget.WidgetBitmapUtils.createProgressBarBitmap(200, 16, 0.5f, android.graphics.Color.RED)
        assertNotNull(barBmp)
        assertEquals(200, barBmp.width)
        assertEquals(16, barBmp.height)
    }

    @Test
    fun testSnoozeSchedulingAndCancellation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Verify scheduleTaskSnooze and cancelTaskSnooze execute cleanly without throwing
        com.example.reminder.ReminderManager.scheduleTaskSnooze(context, taskId = 42L, minutes = 10)
        com.example.reminder.ReminderManager.cancelTaskSnooze(context, taskId = 42L)

        com.example.reminder.ReminderManager.scheduleGeneralSnooze(context, minutes = 30)
        com.example.reminder.ReminderManager.cancelGeneralSnooze(context)
    }

    @Test
    fun testVibrationSettingsAndHaptics() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appSettings = com.example.util.AppSettings.getInstance(context)

        // Vibration is off by default
        assertEquals(false, appSettings.vibrationFeedbackEnabled.value)

        // Enable vibration
        appSettings.setVibrationFeedbackEnabled(true)
        assertEquals(true, appSettings.vibrationFeedbackEnabled.value)

        // Completion animation is enabled by default
        assertEquals(true, appSettings.completionAnimationEnabled.value)

        // Test safe execution of HapticUtils
        com.example.util.HapticUtils.vibrateTick(context)
        com.example.util.HapticUtils.vibrateSuccess(context)
    }

    @Test
    fun testNewTaskCreatedTodayDoesNotBreakPastStreak() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = com.example.data.DailyRepository(db.dailyTaskDao(), context)
        val yesterday = "2026-09-28"
        val today = com.example.util.DateUtils.getTodayKey()

        // 1. Task created yesterday
        val task1Id = repo.insertTask(
            DailyTask(title = "Morning Run", category = "Sport", createdAt = 0L)
        )
        db.dailyTaskDao().insertCompletion(
            TaskCompletion(taskId = task1Id, dateKey = yesterday, count = 1)
        )

        // Yesterday was fully completed
        val fullyDoneBefore = repo.getFullyCompletedDatesSync()
        assertTrue(fullyDoneBefore.contains(yesterday))

        // 2. Today, a brand new task is created
        val task2Id = repo.insertTask(
            DailyTask(title = "Read 20 pages", category = "Fokus", createdAt = System.currentTimeMillis())
        )

        // 3. Yesterday's streak must NOT be broken!
        val fullyDoneAfter = repo.getFullyCompletedDatesSync()
        assertTrue(fullyDoneAfter.contains(yesterday))

        // 4. Querying tasks for yesterday should only return task1, not task2
        val yesterdayTasks = repo.getTasksWithCompletions(yesterday)
        val firstList = yesterdayTasks.first()
        assertEquals(1, firstList.size)
        assertEquals(task1Id, firstList[0].task.id)
    }

    @Test
    fun testPastDaysCannotBeModifiedInViewModel() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<DailyApplication>()
        val viewModel = com.example.ui.DailyViewModel(app)
        val yesterday = "2026-09-28"

        val taskId = app.repository.insertTask(
            DailyTask(title = "Meditation", category = "Fokus", createdAt = 0L)
        )

        // Select yesterday in ViewModel
        viewModel.selectDate(yesterday)

        // Attempt to toggle task on past day
        viewModel.toggleTask(taskId)
        viewModel.incrementCounter(taskId, 1)
        viewModel.setCounter(taskId, 5)

        // Verify that yesterday has NO completion recorded
        val comp = db.dailyTaskDao().getCompletion(taskId, yesterday)
        assertNull(comp)
    }

    @Test
    fun testIndividualHabitStatsCalculation() {
        val app = ApplicationProvider.getApplicationContext<DailyApplication>()
        val viewModel = com.example.ui.DailyViewModel(app)

        val habit1 = DailyTask(id = 101L, title = "Wasser 2L", category = "Gesundheit", isCounter = false)
        val habit2 = DailyTask(id = 102L, title = "Pushups", category = "Sport", isCounter = true, targetCount = 50, unit = "Reps")

        val completions = listOf(
            TaskCompletion(id = 1, taskId = 101L, dateKey = "2026-09-28", count = 1),
            TaskCompletion(id = 2, taskId = 101L, dateKey = "2026-09-29", count = 1),
            TaskCompletion(id = 3, taskId = 102L, dateKey = "2026-09-28", count = 50),
            TaskCompletion(id = 4, taskId = 102L, dateKey = "2026-09-29", count = 25)
        )

        val statsList = viewModel.calculateHabitStatsList(listOf(habit1, habit2), completions)

        // Verify habit1 stats
        val habit1Stats = statsList.firstOrNull { it.task.id == 101L }
        assertNotNull(habit1Stats)
        assertEquals(2, habit1Stats!!.totalCompletions)

        // Verify habit2 stats (only 1 full completion because count 25 < target 50)
        val habit2Stats = statsList.firstOrNull { it.task.id == 102L }
        assertNotNull(habit2Stats)
        assertEquals(1, habit2Stats!!.totalCompletions)
        assertEquals(75L, habit2Stats.totalCountSum)
    }

    @Test
    fun testCategoryEquivalenceAcrossLanguages() {
        assertTrue(com.example.ui.DailyViewModel.isCategoryEquivalent("Gesundheit", "Health"))
        assertTrue(com.example.ui.DailyViewModel.isCategoryEquivalent("Sport", "Workout"))
        assertTrue(com.example.ui.DailyViewModel.isCategoryEquivalent("Fokus", "Focus"))
        assertTrue(com.example.ui.DailyViewModel.isCategoryEquivalent("Supplements", "Supplements"))
        assertTrue(com.example.ui.DailyViewModel.isCategoryEquivalent("Routine", "Routines"))
    }

    @Test
    fun testWeeklyHabitDoesNotBreakDailyStreakOnNonRequiredDays() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = com.example.data.DailyRepository(db.dailyTaskDao(), context)

        // Monday of week
        val monday = "2026-09-28" // This was a Monday

        // 1. Create a daily task
        val dailyTaskId = repo.insertTask(
            DailyTask(title = "Daily Vitamin", category = "Supplements", createdAt = 0L)
        )
        // 2. Create a weekly task (1x per week)
        val weeklyTaskId = repo.insertTask(
            DailyTask(title = "Clean Room", category = "Routine", frequencyType = "WEEKLY", targetDaysPerWeek = 1, createdAt = 0L)
        )

        // Complete ONLY the daily task on Monday (clean room is NOT done yet)
        db.dailyTaskDao().insertCompletion(TaskCompletion(taskId = dailyTaskId, dateKey = monday, count = 1))

        // Check if Monday is counted as a fully completed day:
        // On Monday, the weekly task (1x/week) has 7 days left in the week and only 1 needed.
        // Therefore, it is NOT mandatory on Monday, and Monday's streak MUST be saved!
        val fullyDone = repo.getFullyCompletedDatesSync()
        assertTrue("Monday streak should be preserved even without weekly task completion!", fullyDone.contains(monday))
    }

    @Test
    fun testWeeklyHabitDueOnlyOnMandatoryDays() = runBlocking {
        // Mon..Thu with 0 completions: NOT due yet!
        assertEquals(false, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-09-28", 0, false))
        assertEquals(false, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-09-29", 0, false))
        assertEquals(false, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-09-30", 0, false))
        assertEquals(false, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-10-01", 0, false))

        // Fri..Sun with 0 completions: strictly mandatory and DUE!
        assertEquals(true, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-10-02", 0, false))
        assertEquals(true, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-10-03", 0, false))
        assertEquals(true, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-10-04", 0, false))

        // But if user completes it on Monday (1/3 done):
        // Then Tue..Fri are NOT due (remaining needed = 2, only Sat & Sun are mandatory!)
        assertEquals(false, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-09-29", 1, false))
        assertEquals(false, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-09-30", 1, false))
        assertEquals(false, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-10-01", 1, false))
        assertEquals(false, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-10-02", 1, false))
        // Sat & Sun are the 2 remaining mandatory days:
        assertEquals(true, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-10-03", 1, false))
        assertEquals(true, DateUtils.isTaskDueOnDate("WEEKLY", "", 3, "2026-10-04", 1, false))
    }

    @Test
    fun testIntervalHabitPersistenceAndDueCalculation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = com.example.data.DailyRepository(db.dailyTaskDao(), context)

        val startDay = "2026-10-01"
        // Create an interval task: every 5 days
        val intervalTaskId = repo.insertTask(
            DailyTask(
                title = "Water Plants",
                category = "Routine",
                frequencyType = "INTERVAL",
                intervalDays = 5,
                intervalAnchorDate = startDay,
                createdAt = 0L
            )
        )

        val task = db.dailyTaskDao().getTaskById(intervalTaskId)
        assertNotNull(task)
        assertEquals("INTERVAL", task!!.frequencyType)
        assertEquals(5, task.intervalDays)
        assertEquals(startDay, task.intervalAnchorDate)

        // Day 1 (2026-10-01): due from anchor
        assertTrue(DateUtils.isIntervalTaskDue(5, startDay, null, "2026-10-01"))

        // Complete on 2026-10-01
        db.dailyTaskDao().insertCompletion(TaskCompletion(taskId = intervalTaskId, dateKey = "2026-10-01", count = 1))

        // Intermediate days: Day 2 (10-02), Day 3 (10-03), Day 4 (10-04), Day 5 (10-05) -> NOT DUE
        assertEquals(false, DateUtils.isIntervalTaskDue(5, startDay, "2026-10-01", "2026-10-02"))
        assertEquals(false, DateUtils.isIntervalTaskDue(5, startDay, "2026-10-01", "2026-10-03"))
        assertEquals(false, DateUtils.isIntervalTaskDue(5, startDay, "2026-10-01", "2026-10-04"))
        assertEquals(false, DateUtils.isIntervalTaskDue(5, startDay, "2026-10-01", "2026-10-05"))

        // 5 days later: Day 6 (2026-10-06) -> DUE!
        assertTrue(DateUtils.isIntervalTaskDue(5, startDay, "2026-10-01", "2026-10-06"))

        // Days until due calculation
        assertEquals(4, DateUtils.getDaysUntilIntervalDue(5, startDay, "2026-10-01", "2026-10-02"))
        assertEquals(0, DateUtils.getDaysUntilIntervalDue(5, startDay, "2026-10-01", "2026-10-06"))
    }

    @Test
    fun testIntervalHabitDoesNotBreakDailyStreakOnIntermediateDays() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = com.example.data.DailyRepository(db.dailyTaskDao(), context)

        val day1 = "2026-10-01"
        val day2 = "2026-10-02"

        // 1. Daily task
        val dailyTaskId = repo.insertTask(DailyTask(title = "Morning Coffee", category = "Supplements", createdAt = 0L))
        // 2. Interval task: every 5 days, anchor = day1
        val intervalTaskId = repo.insertTask(
            DailyTask(title = "Change Sheets", category = "Routine", frequencyType = "INTERVAL", intervalDays = 5, intervalAnchorDate = day1, createdAt = 0L)
        )

        // Day 1: User does both daily task and interval task
        db.dailyTaskDao().insertCompletion(TaskCompletion(taskId = dailyTaskId, dateKey = day1, count = 1))
        db.dailyTaskDao().insertCompletion(TaskCompletion(taskId = intervalTaskId, dateKey = day1, count = 1))

        // Day 2: User does ONLY daily task (interval task is not due until Day 6)
        db.dailyTaskDao().insertCompletion(TaskCompletion(taskId = dailyTaskId, dateKey = day2, count = 1))

        val fullyDone = repo.getFullyCompletedDatesSync()
        // Day 2 MUST be saved in streak!
        assertTrue("Day 2 streak should be preserved because interval task is not due today!", fullyDone.contains(day2))
    }

    @Test
    fun testSundayCreationWeeklyTargetIsOne() {
        // Sunday 2026-10-04 (ISO day 7)
        val sundayMillis = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse("2026-10-04")!!.time

        // If created on Sunday with target = 3, effective target in that week is 1!
        val effectiveTargetSunday = DateUtils.getEffectiveWeeklyTarget(3, sundayMillis, "2026-10-04")
        assertEquals(1, effectiveTargetSunday)

        // On Sunday with 0 completions, it is mandatory (needed = 1, remaining = 1)
        assertTrue(DateUtils.isTaskMandatoryOnDate("WEEKLY", "", 3, 1, "", null, "2026-10-04", 0, false, sundayMillis))

        // In the next week (e.g. 2026-10-05, Monday), the full target = 3 applies!
        val effectiveTargetNextWeek = DateUtils.getEffectiveWeeklyTarget(3, sundayMillis, "2026-10-05")
        assertEquals(3, effectiveTargetNextWeek)
    }

    @Test
    fun testWeeklyTaskDoesNotBreakDailyStreakOnNonMandatoryDays() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = com.example.data.DailyRepository(db.dailyTaskDao(), context)

        // Monday 2026-09-28
        val mon = "2026-09-28"
        val monMillis = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(mon)!!.time
        val tue = "2026-09-29"

        // 1. Daily task
        val dailyTaskId = repo.insertTask(DailyTask(title = "Daily Meditation", category = "Routine", createdAt = monMillis))
        // 2. Weekly task created on Monday: 3x per week
        repo.insertTask(DailyTask(title = "Gym Workout", category = "Sport", frequencyType = "WEEKLY", targetDaysPerWeek = 3, createdAt = monMillis))

        // Monday: User completes ONLY the daily task
        db.dailyTaskDao().insertCompletion(TaskCompletion(taskId = dailyTaskId, dateKey = mon, count = 1))
        // Tuesday: User completes ONLY the daily task
        db.dailyTaskDao().insertCompletion(TaskCompletion(taskId = dailyTaskId, dateKey = tue, count = 1))

        val fullyDone = repo.getFullyCompletedDatesSync()
        // Both Monday and Tuesday must be fully completed because Gym (3x/week) was NOT mandatory on Mon or Tue!
        assertTrue("Monday streak should be secured!", fullyDone.contains(mon))
        assertTrue("Tuesday streak should be secured!", fullyDone.contains(tue))
    }

    @Test
    fun testDailyDatabaseDiskOpenAndValidation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Verify getInstance opens database cleanly without throwing IllegalStateException
        val diskDb = DailyDatabase.getInstance(context)
        assertNotNull(diskDb)
        val dao = diskDb.dailyTaskDao()
        assertNotNull(dao)

        val id = dao.insertTask(
            DailyTask(
                title = "Test Habit Disk",
                category = "Supplements",
                orderIndex = 1
            )
        )
        assertTrue(id > 0)
        val loaded = dao.getTaskById(id)
        assertNotNull(loaded)
        assertEquals("Test Habit Disk", loaded?.title)
    }
}
