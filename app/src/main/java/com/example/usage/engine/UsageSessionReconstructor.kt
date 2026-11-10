package com.example.usage.engine

import com.example.usage.model.ExcludedIntervalRecord
import com.example.usage.model.ExclusionReason
import com.example.usage.model.MeasurementStatus
import com.example.usage.model.RawUsageEvent
import com.example.usage.model.ReconstructedInterval
import com.example.usage.model.ReconstructionCheckpointState
import com.example.usage.model.ReconstructionResult
import com.example.usage.model.SessionType
import com.example.usage.model.TimeInterval
import com.example.usage.model.UsageDiagnosticsReport
import com.example.usage.model.UsageEventType
import java.time.ZoneId

/**
 * Callback interface to classify packages during reconstruction without coupling to Android's PackageManager.
 */
fun interface PackageClassifier {
    /**
     * Returns `null` if [packageName] is a valid trackable user app,
     * or the specific [ExclusionReason] if it must be excluded from user app foreground time.
     */
    fun getExclusionReason(packageName: String, className: String): ExclusionReason?
}

/**
 * Deterministic, UI-independent state machine that reconstructs device interactive time,
 * unlocked device time, per-app foreground time, and optional visible time from raw [RawUsageEvent] streams.
 */
object UsageSessionReconstructor {

    /**
     * Maximum plausible duration for an unclosed activity session before a `DEVICE_STARTUP` event
     * when no `DEVICE_SHUTDOWN` event was recorded (e.g. battery died or kernel crash).
     * Never count blind multi-hour gaps across ungraceful reboots.
     */
    private const val MAX_UNCLOSED_PRE_STARTUP_GAP_MS: Long = 5L * 60L * 1000L // 5 minutes

