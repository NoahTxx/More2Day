package com.example.usage.repository

import com.example.data.ReconstructedUsageSessionEntity
import com.example.data.UsageImportCheckpointEntity
import com.example.data.UsageTrackingDao
import com.example.usage.engine.PackageClassifier
import com.example.usage.engine.UsageSessionReconstructor
import com.example.usage.engine.UsageWindowBounds
import com.example.usage.model.MeasurementStatus
import com.example.usage.model.ReconstructedInterval
import com.example.usage.model.ReconstructionCheckpointState
import com.example.usage.model.ReconstructionResult
import com.example.usage.model.SessionType
import com.example.usage.model.TimeInterval
import com.example.usage.model.UsageDiagnosticsReport
import com.example.usage.source.UsageEventSource
import java.time.ZoneId

/**
 * Coordinates event retrieval, lookback/checkpoint resolution, deterministic session reconstruction,
 * and idempotent local Room persistence.
 */
class UsageTrackingRepository(
    private val usageTrackingDao: UsageTrackingDao,
    private val eventSource: UsageEventSource
) {

    /**
     * Evaluates and persists usage for `[windowStartMs, windowEndMs)` idempotently.
     *
     * Strategy:
     * 1. Queries raw events starting at `windowStartMs - lookbackMs` up to `windowEndMs`.
     * 2. If a stored checkpoint exists at or before `queryLookbackStartMs`, seeds the state machine with it.
     * 3. Runs [UsageSessionReconstructor.reconstruct] on the raw events.
     * 4. If raw events are present for the window, replaces finalized intervals overlapping `[windowStartMs, windowEndMs)`
     *    in a single database transaction (guaranteeing idempotency across repeated calls).
     * 5. If raw events have already expired for older parts of a multi-day window (e.g. 7-day view where
     *    early days have no raw events left), merges persisted finalized intervals from the database with
     *    newly reconstructed intervals without double-counting any overlapping time range.
     */
    @Synchronized
    fun evaluateAndSyncWindow(
        windowStartMs: Long,
        windowEndMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        lookbackMs: Long = UsageWindowBounds.DEFAULT_LOOKBACK_MS,
        projectOngoingAtWindowEnd: Boolean = true
    ): ReconstructionResult {
        val queryLookbackStartMs = (windowStartMs - lookbackMs).coerceAtLeast(0L)
        val queryResponse = eventSource.queryRawEvents(queryLookbackStartMs, windowEndMs)

        if (queryResponse.availabilityStatus == MeasurementStatus.PERMISSION_MISSING ||
            queryResponse.availabilityStatus == MeasurementStatus.TEMPORARILY_UNAVAILABLE
        ) {
            val emptyDiag = UsageDiagnosticsReport(
                queryLookbackStartMs = queryLookbackStartMs,
                targetWindowStartMs = windowStartMs,
                targetWindowEndMs = windowEndMs,
                zoneId = zoneId.id,
                status = queryResponse.availabilityStatus,
                rawEventCount = 0,
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
                status = queryResponse.availabilityStatus,
                unlockedDeviceDurationMs = 0L,
                interactiveDeviceDurationMs = 0L,
                totalAppForegroundDurationMs = 0L,
                appForegroundDurationMap = emptyMap(),
                finalizedAppForegroundDurationMap = emptyMap(),
                appVisibleDurationMap = emptyMap(),
                hourlyAppForegroundMs = emptyMap(),
                finalizedIntervals = emptyList(),
                ongoingProjections = emptyList(),
                endCheckpoint = null,
                diagnostics = emptyDiag
            )
        }

        val classifier: PackageClassifier = eventSource.createPackageClassifier()
        val storedCheckpointEntity = usageTrackingDao.getCheckpointSync()
        val initialCheckpoint = if (storedCheckpointEntity != null &&
            storedCheckpointEntity.algorithmVersion == ReconstructionCheckpointState.CURRENT_ALGORITHM_VERSION &&
            storedCheckpointEntity.timestampMs <= queryLookbackStartMs
        ) {
            ReconstructionCheckpointState(
                timestampMs = storedCheckpointEntity.timestampMs,
                screenInteractive = storedCheckpointEntity.screenInteractive,
                keyguardShowing = storedCheckpointEntity.keyguardShowing,
                activeForegroundPackage = storedCheckpointEntity.activeForegroundPackage,
                activeForegroundClass = storedCheckpointEntity.activeForegroundClass,
                algorithmVersion = storedCheckpointEntity.algorithmVersion
            )
        } else {
            null
        }

        val freshReconstruction = UsageSessionReconstructor.reconstruct(
            rawEvents = queryResponse.events,
            queryLookbackStartMs = queryLookbackStartMs,
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
            zoneId = zoneId,
            initialCheckpoint = initialCheckpoint,
            packageClassifier = classifier,
            projectOngoingAtWindowEnd = projectOngoingAtWindowEnd
        )

        // Determine the earliest raw event timestamp available in the system response
        val earliestRawEventMs = queryResponse.events.minOfOrNull { it.timestampMs }

        if (earliestRawEventMs != null) {
            // Only overwrite DB sessions from max(windowStartMs, earliestRawEventMs) to windowEndMs
            // so we never wipe out older persisted days whose raw events Android has already purged.
            val replaceStartMs = maxOf(windowStartMs, earliestRawEventMs)
            val sessionsToPersist = freshReconstruction.finalizedIntervals
                .filter { it.endEpochMs > replaceStartMs }
                .mapNotNull { interval ->
                    val clippedStart = maxOf(interval.startEpochMs, replaceStartMs)
                    val clippedEnd = interval.endEpochMs
                    if (clippedEnd > clippedStart) {
                        ReconstructedUsageSessionEntity(
                            dateKey = UsageWindowBounds.getDateKeyForEpochMs(clippedStart, zoneId),
                            sessionType = interval.sessionType.name,
                            packageName = interval.packageName,
                            className = interval.className,
                            startEpochMs = clippedStart,
                            endEpochMs = clippedEnd,
                            durationMs = clippedEnd - clippedStart,
                            zoneId = zoneId.id,
                            measurementStatus = freshReconstruction.status.name,
                            algorithmVersion = ReconstructionCheckpointState.CURRENT_ALGORITHM_VERSION
                        )
                    } else null
                }

            usageTrackingDao.replaceSessionsInWindowSync(
                windowStartMs = replaceStartMs,
                windowEndMs = windowEndMs,
                newSessions = sessionsToPersist
            )

            // Save latest checkpoint if we reconstructed up to now
            freshReconstruction.endCheckpoint?.let { cp ->
                usageTrackingDao.upsertCheckpointSync(
                    UsageImportCheckpointEntity(
                        timestampMs = cp.timestampMs,
                        screenInteractive = cp.screenInteractive,
                        keyguardShowing = cp.keyguardShowing,
                        activeForegroundPackage = cp.activeForegroundPackage,
                        activeForegroundClass = cp.activeForegroundClass,
                        zoneId = zoneId.id,
                        algorithmVersion = cp.algorithmVersion
                    )
                )
            }
        }

        // Now combine any older persisted sessions in [windowStartMs, windowEndMs) with fresh intervals
        // and merge overlapping intervals per package/sessionType so there is zero double-counting.
        val persistedEntities = usageTrackingDao.getSessionsOverlappingWindowSync(windowStartMs, windowEndMs)
        if (persistedEntities.isEmpty()) {
            return freshReconstruction
        }

        val persistedAsIntervals = persistedEntities.mapNotNull { entity ->
            val sessionType = try {
                SessionType.valueOf(entity.sessionType)
            } catch (_: Exception) {
                return@mapNotNull null
            }
            val clipped = TimeInterval(entity.startEpochMs, entity.endEpochMs).clipTo(windowStartMs, windowEndMs)
                ?: return@mapNotNull null
            ReconstructedInterval(
                sessionType = sessionType,
                packageName = entity.packageName,
                className = entity.className,
                startEpochMs = clipped.startEpochMs,
                endEpochMs = clipped.endEpochMs,
                isOngoingProjection = false
            )
        }

        val combinedIntervals = persistedAsIntervals + freshReconstruction.finalizedIntervals + freshReconstruction.ongoingProjections

        // Re-aggregate merged intervals across persisted + fresh + ongoing
        val interactiveMerged = UsageSessionReconstructor.mergeIntervals(
            combinedIntervals
                .filter { it.sessionType == SessionType.DEVICE_INTERACTIVE }
                .map { TimeInterval(it.startEpochMs, it.endEpochMs) }
        )
        val interactiveDeviceDurationMs = interactiveMerged.sumOf { it.durationMs }

        val unlockedMerged = UsageSessionReconstructor.mergeIntervals(
            combinedIntervals
                .filter { it.sessionType == SessionType.DEVICE_UNLOCKED }
                .map { TimeInterval(it.startEpochMs, it.endEpochMs) }
        )
        val unlockedDeviceDurationMs = unlockedMerged.sumOf { it.durationMs }

        val appForegroundByPkg = LinkedHashMap<String, Long>()
        val hourlyMap = LinkedHashMap<Int, Long>()
        val fgGrouped = combinedIntervals
            .filter { it.sessionType == SessionType.APP_FOREGROUND }
            .groupBy { it.packageName }

        for ((pkg, list) in fgGrouped) {
            // Re-verify exclusion on persisted packages in case rules changed
            if (classifier.getExclusionReason(pkg, "") != null) continue
            val merged = UsageSessionReconstructor.mergeIntervals(list.map { TimeInterval(it.startEpochMs, it.endEpochMs) })
            val sumMs = merged.sumOf { it.durationMs }
            if (sumMs > 0L) {
                appForegroundByPkg[pkg] = sumMs
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

        val finalizedForegroundByPkg = LinkedHashMap<String, Long>()
        val finalizedCombined = persistedAsIntervals + freshReconstruction.finalizedIntervals
        val finalizedGrouped = finalizedCombined
            .filter { it.sessionType == SessionType.APP_FOREGROUND }
            .groupBy { it.packageName }
        for ((pkg, list) in finalizedGrouped) {
            if (classifier.getExclusionReason(pkg, "") != null) continue
            val merged = UsageSessionReconstructor.mergeIntervals(list.map { TimeInterval(it.startEpochMs, it.endEpochMs) })
            val sumMs = merged.sumOf { it.durationMs }
            if (sumMs > 0L) {
                finalizedForegroundByPkg[pkg] = sumMs
            }
        }

        val totalAppForegroundDurationMs = appForegroundByPkg.values.sum()
        val combinedStatus = when {
            freshReconstruction.status == MeasurementStatus.HISTORY_INCOMPLETE -> MeasurementStatus.HISTORY_INCOMPLETE
            totalAppForegroundDurationMs > 0L || unlockedDeviceDurationMs > 0L -> MeasurementStatus.VALID_USAGE
            else -> MeasurementStatus.VALID_ZERO
        }

        val updatedDiagnostics = freshReconstruction.diagnostics.copy(
            status = combinedStatus,
            unroundedAppForegroundMs = appForegroundByPkg,
            unroundedUnlockedDeviceMs = unlockedDeviceDurationMs,
            unroundedInteractiveDeviceMs = interactiveDeviceDurationMs
        )

        return freshReconstruction.copy(
            status = combinedStatus,
            unlockedDeviceDurationMs = unlockedDeviceDurationMs,
            interactiveDeviceDurationMs = interactiveDeviceDurationMs,
            totalAppForegroundDurationMs = totalAppForegroundDurationMs,
            appForegroundDurationMap = appForegroundByPkg,
            finalizedAppForegroundDurationMap = finalizedForegroundByPkg,
            hourlyAppForegroundMs = hourlyMap,
            diagnostics = updatedDiagnostics
        )
    }
}
