package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StudyDeckEntity::class,
        FlashcardEntity::class,
        StudyNoteEntity::class,
        ChatMessageEntity::class,
        FocusSessionEntity::class,
        QuizAttemptEntity::class,
        StudyScheduleEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class NibrasDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        @Volatile
        private var INSTANCE: NibrasDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_schedule` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `stageId` TEXT NOT NULL,
                        `dayIndex` INTEGER NOT NULL,
                        `dayLabel` TEXT NOT NULL,
                        `subjectTitle` TEXT NOT NULL,
                        `topic` TEXT NOT NULL,
                        `timeSlot` TEXT NOT NULL,
                        `durationMinutes` INTEGER NOT NULL,
                        `focusType` TEXT NOT NULL,
                        `accentHex` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_schedule_dayIndex` ON `study_schedule` (`dayIndex`)")
            }
        }

        fun getInstance(context: Context): NibrasDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NibrasDatabase::class.java,
                    "nibras_study.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
