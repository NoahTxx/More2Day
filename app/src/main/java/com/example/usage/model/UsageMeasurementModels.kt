package com.example.usage.model

/**
 * Explicit quality and state of a screen time measurement.
 * Never collapse missing permission or incomplete history into silent 0 minutes.
 */
enum class MeasurementStatus {
    /** Valid measurement with > 0 ms of usage. */
    VALID_USAGE,
    /** Valid measurement where no usage occurred (0 ms), with permission granted and complete history. */
    VALID_ZERO,
    /** PACKAGE_USAGE_STATS permission is not granted by the user. */
    PERMISSION_MISSING,
    /** UsageStatsManager is unavailable or device is in Direct Boot / locked state where events cannot be queried. */
    TEMPORARILY_UNAVAILABLE,
    /**
     * Event history is incomplete (e.g. missing initial state at window start without checkpoint/lookback,
     * orphan PAUSE/STOP event, or ungraceful shutdown/reboot gap).
     */
    HISTORY_INCOMPLETE
}

/**
 * Normalized event types derived from Android's UsageEvents.Event constants,
 * kept independent of the Android framework for deterministic unit testing.
 */
enum class UsageEventType(val frameworkCode: Int) {
    ACTIVITY_RESUMED(1), // UsageEvents.Event.ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    ACTIVITY_PAUSED(2),  // UsageEvents.Event.ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    ACTIVITY_STOPPED(23), // UsageEvents.Event.ACTIVITY_STOPPED
    SCREEN_INTERACTIVE(15), // UsageEvents.Event.SCREEN_INTERACTIVE
    SCREEN_NON_INTERACTIVE(16), // UsageEvents.Event.SCREEN_NON_INTERACTIVE
    KEYGUARD_SHOWN(17), // UsageEvents.Event.KEYGUARD_SHOWN
    KEYGUARD_HIDDEN(18), // UsageEvents.Event.KEYGUARD_HIDDEN
    DEVICE_SHUTDOWN(26), // UsageEvents.Event.DEVICE_SHUTDOWN
    DEVICE_STARTUP(27),  // UsageEvents.Event.DEVICE_STARTUP
    OTHER(-1);

    companion object {
        fun fromFrameworkCode(code: Int): UsageEventType = when (code) {
            1 -> ACTIVITY_RESUMED
            2 -> ACTIVITY_PAUSED
            23 -> ACTIVITY_STOPPED
            15 -> SCREEN_INTERACTIVE
            16 -> SCREEN_NON_INTERACTIVE
            17 -> KEYGUARD_SHOWN
            18 -> KEYGUARD_HIDDEN
            26 -> DEVICE_SHUTDOWN
            27 -> DEVICE_STARTUP
            else -> OTHER
        }
    }
}

/**
 * Pure domain representation of a single UsageEvents.Event.
 *
 * @param timestampMs Unix epoch milliseconds.
 * @param eventType Normalized event type.
 * @param packageName Package name associated with the event (empty for system-wide events if not provided).
 * @param className Activity or component class name if provided.
 * @param sequenceIndex Original 0-based order index from UsageEvents cursor to preserve deterministic ordering on equal timestamps.
 */
data class RawUsageEvent(
    val timestampMs: Long,
    val eventType: UsageEventType,
    val packageName: String = "",
    val className: String = "",
    val sequenceIndex: Int = 0
)

/**
 * Classification of reconstructed intervals.
 */
enum class SessionType {
    /** Device screen is interactive (includes lockscreen interaction). */
    DEVICE_INTERACTIVE,
    /** Device screen is interactive AND keyguard (lockscreen) is hidden. */
    DEVICE_UNLOCKED,
    /** Qualified foreground usage of a trackable user app while device is interactive and unlocked. */
    APP_FOREGROUND,
    /** Optional visibility mode: Activity between RESUMED and STOPPED while device is interactive and unlocked. */
    APP_VISIBLE
}

/**
 * Reason why a potential usage interval was excluded or flagged as unknown.
 */
enum class ExclusionReason {
    SCREEN_OFF,
    KEYGUARD_LOCKED,
    LAUNCHER_OR_HOME,
    SYSTEM_UI,
    INPUT_METHOD_EDITOR,
    OWN_APP_OR_LOCK_OVERLAY,
    NON_TRACKABLE_SYSTEM_PACKAGE,
    ORPHAN_PAUSE_WITHOUT_RESUME,
    UNGRACEFUL_SHUTDOWN_GAP,
    INVALID_OR_ZERO_DURATION
}

/**
 * Half-open time interval [startEpochMs, endEpochMs) with startEpochMs < endEpochMs.
 */
