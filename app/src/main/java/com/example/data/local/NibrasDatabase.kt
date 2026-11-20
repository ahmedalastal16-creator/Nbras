package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StudyDeckEntity::class,
        FlashcardEntity::class,
        StudyNoteEntity::class,
        ChatMessageEntity::class,
        FocusSessionEntity::class,
        QuizAttemptEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NibrasDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        @Volatile
        private var INSTANCE: NibrasDatabase? = null

        fun getInstance(context: Context): NibrasDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NibrasDatabase::class.java,
                    "nibras_study.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
