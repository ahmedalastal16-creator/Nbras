package com.example.ui.state

import com.example.data.local.FlashcardEntity
import com.example.data.local.FocusSessionEntity
import com.example.data.local.QuizAttemptEntity
import com.example.data.local.StudyDeckEntity
import com.example.data.repository.AiSummaryResult
import com.example.data.repository.GeneratedFlashcard
import com.example.data.repository.QuizQuestion

data class DeckWithStats(
    val deck: StudyDeckEntity,
    val totalCards: Int,
    val masteredCards: Int
) {
    val masteryFraction: Float
        get() = if (totalCards == 0) 0f else masteredCards.toFloat() / totalCards.toFloat()
}

data class FocusTimerState(
    val selectedMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val activeSubject: String = "جلسة تركيز حرة"
) {
    val progress: Float
        get() {
            val total = selectedMinutes * 60
            return if (total <= 0) 0f else (total - remainingSeconds).toFloat() / total.toFloat()
        }
}

enum class AiLabMode {
    SUMMARY,
    FLASHCARDS,
    QUIZ
}

sealed interface AiLabOutputState {
    data object Idle : AiLabOutputState
    data object Loading : AiLabOutputState
    data class SummaryReady(val summary: AiSummaryResult) : AiLabOutputState
    data class FlashcardsReady(val cards: List<GeneratedFlashcard>) : AiLabOutputState
    data class QuizReady(
        val title: String,
        val questions: List<QuizQuestion>,
        val currentIndex: Int = 0,
        val selectedOptionIndex: Int? = null,
        val score: Int = 0,
        val isCompleted: Boolean = false
    ) : AiLabOutputState
    data class Error(val message: String) : AiLabOutputState
}

data class DashboardStats(
    val totalCards: Int = 0,
    val masteredCards: Int = 0,
    val totalFocusMinutes: Int = 0,
    val totalFocusSessions: Int = 0,
    val totalQuizzes: Int = 0,
    val averageQuizPercent: Int = 0,
    val recentFocusSessions: List<FocusSessionEntity> = emptyList(),
    val recentQuizAttempts: List<QuizAttemptEntity> = emptyList()
) {
    val masteryPercent: Int
        get() = if (totalCards == 0) 0 else ((masteredCards.toFloat() / totalCards.toFloat()) * 100).toInt()
}

sealed interface DeckStudyMode {
    data object Browse : DeckStudyMode
    data class ActiveReview(
        val cards: List<FlashcardEntity>,
        val currentIndex: Int = 0,
        val isFlipped: Boolean = false,
        val completedCount: Int = 0
    ) : DeckStudyMode
}
