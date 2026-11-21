package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "study_decks")
data class StudyDeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val subtitle: String,
    val category: String,
    val accentHex: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = StudyDeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deckId")]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deckId: Long,
    val question: String,
    val answer: String,
    val hint: String = "",
    val isMastered: Boolean = false,
    val reviewCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_notes",
    foreignKeys = [
        ForeignKey(
            entity = StudyDeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deckId")]
)
data class StudyNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deckId: Long,
    val title: String,
    val summaryContent: String,
    val keyTerms: String,
    val isSampleAiOutput: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    indices = [Index("packageId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageId: String = "pkg_tawjihi_scientific_0",
    val isUser: Boolean,
    val text: String,
    val imageUri: String? = null,
    val isSampleAiOutput: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val durationMinutes: Int,
    val subjectTitle: String,
    val completedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deckTitle: String,
    val score: Int,
    val totalQuestions: Int,
    val completedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_schedule",
    indices = [Index("dayIndex")]
)
data class StudyScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val stageId: String,
    val dayIndex: Int,
    val dayLabel: String,
    val subjectTitle: String,
    val topic: String,
    val timeSlot: String,
    val durationMinutes: Int,
    val focusType: String,
    val accentHex: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
