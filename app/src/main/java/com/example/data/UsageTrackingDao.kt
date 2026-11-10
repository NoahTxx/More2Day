package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface UsageTrackingDao {

    @Query("""
        SELECT * FROM reconstructed_usage_sessions
        WHERE endEpochMs > :windowStartMs AND startEpochMs < :windowEndMs
        ORDER BY startEpochMs ASC, endEpochMs ASC
    """)
    fun getSessionsOverlappingWindowSync(
        windowStartMs: Long,
        windowEndMs: Long
    ): List<ReconstructedUsageSessionEntity>

    @Query("""
        SELECT * FROM reconstructed_usage_sessions
        WHERE dateKey = :dateKey
        ORDER BY startEpochMs ASC
    """)
    fun getSessionsForDateKeySync(dateKey: String): List<ReconstructedUsageSessionEntity>

    @Query("""
        DELETE FROM reconstructed_usage_sessions
        WHERE startEpochMs >= :windowStartMs AND endEpochMs <= :windowEndMs
    """)
    fun deleteSessionsContainedInWindowSync(windowStartMs: Long, windowEndMs: Long)

    @Query("""
        DELETE FROM reconstructed_usage_sessions
        WHERE endEpochMs > :windowStartMs AND startEpochMs < :windowEndMs
    """)
    fun deleteSessionsOverlappingWindowSync(windowStartMs: Long, windowEndMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSessionsSync(sessions: List<ReconstructedUsageSessionEntity>)

    /**
     * Transactional idempotent replacement of reconstructed sessions in `[windowStartMs, windowEndMs)`.
     * Repeating the import with identical events yields the exact same database rows without duplication.
     */
    @Transaction
    fun replaceSessionsInWindowSync(
        windowStartMs: Long,
        windowEndMs: Long,
        newSessions: List<ReconstructedUsageSessionEntity>
    ) {
        deleteSessionsOverlappingWindowSync(windowStartMs, windowEndMs)
        if (newSessions.isNotEmpty()) {
            insertSessionsSync(newSessions)
        }
    }

    @Query("SELECT * FROM usage_import_checkpoints WHERE checkpointKey = :key LIMIT 1")
    fun getCheckpointSync(
        key: String = UsageImportCheckpointEntity.DEFAULT_CHECKPOINT_KEY
    ): UsageImportCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertCheckpointSync(checkpoint: UsageImportCheckpointEntity)

    @Query("DELETE FROM reconstructed_usage_sessions")
    fun clearAllSessionsSync()

    @Query("DELETE FROM usage_import_checkpoints")
    fun clearAllCheckpointsSync()
}
