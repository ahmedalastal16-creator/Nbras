package com.example.data.repository

import kotlinx.coroutines.flow.Flow

data class AiSummaryResult(
    val title: String,
    val overview: String,
    val bulletPoints: List<String>,
    val keyTerms: List<String>,
    val examTip: String,
    val isSampleResponse: Boolean = false
)

data class GeneratedFlashcard(
    val question: String,
    val answer: String,
    val hint: String,
    val isSampleResponse: Boolean = false
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val isSampleResponse: Boolean = false
)

interface AiRepository {
    fun askTutorStream(
        prompt: String,
        stageTitle: String,
        subjectTitle: String,
        imageUri: String?
    ): Flow<String>

    suspend fun generateSummary(
        topicOrNotes: String,
        stageTitle: String,
        subjectTitle: String,
        imageUri: String?
    ): AiSummaryResult

    suspend fun generateFlashcards(
        topicOrNotes: String,
        stageTitle: String,
        subjectTitle: String,
        count: Int = 4
    ): List<GeneratedFlashcard>

    suspend fun generateQuiz(
        topicOrNotes: String,
        stageTitle: String,
        subjectTitle: String,
        count: Int = 4
    ): List<QuizQuestion>
}