    /**
     * Merges overlapping or directly adjacent half-open intervals `[start, end)`.
     */
    fun mergeIntervals(intervals: List<TimeInterval>): List<TimeInterval> {
        val valid = intervals.filter { it.endEpochMs > it.startEpochMs }
        if (valid.size <= 1) return valid
        val sorted = valid.sortedWith(compareBy<TimeInterval> { it.startEpochMs }.thenBy { it.endEpochMs })
        val merged = ArrayList<TimeInterval>(sorted.size)
        var current = sorted[0]
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.startEpochMs <= current.endEpochMs) {
                if (next.endEpochMs > current.endEpochMs) {
                    current = TimeInterval(current.startEpochMs, next.endEpochMs)
                }
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    /**
     * Reconstructs usage intervals for the target half-open window `[windowStartMs, windowEndMs)`.
     *
     * @param rawEvents Events covering `[queryLookbackStartMs, windowEndMs)`.
     * @param queryLookbackStartMs Start of the event query (typically `windowStartMs - lookback` or checkpoint timestamp).
     * @param windowStartMs Inclusive start of the target evaluation window.
     * @param windowEndMs Exclusive end of the target evaluation window.
     * @param zoneId Timezone used for windowing and hourly distribution.
     * @param initialCheckpoint Optional checkpoint state at or before `windowStartMs`.
     * @param packageClassifier Classifier determining whether a package/activity is a trackable user app or excluded (Launcher, SystemUI, IME, LockOverlay).
     * @param projectOngoingAtWindowEnd If `true`, open sessions at `windowEndMs` are projected up to `windowEndMs` with `isOngoingProjection = true`.
     */
    fun reconstruct(
        rawEvents: List<RawUsageEvent>,
        queryLookbackStartMs: Long,
        windowStartMs: Long,
        windowEndMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        initialCheckpoint: ReconstructionCheckpointState? = null,
        packageClassifier: PackageClassifier = PackageClassifier { _, _ -> null },
        projectOngoingAtWindowEnd: Boolean = true
    ): ReconstructionResult {
        if (windowEndMs <= windowStartMs) {
            val emptyDiag = UsageDiagnosticsReport(
                queryLookbackStartMs = queryLookbackStartMs,
                targetWindowStartMs = windowStartMs,
                targetWindowEndMs = windowEndMs,
                zoneId = zoneId.id,
                status = MeasurementStatus.VALID_ZERO,
                rawEventCount = rawEvents.size,
                rawEventsSample = emptyList(),
                reconstructedIntervals = emptyList(),
                excludedIntervals = emptyList(),
                unroundedAppForegroundMs = emptyMap(),
                unroundedUnlockedDeviceMs = 0L,
                unroundedInteractiveDeviceMs = 0L,
                ongoingProjectionPackage = null,
                ongoingProjectionDurationMs = 0L
            )
            return ReconstructionResult(
                windowStartMs = windowStartMs,
                windowEndMs = windowEndMs,
                zoneId = zoneId.id,
                status = MeasurementStatus.VALID_ZERO,
                unlockedDeviceDurationMs = 0L,
                interactiveDeviceDurationMs = 0L,
                totalAppForegroundDurationMs = 0L,
                appForegroundDurationMap = emptyMap(),
                finalizedAppForegroundDurationMap = emptyMap(),
                appVisibleDurationMap = emptyMap(),
                hourlyAppForegroundMs = emptyMap(),
                finalizedIntervals = emptyList(),
                ongoingProjections = emptyList(),
                endCheckpoint = initialCheckpoint,
                diagnostics = emptyDiag
            )
        }

        // 1. Deterministic sorting: primary by timestampMs ascending, secondary by original sequenceIndex ascending.
        // Filter out events strictly after windowEndMs, and deduplicate exact consecutive duplicates.
        val sortedEvents = rawEvents
            .asSequence()
            .filter { it.timestampMs <= windowEndMs && it.eventType != UsageEventType.OTHER }
            .sortedWith(compareBy<RawUsageEvent> { it.timestampMs }.thenBy { it.sequenceIndex })
            .toList()

        val deduplicatedEvents = ArrayList<RawUsageEvent>(sortedEvents.size)
        for (ev in sortedEvents) {
            val prev = deduplicatedEvents.lastOrNull()
            if (prev != null &&
                prev.timestampMs == ev.timestampMs &&
                prev.eventType == ev.eventType &&
                prev.packageName == ev.packageName &&
                prev.className == ev.className
            ) {
                continue
            }
            deduplicatedEvents.add(ev)
        }

        // State variables
        var screenInteractive: Boolean? = initialCheckpoint?.screenInteractive
        var keyguardShowing: Boolean? = initialCheckpoint?.keyguardShowing
        var interactiveSegmentStartMs: Long? = null
        var unlockedSegmentStartMs: Long? = null

        // Active resumed activities per package: packageName -> MutableSet<classNameKey>
        val resumedActivitiesByPkg = LinkedHashMap<String, LinkedHashSet<String>>()
        // Active visible activities (between RESUMED and STOPPED) per package
        val visibleActivitiesByPkg = LinkedHashMap<String, LinkedHashSet<String>>()

        // Currently open foreground app session (only while device is interactive + unlocked)
        var currentForegroundPkg: String? = null
        var currentForegroundClass: String? = null
        var currentForegroundStartMs: Long? = null

        // Currently open visible app sessions: packageName -> startEpochMs
        val currentVisibleStartByPkg = LinkedHashMap<String, Long>()

        // Track last event timestamp seen before a potential ungraceful DEVICE_STARTUP
        var lastEventTimestampMs: Long? = initialCheckpoint?.timestampMs
        var hasShutdownClosedState = false
        var historyIncompleteDetected = false

        // Seed from initialCheckpoint if valid and within reasonable range
        if (initialCheckpoint != null && initialCheckpoint.timestampMs <= windowStartMs) {
            val seedTime = initialCheckpoint.timestampMs
            if (screenInteractive == true) {
                interactiveSegmentStartMs = seedTime
                if (keyguardShowing != true) {
                    unlockedSegmentStartMs = seedTime
                }
            }
            val seedPkg = initialCheckpoint.activeForegroundPackage
            if (!seedPkg.isNullOrBlank()) {
                val clsKey = initialCheckpoint.activeForegroundClass?.ifBlank { "*" } ?: "*"
                resumedActivitiesByPkg.getOrPut(seedPkg) { LinkedHashSet() }.add(clsKey)
                visibleActivitiesByPkg.getOrPut(seedPkg) { LinkedHashSet() }.add(clsKey)
                if (screenInteractive == true && keyguardShowing != true) {
                    currentForegroundPkg = seedPkg
                    currentForegroundClass = clsKey
                    currentForegroundStartMs = seedTime
                    currentVisibleStartByPkg[seedPkg] = seedTime
                }
            }
        }

        val rawReconstructedIntervals = ArrayList<ReconstructedInterval>()
        val excludedRecords = ArrayList<ExcludedIntervalRecord>()

        fun isDeviceUnlockedNow(): Boolean = (screenInteractive != false) && (keyguardShowing != true)

        fun closeCurrentForegroundApp(atTimestampMs: Long, isOngoing: Boolean = false) {
            val pkg = currentForegroundPkg ?: return
            val cls = currentForegroundClass ?: ""
            val startMs = currentForegroundStartMs ?: return
            currentForegroundPkg = null
            currentForegroundClass = null
            currentForegroundStartMs = null

            if (atTimestampMs <= startMs) return

            val exclusion = packageClassifier.getExclusionReason(pkg, cls)
            if (exclusion != null) {
                // Only record diagnostic exclusion if it overlaps with target window
                if (atTimestampMs > windowStartMs && startMs < windowEndMs) {
                    excludedRecords.add(
                        ExcludedIntervalRecord(
                            reason = exclusion,
                            packageName = pkg,
                            className = cls,
                            timestampOrStartMs = maxOf(startMs, windowStartMs),
                            endEpochMs = minOf(atTimestampMs, windowEndMs),
                            details = "Excluded package foreground interval"
                        )
                    )
                }
                return
            }

            val clipped = TimeInterval(startMs, atTimestampMs).clipTo(windowStartMs, windowEndMs)
            if (clipped != null) {
                rawReconstructedIntervals.add(
                    ReconstructedInterval(
                        sessionType = SessionType.APP_FOREGROUND,
                        packageName = pkg,
                        className = cls,
                        startEpochMs = clipped.startEpochMs,
                        endEpochMs = clipped.endEpochMs,
                        isOngoingProjection = isOngoing
                    )
                )
            }
        }

        fun closeVisiblePackage(pkg: String, atTimestampMs: Long, isOngoing: Boolean = false) {
            val startMs = currentVisibleStartByPkg.remove(pkg) ?: return
            if (atTimestampMs <= startMs) return
            val exclusion = packageClassifier.getExclusionReason(pkg, "")
            if (exclusion != null) return
            val clipped = TimeInterval(startMs, atTimestampMs).clipTo(windowStartMs, windowEndMs)
            if (clipped != null) {
                rawReconstructedIntervals.add(
                    ReconstructedInterval(
                        sessionType = SessionType.APP_VISIBLE,
                        packageName = pkg,
                        startEpochMs = clipped.startEpochMs,
                        endEpochMs = clipped.endEpochMs,
                        isOngoingProjection = isOngoing
                    )
                )
            }
        }

        fun closeAllVisiblePackages(atTimestampMs: Long, isOngoing: Boolean = false) {
            val keys = currentVisibleStartByPkg.keys.toList()
            for (pkg in keys) {
                closeVisiblePackage(pkg, atTimestampMs, isOngoing)
            }
        }

        fun closeInteractiveSegment(atTimestampMs: Long, isOngoing: Boolean = false) {
            val startMs = interactiveSegmentStartMs ?: return
            interactiveSegmentStartMs = null
            if (atTimestampMs <= startMs) return
            val clipped = TimeInterval(startMs, atTimestampMs).clipTo(windowStartMs, windowEndMs)
            if (clipped != null) {
                rawReconstructedIntervals.add(
                    ReconstructedInterval(
                        sessionType = SessionType.DEVICE_INTERACTIVE,
                        packageName = "android.device.interactive",
                        startEpochMs = clipped.startEpochMs,
                        endEpochMs = clipped.endEpochMs,
                        isOngoingProjection = isOngoing
                    )
                )
            }
        }

        fun closeUnlockedSegment(atTimestampMs: Long, isOngoing: Boolean = false) {
            val startMs = unlockedSegmentStartMs ?: return
            unlockedSegmentStartMs = null
            if (atTimestampMs <= startMs) return
            val clipped = TimeInterval(startMs, atTimestampMs).clipTo(windowStartMs, windowEndMs)
            if (clipped != null) {
                rawReconstructedIntervals.add(
                    ReconstructedInterval(
                        sessionType = SessionType.DEVICE_UNLOCKED,
                        packageName = "android.device.unlocked",
                        startEpochMs = clipped.startEpochMs,
                        endEpochMs = clipped.endEpochMs,
                        isOngoingProjection = isOngoing
                    )
                )
            }
        }

        fun syncDeviceAndForegroundTransitions(atTimestampMs: Long) {
            val nowInteractive = (screenInteractive == true)
            val nowUnlocked = (screenInteractive == true) && (keyguardShowing != true)

            if (nowInteractive) {
                if (interactiveSegmentStartMs == null) {
                    interactiveSegmentStartMs = atTimestampMs
                }
            } else {
                closeInteractiveSegment(atTimestampMs)
            }

            if (nowUnlocked) {
                if (unlockedSegmentStartMs == null) {
                    unlockedSegmentStartMs = atTimestampMs
                }
                // If an app is already in resumed state when unlocking/turning screen on, start its foreground interval at unlock time
                if (currentForegroundPkg == null && resumedActivitiesByPkg.isNotEmpty()) {
                    val topEntry = resumedActivitiesByPkg.entries.lastOrNull { it.value.isNotEmpty() }
                    if (topEntry != null) {
                        currentForegroundPkg = topEntry.key
                        currentForegroundClass = topEntry.value.lastOrNull() ?: ""
                        currentForegroundStartMs = atTimestampMs
                    }
                }
                for ((pkg, classes) in visibleActivitiesByPkg) {
                    if (classes.isNotEmpty() && !currentVisibleStartByPkg.containsKey(pkg)) {
                        currentVisibleStartByPkg[pkg] = atTimestampMs
                    }
                }
            } else {
                closeUnlockedSegment(atTimestampMs)
                closeCurrentForegroundApp(atTimestampMs)
                closeAllVisiblePackages(atTimestampMs)
            }
        }

        // Process event stream
        for (ev in deduplicatedEvents) {
            val t = ev.timestampMs
            val pkg = ev.packageName
            val clsKey = ev.className.ifBlank { "*" }

            when (ev.eventType) {
                UsageEventType.SCREEN_INTERACTIVE -> {
                    hasShutdownClosedState = false
                    screenInteractive = true
                    if (keyguardShowing == null) {
                        keyguardShowing = false
                    }
                    syncDeviceAndForegroundTransitions(t)
                }

                UsageEventType.SCREEN_NON_INTERACTIVE -> {
                    // If screen was unknown prior to turning off, do NOT invent an interactive session from midnight
                    if (screenInteractive == null) {
                        if (t > windowStartMs && initialCheckpoint == null) {
                            historyIncompleteDetected = true
                        }
                    }
                    screenInteractive = false
                    syncDeviceAndForegroundTransitions(t)
                }

                UsageEventType.KEYGUARD_SHOWN -> {
                    keyguardShowing = true
                    if (screenInteractive == true && interactiveSegmentStartMs != null) {
                        // Record that subsequent time while screen is interactive is lockscreen time
                        if (t in windowStartMs..windowEndMs) {
                            excludedRecords.add(
                                ExcludedIntervalRecord(
                                    reason = ExclusionReason.KEYGUARD_LOCKED,
                                    timestampOrStartMs = t,
                                    details = "Keyguard shown"
                                )
                            )
                        }
                    }
                    syncDeviceAndForegroundTransitions(t)
                }

                UsageEventType.KEYGUARD_HIDDEN -> {
                    hasShutdownClosedState = false
                    if (screenInteractive == null) {
                        screenInteractive = true
                    }
                    keyguardShowing = false
                    syncDeviceAndForegroundTransitions(t)
                }

                UsageEventType.DEVICE_SHUTDOWN -> {
                    // Close all active segments at the shutdown timestamp
                    closeCurrentForegroundApp(t)
                    closeAllVisiblePackages(t)
                    closeUnlockedSegment(t)
                    closeInteractiveSegment(t)
                    resumedActivitiesByPkg.clear()
                    visibleActivitiesByPkg.clear()
                    screenInteractive = false
                    keyguardShowing = false
                    hasShutdownClosedState = true
                }

                UsageEventType.DEVICE_STARTUP -> {
                    // If sessions were still open because DEVICE_SHUTDOWN was missing (crash / battery empty),
                    // never count the power-off downtime up to DEVICE_STARTUP!
                    val hadOpenState = currentForegroundPkg != null ||
                        unlockedSegmentStartMs != null ||
                        interactiveSegmentStartMs != null

                    if (hadOpenState && !hasShutdownClosedState) {
                        val safeCutoff = lastEventTimestampMs ?: t
                        val openStart = currentForegroundStartMs ?: unlockedSegmentStartMs ?: interactiveSegmentStartMs ?: safeCutoff
                        if (t - safeCutoff > MAX_UNCLOSED_PRE_STARTUP_GAP_MS) {
                            historyIncompleteDetected = true
                            excludedRecords.add(
                                ExcludedIntervalRecord(
                                    reason = ExclusionReason.UNGRACEFUL_SHUTDOWN_GAP,
                                    packageName = currentForegroundPkg ?: "",
                                    timestampOrStartMs = safeCutoff,
                                    endEpochMs = t,
                                    details = "DEVICE_STARTUP without prior DEVICE_SHUTDOWN; capped at last known event"
                                )
                            )
                        }
                        val closeAt = maxOf(openStart, safeCutoff)
                        closeCurrentForegroundApp(closeAt)
                        closeAllVisiblePackages(closeAt)
                        closeUnlockedSegment(closeAt)
                        closeInteractiveSegment(closeAt)
                    }
                    resumedActivitiesByPkg.clear()
                    visibleActivitiesByPkg.clear()
                    screenInteractive = true
                    keyguardShowing = false
                    hasShutdownClosedState = false
                    syncDeviceAndForegroundTransitions(t)
                }

                UsageEventType.ACTIVITY_RESUMED -> {
                    if (pkg.isNotBlank()) {
                        hasShutdownClosedState = false
                        // If no screen/keyguard events preceded an ACTIVITY_RESUMED, an activity resuming while user
                        // interacts implies the device is interactive and unlocked unless keyguard is explicitly known to be shown.
                        if (screenInteractive == null) {
                            screenInteractive = true
                            if (interactiveSegmentStartMs == null) {
                                interactiveSegmentStartMs = t
                            }
                        }
                        if (keyguardShowing == null) {
                            keyguardShowing = false
                        }
                        if (isDeviceUnlockedNow() && unlockedSegmentStartMs == null) {
                            unlockedSegmentStartMs = t
                        }

                        // Single-foreground rule across distinct packages:
                        // When another package moves to foreground (including Launcher or our own LockOverlayActivity),
                        // close the previous foreground package's interval at t even if its PAUSED event arrives late.
                        if (currentForegroundPkg != null && currentForegroundPkg != pkg) {
                            closeCurrentForegroundApp(t)
                            // Also clear stale resumed activities of other packages that didn't emit PAUSED before transition
                            val otherKeys = resumedActivitiesByPkg.keys.filter { it != pkg }
                            for (other in otherKeys) {
                                resumedActivitiesByPkg.remove(other)
                            }
                        }

                        val pkgResumed = resumedActivitiesByPkg.getOrPut(pkg) { LinkedHashSet() }
                        pkgResumed.remove(clsKey) // refresh insertion order so newest resumed activity is last
                        pkgResumed.add(clsKey)

                        val pkgVisible = visibleActivitiesByPkg.getOrPut(pkg) { LinkedHashSet() }
                        pkgVisible.add(clsKey)

                        if (isDeviceUnlockedNow()) {
                            if (currentForegroundPkg == null) {
                                currentForegroundPkg = pkg
                                currentForegroundClass = clsKey
                                currentForegroundStartMs = t
                            } else if (currentForegroundPkg == pkg) {
                                // Internal activity transition within the same package: keep session continuous!
                                currentForegroundClass = clsKey
                            }

                            if (!currentVisibleStartByPkg.containsKey(pkg)) {
                                currentVisibleStartByPkg[pkg] = t
                            }
                        } else {
                            // Activity resumed while screen off or keyguard locked
                            val reason = if (screenInteractive == false) ExclusionReason.SCREEN_OFF else ExclusionReason.KEYGUARD_LOCKED
                            if (t in windowStartMs..windowEndMs) {
                                excludedRecords.add(
                                    ExcludedIntervalRecord(
                                        reason = reason,
                                        packageName = pkg,
                                        className = clsKey,
                                        timestampOrStartMs = t,
                                        details = "ACTIVITY_RESUMED ignored while device not unlocked"
                                    )
                                )
                            }
                        }
                    }
                }

                UsageEventType.ACTIVITY_PAUSED -> {
                    if (pkg.isNotBlank()) {
                        val pkgResumed = resumedActivitiesByPkg[pkg]
                        val wasTracked = pkgResumed != null && (pkgResumed.remove(clsKey) || pkgResumed.remove("*"))

                        if (!wasTracked && currentForegroundPkg != pkg) {
                            // Orphan PAUSE without a known RESUME in lookback or window:
                            // NEVER invent a session starting at midnight!
                            if (t > windowStartMs && initialCheckpoint == null) {
                                val exclusion = packageClassifier.getExclusionReason(pkg, clsKey)
                                if (exclusion == null) {
                                    historyIncompleteDetected = true
                                    excludedRecords.add(
                                        ExcludedIntervalRecord(
                                            reason = ExclusionReason.ORPHAN_PAUSE_WITHOUT_RESUME,
                                            packageName = pkg,
                                            className = clsKey,
                                            timestampOrStartMs = windowStartMs,
                                            endEpochMs = t,
                                            details = "ACTIVITY_PAUSED without prior ACTIVITY_RESUMED; not counted"
                                        )
                                    )
                                }
                            }
                        } else {
                            // Only close the package's foreground session if NO other activity of this package remains RESUMED.
                            // This prevents late PAUSED events from a splashscreen or previous activity from cutting off the active activity.
                            if (pkgResumed.isNullOrEmpty()) {
                                resumedActivitiesByPkg.remove(pkg)
                                if (currentForegroundPkg == pkg) {
                                    closeCurrentForegroundApp(t)
                                }
                            } else {
                                // Another activity of the same package is still resumed
                                currentForegroundClass = pkgResumed.last()
                            }
                        }
                    }
                }

                UsageEventType.ACTIVITY_STOPPED -> {
                    if (pkg.isNotBlank()) {
                        // ACTIVITY_STOPPED ends visibility of this specific activity class.
                        // Never blindly close the whole app if another activity of the same package is still RESUMED/VISIBLE.
                        val pkgVisible = visibleActivitiesByPkg[pkg]
                        if (pkgVisible != null) {
                            pkgVisible.remove(clsKey)
                            pkgVisible.remove("*")
                            if (pkgVisible.isEmpty()) {
                                visibleActivitiesByPkg.remove(pkg)
                                closeVisiblePackage(pkg, t)
                            }
                        }

                        // Defensive fallback: if Android omitted ACTIVITY_PAUSED for this exact class and no other activity
                        // in this package is resumed, close foreground at STOPPED.
                        val pkgResumed = resumedActivitiesByPkg[pkg]
                        if (pkgResumed != null && pkgResumed.contains(clsKey)) {
                            pkgResumed.remove(clsKey)
                            if (pkgResumed.isEmpty()) {
                                resumedActivitiesByPkg.remove(pkg)
                                if (currentForegroundPkg == pkg) {
                                    closeCurrentForegroundApp(t)
                                }
                            }
                        }
                    }
                }

                UsageEventType.OTHER -> Unit
            }

            lastEventTimestampMs = t
        }

        // Capture checkpoint state at windowEndMs BEFORE closing ongoing projections
        val endCheckpoint = ReconstructionCheckpointState(
            timestampMs = windowEndMs,
            screenInteractive = screenInteractive,
            keyguardShowing = keyguardShowing,
            activeForegroundPackage = currentForegroundPkg,
            activeForegroundClass = currentForegroundClass
        )

        // Handle ongoing sessions at windowEndMs
        if (projectOngoingAtWindowEnd) {
            if (currentForegroundPkg != null) {
                closeCurrentForegroundApp(windowEndMs, isOngoing = true)
            }
            closeAllVisiblePackages(windowEndMs, isOngoing = true)
            if (unlockedSegmentStartMs != null) {
                closeUnlockedSegment(windowEndMs, isOngoing = true)
            }
            if (interactiveSegmentStartMs != null) {
                closeInteractiveSegment(windowEndMs, isOngoing = true)
            }
        }

        // Aggregate and merge overlapping intervals per package/sessionType before summing!
        val finalizedIntervals = rawReconstructedIntervals.filter { !it.isOngoingProjection && it.durationMs > 0L }
        val ongoingProjections = rawReconstructedIntervals.filter { it.isOngoingProjection && it.durationMs > 0L }

        // 1. Device Interactive merged duration
        val interactiveIntervals = mergeIntervals(
            rawReconstructedIntervals
                .filter { it.sessionType == SessionType.DEVICE_INTERACTIVE }
                .map { TimeInterval(it.startEpochMs, it.endEpochMs) }
        )
        val interactiveDeviceDurationMs = interactiveIntervals.sumOf { it.durationMs }

        // 2. Device Unlocked merged duration
        val unlockedIntervals = mergeIntervals(
            rawReconstructedIntervals
                .filter { it.sessionType == SessionType.DEVICE_UNLOCKED }
                .map { TimeInterval(it.startEpochMs, it.endEpochMs) }
        )
        val unlockedDeviceDurationMs = unlockedIntervals.sumOf { it.durationMs }

        // 3. Per-package App Foreground merged duration (finalized + ongoing) & hourly breakdown
        val appForegroundByPkg = LinkedHashMap<String, Long>()
        val hourlyMap = LinkedHashMap<Int, Long>()
        val foregroundGrouped = rawReconstructedIntervals
            .filter { it.sessionType == SessionType.APP_FOREGROUND }
            .groupBy { it.packageName }

        for ((pkg, pkgIntervals) in foregroundGrouped) {
            val merged = mergeIntervals(pkgIntervals.map { TimeInterval(it.startEpochMs, it.endEpochMs) })
            val totalPkgMs = merged.sumOf { it.durationMs }
            if (totalPkgMs > 0L) {
                appForegroundByPkg[pkg] = totalPkgMs
                for (interval in merged) {
                    UsageWindowBounds.accumulateHourlyDistribution(
                        startEpochMs = interval.startEpochMs,
                        endEpochMs = interval.endEpochMs,
                        zoneId = zoneId,
                        targetHourlyMap = hourlyMap
                    )
                }
            }
        }

        // 4. Per-package Finalized-only App Foreground duration
        val finalizedForegroundByPkg = LinkedHashMap<String, Long>()
        val finalizedGrouped = finalizedIntervals
            .filter { it.sessionType == SessionType.APP_FOREGROUND }
            .groupBy { it.packageName }
        for ((pkg, pkgIntervals) in finalizedGrouped) {
            val merged = mergeIntervals(pkgIntervals.map { TimeInterval(it.startEpochMs, it.endEpochMs) })
            val totalPkgMs = merged.sumOf { it.durationMs }
            if (totalPkgMs > 0L) {
                finalizedForegroundByPkg[pkg] = totalPkgMs
            }
        }

        // 5. Per-package App Visible duration
        val appVisibleByPkg = LinkedHashMap<String, Long>()
        val visibleGrouped = rawReconstructedIntervals
            .filter { it.sessionType == SessionType.APP_VISIBLE }
            .groupBy { it.packageName }
        for ((pkg, pkgIntervals) in visibleGrouped) {
            val merged = mergeIntervals(pkgIntervals.map { TimeInterval(it.startEpochMs, it.endEpochMs) })
            val totalPkgMs = merged.sumOf { it.durationMs }
            if (totalPkgMs > 0L) {
                appVisibleByPkg[pkg] = totalPkgMs
            }
        }

        val totalAppForegroundDurationMs = appForegroundByPkg.values.sum()

        val status = when {
            historyIncompleteDetected -> MeasurementStatus.HISTORY_INCOMPLETE
            totalAppForegroundDurationMs > 0L || unlockedDeviceDurationMs > 0L -> MeasurementStatus.VALID_USAGE
            else -> MeasurementStatus.VALID_ZERO
        }

        val ongoingFg = ongoingProjections.firstOrNull { it.sessionType == SessionType.APP_FOREGROUND }
        val diagnostics = UsageDiagnosticsReport(
            queryLookbackStartMs = queryLookbackStartMs,
            targetWindowStartMs = windowStartMs,
            targetWindowEndMs = windowEndMs,
            zoneId = zoneId.id,
            status = status,
            rawEventCount = deduplicatedEvents.size,
            rawEventsSample = deduplicatedEvents.takeLast(200),
            reconstructedIntervals = rawReconstructedIntervals,
            excludedIntervals = excludedRecords,
            unroundedAppForegroundMs = appForegroundByPkg,
            unroundedUnlockedDeviceMs = unlockedDeviceDurationMs,
            unroundedInteractiveDeviceMs = interactiveDeviceDurationMs,
            ongoingProjectionPackage = ongoingFg?.packageName,
            ongoingProjectionDurationMs = ongoingFg?.durationMs ?: 0L
        )

        return ReconstructionResult(
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
            zoneId = zoneId.id,
            status = status,
            unlockedDeviceDurationMs = unlockedDeviceDurationMs,
            interactiveDeviceDurationMs = interactiveDeviceDurationMs,
            totalAppForegroundDurationMs = totalAppForegroundDurationMs,
            appForegroundDurationMap = appForegroundByPkg,
            finalizedAppForegroundDurationMap = finalizedForegroundByPkg,
            appVisibleDurationMap = appVisibleByPkg,
            hourlyAppForegroundMs = hourlyMap,
            finalizedIntervals = finalizedIntervals,
            ongoingProjections = ongoingProjections,
            endCheckpoint = endCheckpoint,
            diagnostics = diagnostics
        )
    }
}
