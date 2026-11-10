package com.example.usage

import com.example.usage.engine.PackageClassifier
import com.example.usage.engine.UsageSessionReconstructor
import com.example.usage.engine.UsageWindowBounds
import com.example.usage.model.ExclusionReason
import com.example.usage.model.MeasurementStatus
import com.example.usage.model.RawUsageEvent
import com.example.usage.model.ReconstructionCheckpointState
import com.example.usage.model.TimeInterval
import com.example.usage.model.UsageEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageSessionReconstructorTest {

    private val berlinZone: ZoneId = ZoneId.of("Europe/Berlin")

    private val testClassifier = PackageClassifier { pkg, cls ->
        when {
            pkg == "com.more2day.app" || cls.contains("LockOverlayActivity") ->
                ExclusionReason.OWN_APP_OR_LOCK_OVERLAY
            pkg == "com.android.systemui" ->
                ExclusionReason.SYSTEM_UI
            pkg == "com.google.android.apps.nexuslauncher" ->
                ExclusionReason.LAUNCHER_OR_HOME
            pkg == "com.google.android.inputmethod.latin" ->
                ExclusionReason.INPUT_METHOD_EDITOR
            else -> null
        }
    }

    @Test
    fun `1 simple app session produces exact foreground and device unlocked duration`() {
        val windowStart = 1_000_000L
        val windowEnd = 2_000_000L

        val events = listOf(
            RawUsageEvent(windowStart + 5_000L, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(windowStart + 5_000L, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(windowStart + 10_000L, UsageEventType.ACTIVITY_RESUMED, "com.instagram.android", "MainActivity", 2),
            RawUsageEvent(windowStart + 70_000L, UsageEventType.ACTIVITY_PAUSED, "com.instagram.android", "MainActivity", 3),
            RawUsageEvent(windowStart + 80_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 4)
        )

        val result = UsageSessionReconstructor.reconstruct(
            rawEvents = events,
            queryLookbackStartMs = windowStart,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = berlinZone,
            packageClassifier = testClassifier
        )

        assertEquals(MeasurementStatus.VALID_USAGE, result.status)
        assertEquals(60_000L, result.appForegroundDurationMap["com.instagram.android"])
        assertEquals(60_000L, result.totalAppForegroundDurationMs)
        assertEquals(75_000L, result.unlockedDeviceDurationMs)
        assertEquals(75_000L, result.interactiveDeviceDurationMs)
    }

    @Test
    fun `2 screen off and back on excludes screen-off gap while activity stays resumed`() {
        val windowStart = 1_000_000L
        val windowEnd = 2_000_000L

        // App resumed at +0s, screen turns off at +30s (without PAUSE or with delayed PAUSE),
        // screen turns back on + unlocked at +90s, app paused at +120s.
        // Expected app foreground: [0..30s) + [90s..120s) = 60_000 ms (screen-off gap [30s..90s) excluded!)
        val events = listOf(
            RawUsageEvent(windowStart, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(windowStart, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(windowStart, UsageEventType.ACTIVITY_RESUMED, "com.youtube.android", "WatchActivity", 2),
            RawUsageEvent(windowStart + 30_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 3),
            RawUsageEvent(windowStart + 90_000L, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 4),
            RawUsageEvent(windowStart + 90_000L, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 5),
            RawUsageEvent(windowStart + 120_000L, UsageEventType.ACTIVITY_PAUSED, "com.youtube.android", "WatchActivity", 6),
            RawUsageEvent(windowStart + 120_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 7)
        )

        val result = UsageSessionReconstructor.reconstruct(
            rawEvents = events,
            queryLookbackStartMs = windowStart,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = berlinZone,
            packageClassifier = testClassifier
        )

        assertEquals(60_000L, result.appForegroundDurationMap["com.youtube.android"])
        assertEquals(60_000L, result.unlockedDeviceDurationMs)
    }

    @Test
    fun `3 keyguard lockscreen interaction counts as interactive device time but NOT unlocked or app foreground time`() {
        val windowStart = 1_000_000L
        val windowEnd = 2_000_000L

        // t=+0s: Screen turns on with Keyguard shown (user looks at lockscreen notifications for 20s)
        // t=+5s: Background app tries to resume behind keyguard
        // t=+20s: User unlocks device (KEYGUARD_HIDDEN)
        // t=+50s: App pauses and screen turns off
        val events = listOf(
            RawUsageEvent(windowStart, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(windowStart, UsageEventType.KEYGUARD_SHOWN, sequenceIndex = 1),
            RawUsageEvent(windowStart + 5_000L, UsageEventType.ACTIVITY_RESUMED, "com.reddit.frontpage", "MainActivity", 2),
            RawUsageEvent(windowStart + 20_000L, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 3),
            RawUsageEvent(windowStart + 50_000L, UsageEventType.ACTIVITY_PAUSED, "com.reddit.frontpage", "MainActivity", 4),
            RawUsageEvent(windowStart + 50_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 5)
        )

        val result = UsageSessionReconstructor.reconstruct(
            rawEvents = events,
            queryLookbackStartMs = windowStart,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = berlinZone,
            packageClassifier = testClassifier
        )

        // Interactive device time = 50s (0..50s)
        assertEquals(50_000L, result.interactiveDeviceDurationMs)
        // Unlocked device time = 30s (20s..50s)
        assertEquals(30_000L, result.unlockedDeviceDurationMs)
        // Qualified app foreground time = 30s (only after KEYGUARD_HIDDEN at +20s!)
        assertEquals(30_000L, result.appForegroundDurationMap["com.reddit.frontpage"])
    }

    @Test
    fun `4 internal activity switch and delayed pause or stop of old activity does not break app session`() {
        val windowStart = 1_000_000L
        val windowEnd = 2_000_000L

        // SplashActivity resumes at +0s
        // HomeActivity of same package resumes at +2s
        // SplashActivity pauses at +3s and stops at +5s (delayed lifecycle callback!)
        // HomeActivity continues until +40s, when it pauses and stops at +41s.
        val pkg = "com.twitter.android"
        val events = listOf(
            RawUsageEvent(windowStart, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(windowStart, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(windowStart, UsageEventType.ACTIVITY_RESUMED, pkg, "SplashActivity", 2),
            RawUsageEvent(windowStart + 2_000L, UsageEventType.ACTIVITY_RESUMED, pkg, "HomeActivity", 3),
            RawUsageEvent(windowStart + 3_000L, UsageEventType.ACTIVITY_PAUSED, pkg, "SplashActivity", 4),
            RawUsageEvent(windowStart + 5_000L, UsageEventType.ACTIVITY_STOPPED, pkg, "SplashActivity", 5),
            RawUsageEvent(windowStart + 40_000L, UsageEventType.ACTIVITY_PAUSED, pkg, "HomeActivity", 6),
            RawUsageEvent(windowStart + 41_000L, UsageEventType.ACTIVITY_STOPPED, pkg, "HomeActivity", 7),
            RawUsageEvent(windowStart + 41_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 8)
        )

        val result = UsageSessionReconstructor.reconstruct(
            rawEvents = events,
            queryLookbackStartMs = windowStart,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = berlinZone,
            packageClassifier = testClassifier
        )

        // Foreground time must be continuous [0..40s) = 40_000 ms (NOT cut off at 3s or 5s!)
        assertEquals(40_000L, result.appForegroundDurationMap[pkg])
        // Visible time (until ACTIVITY_STOPPED of HomeActivity at 41s) = 41_000 ms
        assertEquals(41_000L, result.appVisibleDurationMap[pkg])
    }

    @Test
    fun `5 overlapping intervals of the same package are merged before summation`() {
        val intervals = listOf(
            TimeInterval(10_000L, 50_000L),
            TimeInterval(30_000L, 80_000L),
            TimeInterval(80_000L, 100_000L) // adjacent
        )
        val merged = UsageSessionReconstructor.mergeIntervals(intervals)
        assertEquals(1, merged.size)
        assertEquals(10_000L, merged[0].startEpochMs)
        assertEquals(100_000L, merged[0].endEpochMs)
        assertEquals(90_000L, merged[0].durationMs)
    }

    @Test
    fun `6 midnight boundary clips cross-midnight session accurately to Day 1 and Day 2`() {
        val day1 = LocalDate.of(2026, 11, 9)
        val day2 = LocalDate.of(2026, 11, 10)
        val day1Window = UsageWindowBounds.getDayWindow(day1, berlinZone)
        val day2Window = UsageWindowBounds.getDayWindow(day2, berlinZone)
        val midnightMs = day2Window.startEpochMs

        // Session starts at 23:50 on Day 1 (10 min before midnight) and ends at 00:15 on Day 2 (15 min after midnight)
        val sessionStart = midnightMs - 10 * 60_000L
        val sessionEnd = midnightMs + 15 * 60_000L

        val events = listOf(
            RawUsageEvent(sessionStart, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(sessionStart, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(sessionStart, UsageEventType.ACTIVITY_RESUMED, "com.netflix.mediaclient", "PlayerActivity", 2),
            RawUsageEvent(sessionEnd, UsageEventType.ACTIVITY_PAUSED, "com.netflix.mediaclient", "PlayerActivity", 3),
            RawUsageEvent(sessionEnd, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 4)
        )

        val day1Result = UsageSessionReconstructor.reconstruct(
            rawEvents = events,
            queryLookbackStartMs = day1Window.startEpochMs,
            windowStartMs = day1Window.startEpochMs,
            windowEndMs = day1Window.endEpochMs,
            zoneId = berlinZone,
            packageClassifier = testClassifier,
            projectOngoingAtWindowEnd = true
        )

        val day2Result = UsageSessionReconstructor.reconstruct(
            rawEvents = events,
            queryLookbackStartMs = sessionStart, // Lookback covers 23:50 on Day 1!
            windowStartMs = day2Window.startEpochMs,
            windowEndMs = day2Window.endEpochMs,
            zoneId = berlinZone,
            packageClassifier = testClassifier,
            projectOngoingAtWindowEnd = false
        )

        // Day 1 gets exactly 10 minutes (600_000 ms)
        assertEquals(10 * 60_000L, day1Result.appForegroundDurationMap["com.netflix.mediaclient"])
        // Day 2 gets exactly 15 minutes (900_000 ms)
        assertEquals(15 * 60_000L, day2Result.appForegroundDurationMap["com.netflix.mediaclient"])
    }

    @Test
    fun `7 daylight saving time (DST) spring-forward 23h day and fall-back 25h day handled accurately`() {
        // Spring forward in Europe/Berlin: 2026-03-29 (clocks jump 02:00 -> 03:00, day is 23 hours)
        val springDay = LocalDate.of(2026, 3, 29)
        val springWindow = UsageWindowBounds.getDayWindow(springDay, berlinZone)
        assertEquals(23L * 3600_000L, springWindow.durationMs)
        assertEquals(1, UsageWindowBounds.daysBetween("2026-03-29", "2026-03-30"))

        // Fall back in Europe/Berlin: 2026-10-25 (clocks repeat 02:00..03:00, day is 25 hours)
        val fallDay = LocalDate.of(2026, 10, 25)
        val fallWindow = UsageWindowBounds.getDayWindow(fallDay, berlinZone)
        assertEquals(25L * 3600_000L, fallWindow.durationMs)
        assertEquals(1, UsageWindowBounds.daysBetween("2026-10-25", "2026-10-26"))
    }

    @Test
    fun `8 orphan pause event without prior resume does NOT invent session from midnight and sets HISTORY_INCOMPLETE`() {
        val dayWindow = UsageWindowBounds.getDayWindow("2026-11-10", berlinZone)
        // First event of the day is an ACTIVITY_PAUSED at 08:05 AM with no lookback/checkpoint resume
        val eightOhFiveMs = dayWindow.startEpochMs + (8 * 60 + 5) * 60_000L

        val events = listOf(
            RawUsageEvent(eightOhFiveMs, UsageEventType.ACTIVITY_PAUSED, "com.instagram.android", "MainActivity", 0)
        )

        val result = UsageSessionReconstructor.reconstruct(
            rawEvents = events,
            queryLookbackStartMs = dayWindow.startEpochMs,
            windowStartMs = dayWindow.startEpochMs,
            windowEndMs = dayWindow.endEpochMs,
            zoneId = berlinZone,
            initialCheckpoint = null,
            packageClassifier = testClassifier
        )

        // Must NOT create an 8h05m ghost session from midnight!
        assertEquals(0L, result.totalAppForegroundDurationMs)
        assertNull(result.appForegroundDurationMap["com.instagram.android"])
        assertEquals(MeasurementStatus.HISTORY_INCOMPLETE, result.status)
    }

    @Test
    fun `9 ongoing session at windowEnd is separated as ongoing projection from finalized intervals`() {
        val windowStart = 1_000_000L
        val nowMs = windowStart + 120_000L

        // Session 1: finalized [10s..40s) = 30s
        // Session 2: started at 75s, still running at nowMs (120s) = 45s ongoing projection
        val events = listOf(
            RawUsageEvent(windowStart, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(windowStart, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(windowStart + 10_000L, UsageEventType.ACTIVITY_RESUMED, "com.spotify.music", "MainActivity", 2),
            RawUsageEvent(windowStart + 40_000L, UsageEventType.ACTIVITY_PAUSED, "com.spotify.music", "MainActivity", 3),
            RawUsageEvent(windowStart + 75_000L, UsageEventType.ACTIVITY_RESUMED, "com.spotify.music", "MainActivity", 4)
        )

        val result = UsageSessionReconstructor.reconstruct(
            rawEvents = events,
            queryLookbackStartMs = windowStart,
            windowStartMs = windowStart,
            windowEndMs = nowMs,
            zoneId = berlinZone,
            packageClassifier = testClassifier,
            projectOngoingAtWindowEnd = true
        )

        // Finalized only = 30_000 ms
        assertEquals(30_000L, result.finalizedAppForegroundDurationMap["com.spotify.music"])
        // Total including ongoing projection = 30_000 + 45_000 = 75_000 ms
        assertEquals(75_000L, result.appForegroundDurationMap["com.spotify.music"])
        assertEquals(45_000L, result.diagnostics.ongoingProjectionDurationMs)
        assertEquals("com.spotify.music", result.diagnostics.ongoingProjectionPackage)
    }

    @Test
    fun `10 graceful DEVICE_SHUTDOWN closes open session and ungraceful reboot does NOT count power-off gap`() {
        val windowStart = 1_000_000L
        val windowEnd = 10_000_000L

        // Case A: Graceful shutdown at +50s, startup at +500s
        val gracefulEvents = listOf(
            RawUsageEvent(windowStart, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(windowStart, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(windowStart + 10_000L, UsageEventType.ACTIVITY_RESUMED, "com.duolingo", "LessonActivity", 2),
            RawUsageEvent(windowStart + 50_000L, UsageEventType.DEVICE_SHUTDOWN, sequenceIndex = 3),
            RawUsageEvent(windowStart + 500_000L, UsageEventType.DEVICE_STARTUP, sequenceIndex = 4),
            RawUsageEvent(windowStart + 510_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 5)
        )

        val gracefulResult = UsageSessionReconstructor.reconstruct(
            rawEvents = gracefulEvents,
            queryLookbackStartMs = windowStart,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = berlinZone,
            packageClassifier = testClassifier
        )
        assertEquals(40_000L, gracefulResult.appForegroundDurationMap["com.duolingo"])

        // Case B: Ungraceful reboot (battery died at +30s without DEVICE_SHUTDOWN; device boots 2 hours later)
        val twoHoursMs = 2L * 3600_000L
        val crashEvents = listOf(
            RawUsageEvent(windowStart, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(windowStart, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(windowStart + 10_000L, UsageEventType.ACTIVITY_RESUMED, "com.duolingo", "LessonActivity", 2),
            // No PAUSE, no SHUTDOWN! Device boots 2 hours later:
            RawUsageEvent(windowStart + twoHoursMs, UsageEventType.DEVICE_STARTUP, sequenceIndex = 3),
            RawUsageEvent(windowStart + twoHoursMs + 5_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 4)
        )

        val crashResult = UsageSessionReconstructor.reconstruct(
            rawEvents = crashEvents,
            queryLookbackStartMs = windowStart,
            windowStartMs = windowStart,
            windowEndMs = windowStart + twoHoursMs + 60_000L,
            zoneId = berlinZone,
            packageClassifier = testClassifier
        )

        // Must NOT count the 2-hour dead-battery gap as Duolingo usage!
        val duoTime = crashResult.appForegroundDurationMap["com.duolingo"] ?: 0L
        assertEquals(0L, duoTime)
        assertEquals(MeasurementStatus.HISTORY_INCOMPLETE, crashResult.status)
    }

    @Test
    fun `11 own LockOverlayActivity and Launcher immediately stop blocked app foreground session and are excluded`() {
        val windowStart = 1_000_000L
        val windowEnd = 2_000_000L

        // User opens Instagram at +10s
        // At +12s, More2Day's LockOverlayActivity launches on top of Instagram
        // At +18s, User presses "Close" in LockOverlayActivity, returning to NexusLauncher
        // At +25s, Screen turns off
        val events = listOf(
            RawUsageEvent(windowStart, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(windowStart, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(windowStart + 10_000L, UsageEventType.ACTIVITY_RESUMED, "com.instagram.android", "MainActivity", 2),
            RawUsageEvent(windowStart + 12_000L, UsageEventType.ACTIVITY_RESUMED, "com.more2day.app", "com.example.ui.screens.LockOverlayActivity", 3),
            RawUsageEvent(windowStart + 18_000L, UsageEventType.ACTIVITY_RESUMED, "com.google.android.apps.nexuslauncher", "Launcher", 4),
            RawUsageEvent(windowStart + 25_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 5)
        )

        val result = UsageSessionReconstructor.reconstruct(
            rawEvents = events,
            queryLookbackStartMs = windowStart,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = berlinZone,
            packageClassifier = testClassifier
        )

        // Instagram was only in foreground for 2 seconds [10s..12s) before LockOverlay intercepted it
        assertEquals(2_000L, result.appForegroundDurationMap["com.instagram.android"])
        // Neither More2Day LockOverlay nor NexusLauncher are counted as app foreground time
        assertFalse(result.appForegroundDurationMap.containsKey("com.more2day.app"))
        assertFalse(result.appForegroundDurationMap.containsKey("com.google.android.apps.nexuslauncher"))
        assertEquals(2_000L, result.totalAppForegroundDurationMs)
        // Unlocked device time includes the full 25 seconds [0..25s)
        assertEquals(25_000L, result.unlockedDeviceDurationMs)
    }

    @Test
    fun `12 equal timestamps preserve original sequence order and exact duplicates are ignored`() {
        val windowStart = 1_000_000L
        val windowEnd = 2_000_000L

        // At exact same timestamp +10s: App A pauses (seq 2) and App B resumes (seq 3), plus duplicate App B resume (seq 4)
        val events = listOf(
            RawUsageEvent(windowStart, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(windowStart, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(windowStart, UsageEventType.ACTIVITY_RESUMED, "com.app.a", "MainActivity", 2),
            RawUsageEvent(windowStart + 10_000L, UsageEventType.ACTIVITY_PAUSED, "com.app.a", "MainActivity", 3),
            RawUsageEvent(windowStart + 10_000L, UsageEventType.ACTIVITY_RESUMED, "com.app.b", "MainActivity", 4),
            RawUsageEvent(windowStart + 10_000L, UsageEventType.ACTIVITY_RESUMED, "com.app.b", "MainActivity", 5), // duplicate
            RawUsageEvent(windowStart + 35_000L, UsageEventType.ACTIVITY_PAUSED, "com.app.b", "MainActivity", 6)
        )

        val result = UsageSessionReconstructor.reconstruct(
            rawEvents = events,
            queryLookbackStartMs = windowStart,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = berlinZone,
            packageClassifier = testClassifier
        )

        assertEquals(10_000L, result.appForegroundDurationMap["com.app.a"])
        assertEquals(25_000L, result.appForegroundDurationMap["com.app.b"])
    }

    @Test
    fun `13 checkpoint restoration reconstructs ongoing session across window start when lookback has no events`() {
        val windowStart = 5_000_000L
        val windowEnd = 6_000_000L

        // Checkpoint recorded 5 minutes before windowStart shows user was actively watching YouTube on unlocked screen
        val checkpoint = ReconstructionCheckpointState(
            timestampMs = windowStart - 300_000L,
            screenInteractive = true,
            keyguardShowing = false,
            activeForegroundPackage = "com.youtube.android",
            activeForegroundClass = "WatchActivity"
        )

        // Only event inside the window is PAUSE at +120s
        val events = listOf(
            RawUsageEvent(windowStart + 120_000L, UsageEventType.ACTIVITY_PAUSED, "com.youtube.android", "WatchActivity", 0),
            RawUsageEvent(windowStart + 120_000L, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 1)
        )

        val result = UsageSessionReconstructor.reconstruct(
            rawEvents = events,
            queryLookbackStartMs = checkpoint.timestampMs,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = berlinZone,
            initialCheckpoint = checkpoint,
            packageClassifier = testClassifier
        )

        assertEquals(MeasurementStatus.VALID_USAGE, result.status)
        // Clipped to [windowStart, windowStart + 120s) = 120_000 ms
        assertEquals(120_000L, result.appForegroundDurationMap["com.youtube.android"])
    }

    @Test
    fun `14 hourly distribution splits cross-hour session into exact hour buckets`() {
        val zdtStart = ZonedDateTime.of(2026, 11, 10, 14, 45, 0, 0, berlinZone)
        val zdtEnd = ZonedDateTime.of(2026, 11, 10, 15, 20, 0, 0, berlinZone)
        val dayWindow = UsageWindowBounds.getDayWindow(LocalDate.of(2026, 11, 10), berlinZone)

        val startMs = zdtStart.toInstant().toEpochMilli()
        val endMs = zdtEnd.toInstant().toEpochMilli()

        val events = listOf(
            RawUsageEvent(startMs, UsageEventType.SCREEN_INTERACTIVE, sequenceIndex = 0),
            RawUsageEvent(startMs, UsageEventType.KEYGUARD_HIDDEN, sequenceIndex = 1),
            RawUsageEvent(startMs, UsageEventType.ACTIVITY_RESUMED, "com.anki.droid", "StudyActivity", 2),
            RawUsageEvent(endMs, UsageEventType.ACTIVITY_PAUSED, "com.anki.droid", "StudyActivity", 3),
            RawUsageEvent(endMs, UsageEventType.SCREEN_NON_INTERACTIVE, sequenceIndex = 4)
        )

        val result = UsageSessionReconstructor.reconstruct(
            rawEvents = events,
            queryLookbackStartMs = dayWindow.startEpochMs,
            windowStartMs = dayWindow.startEpochMs,
            windowEndMs = dayWindow.endEpochMs,
            zoneId = berlinZone,
            packageClassifier = testClassifier
        )

        // 14:00..15:00 bucket gets 15 minutes (900_000 ms)
        assertEquals(15 * 60_000L, result.hourlyAppForegroundMs[14])
        // 15:00..16:00 bucket gets 20 minutes (1_200_000 ms)
        assertEquals(20 * 60_000L, result.hourlyAppForegroundMs[15])
    }
}
