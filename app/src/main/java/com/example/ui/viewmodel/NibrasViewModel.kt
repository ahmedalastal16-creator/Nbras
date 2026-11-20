package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.curriculum.GazaCurriculumCatalog
import com.example.data.curriculum.GazaStage
import com.example.data.local.ChatMessageEntity
import com.example.data.local.FlashcardEntity
import com.example.data.local.NibrasDatabase
import com.example.data.local.StudyNoteEntity
import com.example.data.repository.AiRepository
import com.example.data.repository.FirebaseAiRepository
import com.example.data.repository.GeneratedFlashcard
import com.example.data.repository.QuizQuestion
import com.example.data.repository.StudyRepository
import com.example.ui.state.AiLabMode
import com.example.ui.state.AiLabOutputState
import com.example.ui.state.DashboardStats
import com.example.ui.state.DeckWithStats
import com.example.ui.state.FocusTimerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "NibrasViewModel"

class NibrasViewModel(application: Application) : AndroidViewModel(application) {

    private val studyRepository: StudyRepository
    private val aiRepository: AiRepository = FirebaseAiRepository(application)

    init {
        val db = NibrasDatabase.getInstance(application)
        studyRepository = StudyRepository(db.studyDao())
        viewModelScope.launch {
            studyRepository.seedStarterContentIfEmpty()
        }
    }

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.update { !it }
    }

    // --- Selected Palestinian Gaza Curriculum Package ---
    private val _selectedGazaStage = MutableStateFlow(GazaCurriculumCatalog.stages.first())
    val selectedGazaStage: StateFlow<GazaStage> = _selectedGazaStage.asStateFlow()

    private val _selectedGazaSubject = MutableStateFlow(
        GazaCurriculumCatalog.stages.first().subjects.first()
    )
    val selectedGazaSubject: StateFlow<String> = _selectedGazaSubject.asStateFlow()

    fun selectGazaStage(stage: GazaStage) {
        _selectedGazaStage.value = stage
        if (!stage.subjects.contains(_selectedGazaSubject.value)) {
            _selectedGazaSubject.value = stage.subjects.first()
        }
    }

    fun selectGazaSubject(subject: String) {
        _selectedGazaSubject.value = subject
    }

    val decksWithStats: StateFlow<List<DeckWithStats>> = combine(
        studyRepository.decks,
        studyRepository.allFlashcards
    ) { decks, cards ->
        val grouped = cards.groupBy { it.deckId }
        decks.map { deck ->
            val deckCards = grouped[deck.id].orEmpty()
            DeckWithStats(
                deck = deck,
                totalCards = deckCards.size,
                masteredCards = deckCards.count { it.isMastered }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val allFlashcards: StateFlow<List<FlashcardEntity>> = studyRepository.allFlashcards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val allNotes: StateFlow<List<StudyNoteEntity>> = studyRepository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val dashboardStats: StateFlow<DashboardStats> = combine(
        studyRepository.allFlashcards,
        studyRepository.focusSessions,
        studyRepository.quizAttempts
    ) { cards, sessions, attempts ->
        val avgQuiz = if (attempts.isEmpty()) {
            0
        } else {
            val totalRatio = attempts.sumOf {
                if (it.totalQuestions == 0) 0.0 else (it.score.toDouble() / it.totalQuestions.toDouble()) * 100.0
            }
            (totalRatio / attempts.size).toInt()
        }
        DashboardStats(
            totalCards = cards.size,
            masteredCards = cards.count { it.isMastered },
            totalFocusMinutes = sessions.sumOf { it.durationMinutes },
            totalFocusSessions = sessions.size,
            totalQuizzes = attempts.size,
            averageQuizPercent = avgQuiz,
            recentFocusSessions = sessions.take(6),
            recentQuizAttempts = attempts.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DashboardStats())

    val chatMessages: StateFlow<List<ChatMessageEntity>> = studyRepository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _isTutorThinking = MutableStateFlow(false)
    val isTutorThinking: StateFlow<Boolean> = _isTutorThinking.asStateFlow()

    private val _streamingTutorText = MutableStateFlow<String?>(null)
    val streamingTutorText: StateFlow<String?> = _streamingTutorText.asStateFlow()

    private val _tutorErrorMessage = MutableStateFlow<String?>(null)
    val tutorErrorMessage: StateFlow<String?> = _tutorErrorMessage.asStateFlow()

    fun dismissTutorError() {
        _tutorErrorMessage.value = null
    }

    private val _focusTimer = MutableStateFlow(FocusTimerState())
    val focusTimer: StateFlow<FocusTimerState> = _focusTimer.asStateFlow()
    private var timerJob: Job? = null

    private val _aiLabMode = MutableStateFlow(AiLabMode.SUMMARY)
    val aiLabMode: StateFlow<AiLabMode> = _aiLabMode.asStateFlow()

    private val _aiLabOutput = MutableStateFlow<AiLabOutputState>(AiLabOutputState.Idle)
    val aiLabOutput: StateFlow<AiLabOutputState> = _aiLabOutput.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun showStatusMessage(message: String) {
        _statusBannerMessage.value = message
    }

    // --- Focus Timer ---

    fun selectFocusPreset(minutes: Int) {
        timerJob?.cancel()
        _focusTimer.update {
            it.copy(
                selectedMinutes = minutes,
                remainingSeconds = minutes * 60,
                isRunning = false
            )
        }
    }

    fun selectFocusSubject(subject: String) {
        _focusTimer.update { it.copy(activeSubject = subject) }
    }

    fun toggleFocusTimer() {
        val current = _focusTimer.value
        if (current.isRunning) {
            timerJob?.cancel()
            _focusTimer.update { it.copy(isRunning = false) }
        } else {
            _focusTimer.update { it.copy(isRunning = true) }
            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (_focusTimer.value.remainingSeconds > 0 && _focusTimer.value.isRunning) {
                    delay(1_000L)
                    _focusTimer.update { state ->
                        val next = (state.remainingSeconds - 1).coerceAtLeast(0)
                        state.copy(remainingSeconds = next)
                    }
                }
                if (_focusTimer.value.remainingSeconds == 0 && _focusTimer.value.isRunning) {
                    val completedMinutes = _focusTimer.value.selectedMinutes
                    val subject = _focusTimer.value.activeSubject
                    studyRepository.recordFocusSession(completedMinutes, subject)
                    _focusTimer.update {
                        it.copy(
                            isRunning = false,
                            remainingSeconds = it.selectedMinutes * 60
                        )
                    }
                    _statusBannerMessage.value = "اكتملت جلسة التركيز ($completedMinutes دقيقة) وتم حفظها."
                }
            }
        }
    }

    fun resetFocusTimer() {
        timerJob?.cancel()
        _focusTimer.update {
            it.copy(
                isRunning = false,
                remainingSeconds = it.selectedMinutes * 60
            )
        }
    }

    fun logQuickFocusSession() {
        val minutes = _focusTimer.value.selectedMinutes
        val subject = _focusTimer.value.activeSubject
        viewModelScope.launch {
            studyRepository.recordFocusSession(minutes, subject)
            _statusBannerMessage.value = "تم تسجيل جلسة تركيز ($minutes دقيقة) في سجل إنجازك."
        }
    }

    // --- AI Tutor Chat (Streaming Gemini) ---

    fun sendTutorMessage(prompt: String, attachedImageUri: String?) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() && attachedImageUri == null) return
        if (_isTutorThinking.value) return

        val stageTitle = _selectedGazaStage.value.title
        val subjectTitle = _selectedGazaSubject.value
        val effectivePrompt = trimmed.ifEmpty { "اشرح لي هذه الصفحة من رزمة $subjectTitle المرفقة" }

        viewModelScope.launch {
            _isTutorThinking.value = true
            _tutorErrorMessage.value = null
            _streamingTutorText.value = null
            try {
                studyRepository.saveChatMessage(
                    isUser = true,
                    text = effectivePrompt,
                    imageUri = attachedImageUri,
                    isSampleAiOutput = false
                )

                var finalReply = ""
                aiRepository.askTutorStream(
                    prompt = effectivePrompt,
                    stageTitle = stageTitle,
                    subjectTitle = subjectTitle,
                    imageUri = attachedImageUri
                ).collect { accumulated ->
                    finalReply = accumulated
                    _streamingTutorText.value = accumulated
                }

                if (finalReply.isNotBlank()) {
                    studyRepository.saveChatMessage(
                        isUser = false,
                        text = finalReply,
                        imageUri = null,
                        isSampleAiOutput = false
                    )
                } else {
                    _tutorErrorMessage.value = "لم يُرجع الذكاء الاصطناعي نصاً. حاول صياغة السؤال مجدداً."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Tutor streaming timed out", e)
                _tutorErrorMessage.value = "انتهت مهلة الاتصال (60 ثانية). تحقق من الإنترنت وحاول مجدداً."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI tutor failed", e)
                _tutorErrorMessage.value = e.localizedMessage ?: "تعذر الاتصال بـ Gemini AI."
            } finally {
                _streamingTutorText.value = null
                _isTutorThinking.value = false
            }
        }
    }

    fun saveTutorReplyToDeck(deckId: Long, replyText: String, isSample: Boolean) {
        viewModelScope.launch {
            val firstLine = replyText
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.take(48)
                ?: "شرح ${_selectedGazaSubject.value}"
            studyRepository.saveNote(
                deckId = deckId,
                title = firstLine,
                summaryContent = replyText,
                keyTerms = "${_selectedGazaSubject.value} • رزم غزة",
                isSampleAiOutput = isSample
            )
            _statusBannerMessage.value = "تم حفظ شرح المعلم في المادة المختارة."
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            studyRepository.clearChatHistory()
        }
    }

    // --- AI Lab (Summary, Flashcards, Quiz via Gemini Structured JSON) ---

    fun setAiLabMode(mode: AiLabMode) {
        _aiLabMode.value = mode
        _aiLabOutput.value = AiLabOutputState.Idle
    }

    fun runAiLab(inputNotes: String, attachedImageUri: String?) {
        val mode = _aiLabMode.value
        val topic = inputNotes.trim()
        if (topic.isEmpty() && attachedImageUri == null) {
            _aiLabOutput.value = AiLabOutputState.Error(
                "أدخل عنوان الدرس أو ملاحظات من رزمة ${_selectedGazaSubject.value}، أو أرفق صورة للبدء."
            )
            return
        }

        val stageTitle = _selectedGazaStage.value.title
        val subjectTitle = _selectedGazaSubject.value

        viewModelScope.launch {
            _aiLabOutput.value = AiLabOutputState.Loading
            try {
                when (mode) {
                    AiLabMode.SUMMARY -> {
                        val result = aiRepository.generateSummary(
                            topicOrNotes = topic,
                            stageTitle = stageTitle,
                            subjectTitle = subjectTitle,
                            imageUri = attachedImageUri
                        )
                        _aiLabOutput.value = AiLabOutputState.SummaryReady(result)
                    }
                    AiLabMode.FLASHCARDS -> {
                        val cards = aiRepository.generateFlashcards(
                            topicOrNotes = topic.ifBlank { "المفاهيم الرئيسية في رزمة $subjectTitle" },
                            stageTitle = stageTitle,
                            subjectTitle = subjectTitle,
                            count = 4
                        )
                        _aiLabOutput.value = AiLabOutputState.FlashcardsReady(cards)
                    }
                    AiLabMode.QUIZ -> {
                        val questions = aiRepository.generateQuiz(
                            topicOrNotes = topic.ifBlank { "مراجعة رزمة $subjectTitle" },
                            stageTitle = stageTitle,
                            subjectTitle = subjectTitle,
                            count = 4
                        )
                        _aiLabOutput.value = AiLabOutputState.QuizReady(
                            title = topic.ifBlank { subjectTitle }.take(32),
                            questions = questions
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI Lab timed out", e)
                _aiLabOutput.value = AiLabOutputState.Error("انتهت مهلة المعالجة بعد 60 ثانية. يرجى المحاولة مرة أخرى.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Lab generation failed", e)
                _aiLabOutput.value = AiLabOutputState.Error(e.localizedMessage ?: "تعذر التوليد عبر Gemini AI.")
            }
        }
    }

    fun startDeckQuiz(deckTitle: String, deckCards: List<FlashcardEntity>) {
        if (deckCards.size < 2) {
            _statusBannerMessage.value = "أضف بطاقتين على الأقل لتشغيل اختبار من هذه المادة."
            return
        }
        val questions = deckCards.take(5).mapIndexed { index, card ->
            val distractors = deckCards.filter { it.id != card.id }.shuffled().take(3).map { it.answer }
            val allOptions = (listOf(card.answer) + distractors).shuffled()
            val correctIdx = allOptions.indexOf(card.answer).coerceAtLeast(0)
            QuizQuestion(
                question = "السؤال ${index + 1}: ${card.question}",
                options = allOptions,
                correctIndex = correctIdx,
                explanation = card.hint.ifBlank { card.answer },
                isSampleResponse = false
            )
        }
        _aiLabMode.value = AiLabMode.QUIZ
        _aiLabOutput.value = AiLabOutputState.QuizReady(
            title = deckTitle,
            questions = questions
        )
    }

    fun selectQuizOption(optionIndex: Int) {
        val current = _aiLabOutput.value as? AiLabOutputState.QuizReady ?: return
        if (current.selectedOptionIndex != null || current.isCompleted) return
        val isCorrect = optionIndex == current.questions[current.currentIndex].correctIndex
        _aiLabOutput.value = current.copy(
            selectedOptionIndex = optionIndex,
            score = if (isCorrect) current.score + 1 else current.score
        )
    }

    fun advanceQuiz() {
        val current = _aiLabOutput.value as? AiLabOutputState.QuizReady ?: return
        val nextIndex = current.currentIndex + 1
        if (nextIndex >= current.questions.size) {
            _aiLabOutput.value = current.copy(isCompleted = true)
            viewModelScope.launch {
                studyRepository.recordQuizAttempt(
                    deckTitle = current.title,
                    score = current.score,
                    totalQuestions = current.questions.size
                )
            }
        } else {
            _aiLabOutput.value = current.copy(
                currentIndex = nextIndex,
                selectedOptionIndex = null
            )
        }
    }

    fun resetAiLabOutput() {
        _aiLabOutput.value = AiLabOutputState.Idle
    }

    fun saveSummaryToDeck(
        deckId: Long,
        title: String,
        overview: String,
        bullets: List<String>,
        keyTerms: List<String>,
        isSample: Boolean
    ) {
        viewModelScope.launch {
            val content = buildString {
                append(overview)
                append("\n\n")
                bullets.forEachIndexed { idx, bullet ->
                    append("${idx + 1}. $bullet\n")
                }
            }
            studyRepository.saveNote(
                deckId = deckId,
                title = title,
                summaryContent = content,
                keyTerms = keyTerms.joinToString(" • "),
                isSampleAiOutput = isSample
            )
            _statusBannerMessage.value = "تم حفظ الملخص في المادة المختارة."
        }
    }

    fun saveGeneratedCardsToDeck(deckId: Long, cards: List<GeneratedFlashcard>) {
        viewModelScope.launch {
            val entities = cards.map {
                FlashcardEntity(
                    deckId = deckId,
                    question = it.question,
                    answer = it.answer,
                    hint = it.hint
                )
            }
            studyRepository.addFlashcards(entities)
            _statusBannerMessage.value = "تمت إضافة ${cards.size} بطاقات تعليمية إلى المادة."
        }
    }

    // --- Decks & Flashcards CRUD ---

    fun createDeckFromGazaPackage(stageTitle: String, subjectTitle: String) {
        viewModelScope.launch {
            studyRepository.createDeck(
                title = "$subjectTitle — رزم غزة",
                subtitle = stageTitle,
                category = stageTitle,
                accentHex = 0xFFE2C068
            )
            _statusBannerMessage.value = "تمت إضافة رزمة «$subjectTitle» إلى موادك الدراسية."
        }
    }

    fun createDeck(title: String, subtitle: String, category: String, accentHex: Long) {
        if (title.isBlank()) return
        viewModelScope.launch {
            studyRepository.createDeck(title, subtitle, category, accentHex)
            _statusBannerMessage.value = "تم إنشاء المادة الدراسية بنجاح."
        }
    }

    fun deleteDeck(deckId: Long) {
        viewModelScope.launch {
            studyRepository.deleteDeck(deckId)
        }
    }

    fun addFlashcard(deckId: Long, question: String, answer: String, hint: String) {
        if (question.isBlank() || answer.isBlank()) return
        viewModelScope.launch {
            studyRepository.addFlashcard(deckId, question, answer, hint)
            _statusBannerMessage.value = "تمت إضافة البطاقة التعليمية."
        }
    }

    fun toggleFlashcardMastery(card: FlashcardEntity, mastered: Boolean) {
        viewModelScope.launch {
            studyRepository.toggleMastery(card, mastered)
        }
    }

    fun deleteFlashcard(cardId: Long) {
        viewModelScope.launch {
            studyRepository.deleteFlashcard(cardId)
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            studyRepository.deleteNote(noteId)
        }
    }
}
