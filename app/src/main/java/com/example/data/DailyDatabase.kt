package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyTask::class, TaskCompletion::class],
    version = 6,
    exportSchema = false
)
abstract class DailyDatabase : RoomDatabase() {

    abstract fun dailyTaskDao(): DailyTaskDao

    companion object {
        private const val TAG = "DailyDatabase"
        private const val DB_NAME = "more2day_habits.db"

        @Volatile
        private var INSTANCE: DailyDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_tasks ADD COLUMN frequencyType TEXT NOT NULL DEFAULT 'DAILY'")
                db.execSQL("ALTER TABLE daily_tasks ADD COLUMN targetDaysPerWeek INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE daily_tasks ADD COLUMN daysOfWeek TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_tasks ADD COLUMN intervalDays INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE daily_tasks ADD COLUMN intervalAnchorDate TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_daily_tasks_isArchived_orderIndex")
                db.execSQL("DROP INDEX IF EXISTS index_daily_tasks_reminderEnabled")
                db.execSQL("DROP INDEX IF EXISTS index_task_completions_taskId_dateKey")
                db.execSQL("DROP INDEX IF EXISTS index_task_completions_dateKey")

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_daily_tasks_isArchived_orderIndex ON daily_tasks (isArchived, orderIndex, id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_daily_tasks_reminderEnabled ON daily_tasks (isArchived, reminderEnabled)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_task_completions_taskId_dateKey ON task_completions (taskId, dateKey)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_task_completions_dateKey ON task_completions (dateKey)"
                )
            }
        }

        private fun buildDatabase(context: Context): DailyDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DailyDatabase::class.java,
                DB_NAME
            )
                .addMigrations(
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_6
                )
                .fallbackToDestructiveMigration()
                .build()
        }

        fun getInstance(context: Context): DailyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val db = try {
                        val candidate = buildDatabase(context)
                        candidate.openHelper.writableDatabase
                        candidate
                    } catch (t: Throwable) {
                        Log.e(TAG, "Database initialization or migration failed; resetting local database", t)
                        try {
                            context.applicationContext.deleteDatabase(DB_NAME)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to remove corrupted database", e)
                        }
                        val fallback = buildDatabase(context)
                        fallback.openHelper.writableDatabase
                        fallback
                    }
                    INSTANCE = db
                    db
                }
            }
        }
    }
}
