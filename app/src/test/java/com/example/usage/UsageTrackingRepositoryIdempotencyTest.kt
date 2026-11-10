package com.example.usage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.DailyDatabase
import com.example.usage.engine.PackageClassifier
import com.example.usage.engine.UsageWindowBounds
import com.example.usage.model.MeasurementStatus
import com.example.usage.model.RawUsageEvent
import com.example.usage.model.UsageEventType
import com.example.usage.repository.UsageTrackingRepository
import com.example.usage.source.EventQueryResponse
import com.example.usage.source.UsageEventSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UsageTrackingRepositoryIdempotencyTest {

    private lateinit var db: DailyDatabase
    private val berlinZone = ZoneId.of("Europe/Berlin")

    private class FakeUsageEventSource(
        var status: MeasurementStatus = MeasurementStatus.VALID_USAGE,
        var events: List<RawUsageEvent> = emptyList()
    ) : UsageEventSource {
        override fun queryRawEvents(startEpochMs: Long, endEpochMs: Long): EventQueryResponse {
            if (status == MeasurementStatus.PERMISSION_MISSING || status == MeasurementStatus.TEMPORARILY_UNAVAILABLE) {
                return EventQueryResponse(status, emptyList())
            }
            val filtered = events.filter { it.timestampMs in startEpochMs..endEpochMs }
            val effectiveStatus = if (filtered.isEmpty()) MeasurementStatus.VALID_ZERO else MeasurementStatus.VALID_USAGE
            return EventQueryResponse(effectiveStatus, filtered)
        }

        override fun createPackageClassifier(): PackageClassifier {
            return PackageClassifier { _, _ -> null }
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DailyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `repeated imports of identical and overlapping windows are strictly idempotent`() {
        val dayWindow = UsageWindowBounds.getDayWindow(LocalDate.of(2026, 11, 10), berlinZone)
        val t0 = dayWindow.startEpochMs + 3600_000L // 01:00

        val rawEvents = listOf(
            RawUsageEvent(t0, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(t0, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(t0 + 10_000L, UsageEventType.ACTIVITY_RESUMED, "com.instagram.android", "MainActivity", 2),
            RawUsageEvent(t0 + 190_000L, UsageEventType.ACTIVITY_PAUSED, "com.instagram.android", "MainActivity", 3),
            RawUsageEvent(t0 + 200_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 4)
        )

        val fakeSource = FakeUsageEventSource(events = rawEvents)
        val repo = UsageTrackingRepository(db.usageTrackingDao(), fakeSource)

        // Import #1
        val res1 = repo.evaluateAndSyncWindow(
            windowStartMs = dayWindow.startEpochMs,
            windowEndMs = t0 + 300_000L,
            zoneId = berlinZone
        )
        // Import #2 (exact same window)
        val res2 = repo.evaluateAndSyncWindow(
            windowStartMs = dayWindow.startEpochMs,
            windowEndMs = t0 + 300_000L,
            zoneId = berlinZone
        )
        // Import #3 (extended window later in the day)
        val res3 = repo.evaluateAndSyncWindow(
            windowStartMs = dayWindow.startEpochMs,
            windowEndMs = t0 + 600_000L,
            zoneId = berlinZone
        )

        assertEquals(180_000L, res1.totalAppForegroundDurationMs)
        assertEquals(180_000L, res2.totalAppForegroundDurationMs)
        assertEquals(180_000L, res3.totalAppForegroundDurationMs)

        // Verify database contains only 1 APP_FOREGROUND row for Instagram (no duplicate rows)
        val storedRows = db.usageTrackingDao().getSessionsOverlappingWindowSync(dayWindow.startEpochMs, dayWindow.endEpochMs)
        val igRows = storedRows.filter { it.packageName == "com.instagram.android" && it.sessionType == "APP_FOREGROUND" }
        assertEquals(1, igRows.size)
        assertEquals(180_000L, igRows[0].durationMs)
    }

    @Test
    fun `ongoing projection is not duplicated when finalized on subsequent import`() {
        val dayWindow = UsageWindowBounds.getDayWindow(LocalDate.of(2026, 11, 10), berlinZone)
        val t0 = dayWindow.startEpochMs + 3600_000L

        // Step 1: User starts using app at t0 + 10s, query happens at t0 + 40s while still open (30s ongoing projection)
        val initialEvents = listOf(
            RawUsageEvent(t0, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(t0, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(t0 + 10_000L, UsageEventType.ACTIVITY_RESUMED, "com.youtube.android", "WatchActivity", 2)
        )
        val fakeSource = FakeUsageEventSource(events = initialEvents)
        val repo = UsageTrackingRepository(db.usageTrackingDao(), fakeSource)

        val runningResult = repo.evaluateAndSyncWindow(
            windowStartMs = dayWindow.startEpochMs,
            windowEndMs = t0 + 40_000L,
            zoneId = berlinZone,
            projectOngoingAtWindowEnd = true
        )
        assertEquals(30_000L, runningResult.totalAppForegroundDurationMs)
        assertEquals(0L, runningResult.finalizedAppForegroundDurationMap["com.youtube.android"] ?: 0L)

        // Step 2: User finishes using app at t0 + 70s (total 60s session), query happens at t0 + 100s
        fakeSource.events = initialEvents + listOf(
            RawUsageEvent(t0 + 70_000L, UsageEventType.ACTIVITY_PAUSED, "com.youtube.android", "WatchActivity", 3),
            RawUsageEvent(t0 + 70_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 4)
        )

        val finalizedResult = repo.evaluateAndSyncWindow(
            windowStartMs = dayWindow.startEpochMs,
            windowEndMs = t0 + 100_000L,
            zoneId = berlinZone,
            projectOngoingAtWindowEnd = true
        )

        // Total must be 60_000 ms (NOT 30_000 + 60_000 = 90_000 ms!)
        assertEquals(60_000L, finalizedResult.totalAppForegroundDurationMs)
        assertEquals(60_000L, finalizedResult.finalizedAppForegroundDurationMap["com.youtube.android"])
    }

    @Test
    fun `persisted historical sessions are preserved when raw events for older days expire`() {
        val day1Window = UsageWindowBounds.getDayWindow(LocalDate.of(2026, 11, 5), berlinZone)
        val day6Window = UsageWindowBounds.getDayWindow(LocalDate.of(2026, 11, 10), berlinZone)

        val d1Start = day1Window.startEpochMs + 60_000L
        val d6Start = day6Window.startEpochMs + 60_000L

        val fakeSource = FakeUsageEventSource()
        val repo = UsageTrackingRepository(db.usageTrackingDao(), fakeSource)

        // On Day 1, raw events exist and get imported (100s of usage)
        fakeSource.events = listOf(
            RawUsageEvent(d1Start, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(d1Start, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(d1Start, UsageEventType.ACTIVITY_RESUMED, "com.anki.droid", "StudyActivity", 2),
            RawUsageEvent(d1Start + 100_000L, UsageEventType.ACTIVITY_PAUSED, "com.anki.droid", "StudyActivity", 3),
            RawUsageEvent(d1Start + 100_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 4)
        )
        repo.evaluateAndSyncWindow(day1Window.startEpochMs, day1Window.endEpochMs, berlinZone)

        // On Day 6, Android has purged Day 1 raw events; only Day 6 raw events (200s of usage) are returned
        fakeSource.events = listOf(
            RawUsageEvent(d6Start, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(d6Start, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(d6Start, UsageEventType.ACTIVITY_RESUMED, "com.anki.droid", "StudyActivity", 2),
            RawUsageEvent(d6Start + 200_000L, UsageEventType.ACTIVITY_PAUSED, "com.anki.droid", "StudyActivity", 3),
            RawUsageEvent(d6Start + 200_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 4)
        )

        // Query 7-day rolling window covering Day 1 through Day 6
        val weekResult = repo.evaluateAndSyncWindow(
            windowStartMs = day1Window.startEpochMs,
            windowEndMs = day6Window.endEpochMs,
            zoneId = berlinZone
        )

        // Must combine persisted Day 1 (100s) + fresh Day 6 (200s) = 300_000 ms!
        assertEquals(300_000L, weekResult.appForegroundDurationMap["com.anki.droid"])
    }

    @Test
    fun `permission revocation and empty history return distinct MeasurementStatus values`() {
        val dayWindow = UsageWindowBounds.getDayWindow(LocalDate.of(2026, 11, 10), berlinZone)
        val fakeSource = FakeUsageEventSource(status = MeasurementStatus.PERMISSION_MISSING)
        val repo = UsageTrackingRepository(db.usageTrackingDao(), fakeSource)

        val missingPermResult = repo.evaluateAndSyncWindow(dayWindow.startEpochMs, dayWindow.endEpochMs, berlinZone)
        assertEquals(MeasurementStatus.PERMISSION_MISSING, missingPermResult.status)

        fakeSource.status = MeasurementStatus.TEMPORARILY_UNAVAILABLE
        val unavailableResult = repo.evaluateAndSyncWindow(dayWindow.startEpochMs, dayWindow.endEpochMs, berlinZone)
        assertEquals(MeasurementStatus.TEMPORARILY_UNAVAILABLE, unavailableResult.status)

        fakeSource.status = MeasurementStatus.VALID_ZERO
        fakeSource.events = emptyList()
        val validZeroResult = repo.evaluateAndSyncWindow(dayWindow.startEpochMs, dayWindow.endEpochMs, berlinZone)
        assertEquals(MeasurementStatus.VALID_ZERO, validZeroResult.status)
        assertTrue(validZeroResult.appForegroundDurationMap.isEmpty())
    }

    @Test
    fun `migration 6 to 7 preserves existing daily_tasks and task_completions while creating usage tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_6_to_7.db"
        context.deleteDatabase(dbName)

        // 1. Create a v6 SQLite database directly and populate a habit + completion
        val rawV6Db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(dbName),
            null
        )
        rawV6Db.version = 6
        rawV6Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `daily_tasks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `reminderHour` INTEGER,
                `reminderMinute` INTEGER,
                `reminderEnabled` INTEGER NOT NULL,
                `iconName` TEXT NOT NULL,
                `orderIndex` INTEGER NOT NULL,
                `isCounter` INTEGER NOT NULL,
                `targetCount` INTEGER NOT NULL,
                `unit` TEXT NOT NULL,
                `frequencyType` TEXT NOT NULL,
                `targetDaysPerWeek` INTEGER NOT NULL,
                `daysOfWeek` TEXT NOT NULL,
                `intervalDays` INTEGER NOT NULL,
                `intervalAnchorDate` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `isArchived` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawV6Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `task_completions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `taskId` INTEGER NOT NULL,
                `dateKey` TEXT NOT NULL,
                `completedAt` INTEGER NOT NULL,
                `count` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawV6Db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_daily_tasks_isArchived_orderIndex` ON `daily_tasks` (`isArchived`, `orderIndex`, `id`)"
        )
        rawV6Db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_daily_tasks_reminderEnabled` ON `daily_tasks` (`isArchived`, `reminderEnabled`)"
        )
        rawV6Db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_task_completions_taskId_dateKey` ON `task_completions` (`taskId`, `dateKey`)"
        )
        rawV6Db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_task_completions_dateKey` ON `task_completions` (`dateKey`)"
        )

        rawV6Db.execSQL(
            """
            INSERT INTO `daily_tasks` (
                `id`, `title`, `category`, `reminderHour`, `reminderMinute`, `reminderEnabled`,
                `iconName`, `orderIndex`, `isCounter`, `targetCount`, `unit`, `frequencyType`,
                `targetDaysPerWeek`, `daysOfWeek`, `intervalDays`, `intervalAnchorDate`, `createdAt`, `isArchived`
            ) VALUES (42, 'Existing User Habit', 'Fokus', 8, 0, 1, 'book', 0, 0, 1, '', 'DAILY', 1, '1,2,3,4,5,6,7', 1, '', 1000, 0)
            """.trimIndent()
        )
        rawV6Db.execSQL(
            """
            INSERT INTO `task_completions` (`id`, `taskId`, `dateKey`, `completedAt`, `count`)
            VALUES (7, 42, '2026-11-10', 2000, 1)
            """.trimIndent()
        )
        rawV6Db.close()

        // 2. Open with Room at version 7 using ONLY MIGRATION_6_7 (NO fallbackToDestructiveMigration!)
        val migratedDb = Room.databaseBuilder(context, DailyDatabase::class.java, dbName)
            .addMigrations(DailyDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        try {
            // Verify user habit and completion survived intact
            val tasks = migratedDb.dailyTaskDao().getAllActiveTasksSync()
            assertEquals(1, tasks.size)
            assertEquals(42L, tasks[0].id)
            assertEquals("Existing User Habit", tasks[0].title)

            val completions = migratedDb.dailyTaskDao().getCompletionsForDateSync("2026-11-10")
            assertEquals(1, completions.size)
            assertEquals(42L, completions[0].taskId)

            // Verify new usage tables are operational
            val emptySessions = migratedDb.usageTrackingDao().getSessionsForDateKeySync("2026-11-10")
            assertTrue(emptySessions.isEmpty())
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `diagnostics formatter includes intervals, exclusions, status, and unrounded ms`() {
        val dayWindow = UsageWindowBounds.getDayWindow(LocalDate.of(2026, 11, 10), berlinZone)
        val t0 = dayWindow.startEpochMs + 10_000L
        val fakeSource = FakeUsageEventSource(
            events = listOf(
                RawUsageEvent(t0, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
                RawUsageEvent(t0, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
                RawUsageEvent(t0 + 5_000L, UsageEventType.ACTIVITY_RESUMED, "com.instagram.android", "MainActivity", 2),
                RawUsageEvent(t0 + 65_000L, UsageEventType.ACTIVITY_PAUSED, "com.instagram.android", "MainActivity", 3)
            )
        )
        val repo = UsageTrackingRepository(db.usageTrackingDao(), fakeSource)
        val res = repo.evaluateAndSyncWindow(dayWindow.startEpochMs, t0 + 100_000L, berlinZone)

        val formatted = com.example.usage.diagnostics.UsageDiagnosticsFormatter.formatReportForExport(
            report = res.diagnostics,
            hasUsagePermission = true,
            isAccessibilityEnabled = true
        )

        assertTrue(formatted.contains("MeasurementStatus: VALID_USAGE"))
        assertTrue(formatted.contains("com.instagram.android: 60000 ms"))
        assertTrue(formatted.contains("[APP_FOREGROUND] com.instagram.android"))
    }
}
