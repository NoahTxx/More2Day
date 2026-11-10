package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted finalized usage interval reconstructed from UsageEvents.
 * Ongoing projections are NOT stored here; only closed intervals are persisted.
 *
 * Overlapping imports replace existing intervals within the recalculated window transactionally,
 * guaranteeing idempotency across repeated reads.
 */
@Entity(
    tableName = "reconstructed_usage_sessions",
    indices = [
        Index(value = ["dateKey", "sessionType"], name = "index_reconstructed_usage_sessions_dateKey_sessionType"),
        Index(value = ["startEpochMs", "endEpochMs"], name = "index_reconstructed_usage_sessions_start_end"),
        Index(
            value = ["sessionType", "packageName", "startEpochMs", "endEpochMs"],
            unique = true,
            name = "index_reconstructed_usage_sessions_unique_interval"
        )
    ]
)
data class ReconstructedUsageSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateKey: String,
    val sessionType: String, // SessionType.name
    val packageName: String,
    val className: String = "",
    val startEpochMs: Long,
    val endEpochMs: Long,
    val durationMs: Long,
    val zoneId: String,
    val measurementStatus: String, // MeasurementStatus.name
    val algorithmVersion: Int = 1,
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * Stores the latest verified reconstruction state checkpoint so subsequent queries can
 * know the screen/keyguard/foreground state at window boundaries even after raw UsageEvents expire.
 */
@Entity(tableName = "usage_import_checkpoints")
data class UsageImportCheckpointEntity(
    @PrimaryKey
    val checkpointKey: String = DEFAULT_CHECKPOINT_KEY,
    val timestampMs: Long,
    val screenInteractive: Boolean?,
    val keyguardShowing: Boolean?,
    val activeForegroundPackage: String?,
    val activeForegroundClass: String?,
    val zoneId: String,
    val algorithmVersion: Int = 1,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_CHECKPOINT_KEY = "PRIMARY_USAGE_CHECKPOINT"
    }
}