data class TimeInterval(
    val startEpochMs: Long,
    val endEpochMs: Long
) {
    val durationMs: Long
        get() = (endEpochMs - startEpochMs).coerceAtLeast(0L)

    fun clipTo(windowStartMs: Long, windowEndMs: Long): TimeInterval? {
        val clippedStart = maxOf(startEpochMs, windowStartMs)
        val clippedEnd = minOf(endEpochMs, windowEndMs)
        return if (clippedEnd > clippedStart) {
            TimeInterval(clippedStart, clippedEnd)
        } else {
            null
        }
    }
}

/**
 * A reconstructed usage session interval clipped to the target window.
 */
data class ReconstructedInterval(
    val sessionType: SessionType,
    val packageName: String,
    val className: String = "",
    val startEpochMs: Long,
    val endEpochMs: Long,
    val isOngoingProjection: Boolean = false
) {
    val durationMs: Long
        get() = (endEpochMs - startEpochMs).coerceAtLeast(0L)
}

/**
 * An excluded or unknown interval recorded for diagnostics.
 */
data class ExcludedIntervalRecord(
    val reason: ExclusionReason,
    val packageName: String = "",
    val className: String = "",
    val timestampOrStartMs: Long,
    val endEpochMs: Long = timestampOrStartMs,
    val details: String = ""
)

/**
 * Snapshot of the device and foreground state at a specific timestamp,
 * used both to seed reconstruction at the start of a window and to persist checkpoints.
 */
data class ReconstructionCheckpointState(
    val timestampMs: Long,
    val screenInteractive: Boolean?,
    val keyguardShowing: Boolean?,
    val activeForegroundPackage: String? = null,
    val activeForegroundClass: String? = null,
    val algorithmVersion: Int = CURRENT_ALGORITHM_VERSION
) {
    companion object {
        const val CURRENT_ALGORITHM_VERSION = 1
    }
}

/**
 * Diagnostics payload produced only when diagnostic mode is requested or inspected.
 */
data class UsageDiagnosticsReport(
    val algorithmVersion: Int = ReconstructionCheckpointState.CURRENT_ALGORITHM_VERSION,
    val queryLookbackStartMs: Long,
    val targetWindowStartMs: Long,
    val targetWindowEndMs: Long,
    val zoneId: String,
    val status: MeasurementStatus,
    val rawEventCount: Int,
    val rawEventsSample: List<RawUsageEvent>,
    val reconstructedIntervals: List<ReconstructedInterval>,
    val excludedIntervals: List<ExcludedIntervalRecord>,
    val unroundedAppForegroundMs: Map<String, Long>,
    val unroundedUnlockedDeviceMs: Long,
    val unroundedInteractiveDeviceMs: Long,
    val ongoingProjectionPackage: String?,
    val ongoingProjectionDurationMs: Long
)

/**
 * Complete result of reconstructing a target window [windowStartMs, windowEndMs).
 */
data class ReconstructionResult(
    val windowStartMs: Long,
    val windowEndMs: Long,
    val zoneId: String,
    val status: MeasurementStatus,
    /** Interactive + unlocked device time in [windowStartMs, windowEndMs). */
    val unlockedDeviceDurationMs: Long,
    /** Interactive device time (including lockscreen) in [windowStartMs, windowEndMs). */
    val interactiveDeviceDurationMs: Long,
    /** Sum of qualified foreground usage across all trackable apps (finalized + ongoing projection). */
    val totalAppForegroundDurationMs: Long,
    /** Per-package qualified foreground duration in ms (finalized + ongoing projection). */
    val appForegroundDurationMap: Map<String, Long>,
    /** Per-package finalized (closed) foreground duration in ms (excluding ongoing projection). */
    val finalizedAppForegroundDurationMap: Map<String, Long>,
    /** Per-package visible duration in ms (optional mode between RESUMED and STOPPED). */
    val appVisibleDurationMap: Map<String, Long>,
    /** Hourly distribution (0..23 in local ZoneId) of qualified app foreground time in ms. */
    val hourlyAppForegroundMs: Map<Int, Long>,
    /** Finalized intervals within [windowStartMs, windowEndMs) suitable for database persistence. */
    val finalizedIntervals: List<ReconstructedInterval>,
    /** Currently ongoing projected intervals at windowEndMs (not yet closed). */
    val ongoingProjections: List<ReconstructedInterval>,
    /** Checkpoint state at windowEndMs. */
    val endCheckpoint: ReconstructionCheckpointState?,
    /** Detailed diagnostic report. */
    val diagnostics: UsageDiagnosticsReport
)
