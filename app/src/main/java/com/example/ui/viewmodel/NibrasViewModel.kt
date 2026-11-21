package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.attemptAutoSignIn
import com.example.auth.signOutUser
import com.example.audio.StudyVoicePlayer
import com.example.audio.VoicePlaybackState
import com.example.data.curriculum.GazaCurriculumCatalog
import com.example.data.curriculum.GazaStage
import com.example.data.curriculum.PreferredStudyPeriod
import com.example.data.curriculum.ScheduleGenerator
import com.example.data.curriculum.StudyIntensity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.FlashcardEntity
import com.example.data.local.NibrasDatabase
import com.example.data.local.StudyNoteEntity
import com.example.data.local.StudyScheduleEntity
import com.example.data.remote.CloudStudySummary
import com.example.data.remote.ConversationSummary
import com.example.data.remote.FirestoreChatRepository
import com.example.data.remote.FirestoreSummariesRepository
import com.example.data.repository.AiRepository
import com.example.data.repository.AiSpeedMode
import com.example.data.repository.FirebaseAiRepository
import com.example.data.repository.GeneratedFlashcard
import com.example.data.repository.QuizQuestion
import com.example.data.repository.StudyRepository
import com.example.ui.state.AiLabMode
import com.example.ui.state.AiLabOutputState
import com.example.ui.state.DashboardStats
import com.example.ui.state.DeckWithStats
import com.example.ui.state.FocusTimerState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "NibrasViewModel"

sealed interface PendingAiAction {
    val previewText: String
    data class TutorSend(val prompt: String, val imageUri: String?) : PendingAiAction {
        override val previewText: String get() = prompt
    }
    data class LabRun(val notes: String, val imageUri: String?) : PendingAiAction {
        override val previewText: String get() = notes.ifBlank { "صورة صفحة من الرزمة" }
    }
    data class CloudSummarySave(
        val stage: GazaStage,
        val subjectTitle: String,
        val title: String,
        val content: String,
        val keyTerms: String,
        val sourceType: String
    ) : PendingAiAction {
        override val previewText: String get() = "حفظ ملخص: $title"
    }
}

data class AuthAccountState(
    val uid: String? = null,
    val displayName: String? = null,
    val email: String? = null
) {
    val isSignedIn: Boolean get() = !uid.isNullOrBlank()
}

@OptIn(ExperimentalCoroutinesApi::class)
class NibrasViewModel(application: Application) : AndroidViewModel(application) {

    private val studyRepository: StudyRepository
    private val aiRepository: AiRepository = FirebaseAiRepository(application)
    private val firestoreChatRepository: FirestoreChatRepository? = try {
        FirestoreChatRepository(application)
    } catch (e: Exception) {
        Log.w(TAG, "FirestoreChatRepository unavailable: ${e.message}")
        null
    }
    private val firestoreSummariesRepository: FirestoreSummariesRepository? = try {
        FirestoreSummariesRepository(application)
    } catch (e: Exception) {
        Log.w(TAG, "FirestoreSummariesRepository unavailable: ${e.message}")
        null
    }
    private val voicePlayer = StudyVoicePlayer(application)
    private val firebaseAuth: FirebaseAuth? = try {
        Firebase.auth
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseAuth unavailable: ${e.message}")
        null
    }

    private val _authState = MutableStateFlow(readCurrentAuthState())
    val authState: StateFlow<AuthAccountState> = _authState.asStateFlow()

    private val _showSignInScreen = MutableStateFlow(false)
    val showSignInScreen: StateFlow<Boolean> = _showSignInScreen.asStateFlow()

    private val _pendingAiAction = MutableStateFlow<PendingAiAction?>(null)
    val pendingAiAction: StateFlow<PendingAiAction?> = _pendingAiAction.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        _authState.value = AuthAccountState(
            uid = user?.uid,
            displayName = user?.displayName,
            email = user?.email
        )
    }

    init {
        val db = NibrasDatabase.getInstance(application)
        studyRepository = StudyRepository(db.studyDao())
        firebaseAuth?.addAuthStateListener(authStateListener)
        viewModelScope.launch {
            studyRepository.seedStarterContentIfEmpty()
        }
        // Silent auto sign-in for returning Google users; keeps guests free to browse on failure.
        if (firebaseAuth != null && firebaseAuth.currentUser == null) {
            try {
                val cm = CredentialManager.create(application)
                attemptAutoSignIn(
                    context = application,
                    credentialManager = cm,
                    onAuthSuccess = { syncAuthStateNow() },
                    onUnauthenticated = {},
                    scope = viewModelScope
                )
            } catch (e: Exception) {
                Log.w(TAG, "Auto sign-in skipped: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        firebaseAuth?.removeAuthStateListener(authStateListener)
        voicePlayer.release()
    }

    private fun readCurrentAuthState(): AuthAccountState {
        val user = try {
            Firebase.auth.currentUser
        } catch (_: Exception) {
            null
        }
        return AuthAccountState(
            uid = user?.uid,
            displayName = user?.displayName,
            email = user?.email
        )
    }

    fun syncAuthStateNow() {
        _authState.value = readCurrentAuthState()
    }

    fun openSignInScreen() {
        _showSignInScreen.value = true
    }

    fun dismissSignInScreenAsGuest() {
        _showSignInScreen.value = false
        _pendingAiAction.value = null
    }

    fun onGoogleSignInSucceeded() {
        syncAuthStateNow()
        _showSignInScreen.value = false
        val pending = _pendingAiAction.value
        _pendingAiAction.value = null
        when (pending) {
            is PendingAiAction.TutorSend -> sendTutorMessage(pending.prompt, pending.imageUri)
            is PendingAiAction.LabRun -> runAiLab(pending.notes, pending.imageUri)
            is PendingAiAction.CloudSummarySave -> saveCloudStudySummary(
                stage = pending.stage,
                subjectTitle = pending.subjectTitle,
                title = pending.title,
                content = pending.content,
                keyTerms = pending.keyTerms,
                sourceType = pending.sourceType
            )
            null -> Unit
        }
    }

    fun signOut(credentialManager: CredentialManager) {
        cancelTutorRequest()
        cancelAiLabRequest()
        signOutUser(
            credentialManager = credentialManager,
            scope = viewModelScope,
            beforeSignOut = {
                // Detach authenticated Firestore listeners BEFORE Firebase.auth.signOut().
                _authState.value = AuthAccountState()
            },
            onSignOutComplete = {
                _statusBannerMessage.value = "تم تسجيل الخروج. يمكنك مواصلة التصفح كضيف."
            }
        )
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

    val activePackageId: StateFlow<String> = combine(
        _selectedGazaStage,
        _selectedGazaSubject
    ) { stage, subject ->
        GazaCurriculumCatalog.packageConversationId(stage.id, subject)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = GazaCurriculumCatalog.packageConversationId(
            GazaCurriculumCatalog.stages.first().id,
            GazaCurriculumCatalog.stages.first().subjects.first()
        )
    )

    fun selectGazaStage(stage: GazaStage) {
        _selectedGazaStage.value = stage
        if (!stage.subjects.contains(_selectedGazaSubject.value)) {
            _selectedGazaSubject.value = stage.subjects.first()
        }
        _tutorErrorMessage.value = null
    }

    fun selectGazaSubject(subject: String) {
        _selectedGazaSubject.value = subject
        _tutorErrorMessage.value = null
    }

    fun selectSavedPackageConversation(packageId: String, stageId: String, subjectTitle: String) {
        val found = GazaCurriculumCatalog.findPackageById(packageId)
        if (found != null) {
            _selectedGazaStage.value = found.first
            _selectedGazaSubject.value = found.second
        } else {
            val stage = GazaCurriculumCatalog.stages.firstOrNull { it.id == stageId }
                ?: GazaCurriculumCatalog.stages.first()
            _selectedGazaStage.value = stage
            _selectedGazaSubject.value = subjectTitle
        }
        _tutorErrorMessage.value = null
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

    val scheduleItems: StateFlow<List<StudyScheduleEntity>> = studyRepository.scheduleItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _cloudSummaryError = MutableStateFlow<String?>(null)
    val cloudSummaryError: StateFlow<String?> = _cloudSummaryError.asStateFlow()

    private val _isSavingCloudSummary = MutableStateFlow(false)
    val isSavingCloudSummary: StateFlow<Boolean> = _isSavingCloudSummary.asStateFlow()

    val cloudSummaries: StateFlow<List<CloudStudySummary>> = _authState
        .map { it.uid }
        .flatMapLatest { uid ->
            val remoteRepo = firestoreSummariesRepository
            if (uid.isNullOrBlank() || remoteRepo == null) {
                flowOf(emptyList())
            } else {
                remoteRepo.observeSummaries(uid)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Unable to observe cloud summaries", firestoreError ?: error)
                        _cloudSummaryError.value = "تعذر مزامنة بعض الملخصات السحابية مؤقتاً."
                        emit(emptyList())
                    }
            }
        }
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

    // --- Saved Package Conversations List (Authenticated users only; never queries Firestore when guest) ---
    val savedConversations: StateFlow<List<ConversationSummary>> = _authState
        .map { it.uid }
        .flatMapLatest { uid ->
            val remoteRepo = firestoreChatRepository
            if (uid.isNullOrBlank() || remoteRepo == null) {
                flowOf(emptyList())
            } else {
                remoteRepo.observeSavedConversations(uid)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Unable to observe saved conversations", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    // --- Isolated Per-Package Chat Messages ---
    val chatMessages: StateFlow<List<ChatMessageEntity>> = combine(
        _authState.map { it.uid },
        activePackageId
    ) { uid, packageId ->
        uid to packageId
    }.flatMapLatest { (uid, packageId) ->
        val remoteRepo = firestoreChatRepository
        if (!uid.isNullOrBlank() && remoteRepo != null) {
            remoteRepo.observeMessages(uid, packageId)
                .map { remoteMessages ->
                    remoteMessages.mapIndexed { idx, msg ->
                        ChatMessageEntity(
                            id = msg.id.hashCode().toLong() + idx + 1L,
                            packageId = packageId,
                            isUser = msg.role == "user",
                            text = msg.text,
                            imageUri = msg.imageUri,
                            isSampleAiOutput = false,
                            timestamp = msg.createdAt?.toDate()?.time ?: (idx + 1L)
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Remote chat stream failed, falling back to local cache", firestoreError ?: error)
                    emit(emptyList())
                }
        } else {
            // Guest browsing uses strictly local Room storage; no unauthenticated Firestore queries.
            studyRepository.observeChatMessages(packageId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _isTutorThinking = MutableStateFlow(false)
    val isTutorThinking: StateFlow<Boolean> = _isTutorThinking.asStateFlow()

    private val _streamingTutorText = MutableStateFlow<String?>(null)
    val streamingTutorText: StateFlow<String?> = _streamingTutorText.asStateFlow()

    private val _tutorErrorMessage = MutableStateFlow<String?>(null)
    val tutorErrorMessage: StateFlow<String?> = _tutorErrorMessage.asStateFlow()

    private var lastTutorRequest: Pair<String, String?>? = null

    private val _tutorElapsedSeconds = MutableStateFlow(0)
    val tutorElapsedSeconds: StateFlow<Int> = _tutorElapsedSeconds.asStateFlow()

    private val _labElapsedSeconds = MutableStateFlow(0)
    val labElapsedSeconds: StateFlow<Int> = _labElapsedSeconds.asStateFlow()

    private val _aiSpeedMode = MutableStateFlow(AiSpeedMode.FAST)
    val aiSpeedMode: StateFlow<AiSpeedMode> = _aiSpeedMode.asStateFlow()

    private val _autoSpeakTutor = MutableStateFlow(true)
    val autoSpeakTutor: StateFlow<Boolean> = _autoSpeakTutor.asStateFlow()

    val voiceState: StateFlow<VoicePlaybackState> = voicePlayer.state

    private var tutorJob: Job? = null
    private var tutorTickerJob: Job? = null
    private var labJob: Job? = null
    private var labTickerJob: Job? = null

    fun setAiSpeedMode(mode: AiSpeedMode) {
        _aiSpeedMode.value = mode
    }

    fun toggleAutoSpeakTutor() {
        val next = !_autoSpeakTutor.value
        _autoSpeakTutor.value = next
        _statusBannerMessage.value = if (next) {
            "تم تفعيل القراءة الصوتية التلقائية للشرح."
        } else {
            "تم إيقاف القراءة الصوتية التلقائية."
        }
    }

    fun toggleSpeak(itemId: String, text: String) {
        voicePlayer.toggleSpeak(itemId, text)
    }

    fun stopSpeaking() {
        voicePlayer.stop()
    }

    fun setSpeechRate(rate: Float) {
        voicePlayer.setSpeechRate(rate)
    }

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

    // --- AI Tutor Chat (Auth-gated on Send, Per-Package Persistent Thread) ---

    fun cancelTutorRequest() {
        tutorJob?.cancel()
        tutorTickerJob?.cancel()
        _tutorElapsedSeconds.value = 0
        _streamingTutorText.value = null
        _isTutorThinking.value = false
    }

    fun retryLastTutorMessage() {
        val last = lastTutorRequest ?: return
        _tutorErrorMessage.value = null
        sendTutorMessage(last.first, last.second)
    }

    fun sendTutorMessage(prompt: String, attachedImageUri: String?) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() && attachedImageUri == null) return
        if (_isTutorThinking.value) return

        val stage = _selectedGazaStage.value
        val subjectTitle = _selectedGazaSubject.value
        val effectivePrompt = trimmed.ifEmpty { "اشرح لي هذه الصفحة من رزمة $subjectTitle المرفقة" }
        lastTutorRequest = effectivePrompt to attachedImageUri

        // Gate sending AI messages behind Google Sign-In while keeping guest browsing open.
        val uid = readCurrentAuthState().uid
        if (uid.isNullOrBlank()) {
            _pendingAiAction.value = PendingAiAction.TutorSend(effectivePrompt, attachedImageUri)
            _showSignInScreen.value = true
            return
        }

        val packageId = GazaCurriculumCatalog.packageConversationId(stage.id, subjectTitle)
        val speedMode = _aiSpeedMode.value
        val historySnapshot = chatMessages.value.takeLast(6).map { it.isUser to it.text }

        tutorJob?.cancel()
        tutorTickerJob?.cancel()
        _tutorElapsedSeconds.value = 0

        tutorTickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                _tutorElapsedSeconds.update { it + 1 }
            }
        }

        tutorJob = viewModelScope.launch {
            _isTutorThinking.value = true
            _tutorErrorMessage.value = null
            _streamingTutorText.value = null
            try {
                persistPackageMessage(
                    packageId = packageId,
                    stage = stage,
                    subjectTitle = subjectTitle,
                    isUser = true,
                    text = effectivePrompt,
                    imageUri = attachedImageUri
                )

                var finalReply = ""
                aiRepository.askTutorStream(
                    prompt = effectivePrompt,
                    stageTitle = stage.title,
                    subjectTitle = subjectTitle,
                    imageUri = attachedImageUri,
                    speedMode = speedMode,
                    recentHistory = historySnapshot
                ).collect { accumulated ->
                    finalReply = accumulated
                    _streamingTutorText.value = accumulated
                }

                if (finalReply.isNotBlank()) {
                    lastTutorRequest = null
                    persistPackageMessage(
                        packageId = packageId,
                        stage = stage,
                        subjectTitle = subjectTitle,
                        isUser = false,
                        text = finalReply,
                        imageUri = null
                    )
                    if (_autoSpeakTutor.value) {
                        voicePlayer.speak("tutor_auto", finalReply)
                    }
                } else {
                    _tutorErrorMessage.value = "لم يُرجع الذكاء الاصطناعي نصاً. حاول صياغة السؤال مجدداً."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Tutor streaming timed out", e)
                _tutorErrorMessage.value = "انتهت مهلة الاتصال (60 ثانية). تحقق من الإنترنت وجرّب مرة أخرى."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI tutor failed", e)
                _tutorErrorMessage.value = FirebaseAiRepository.formatErrorMessage(e)
            } finally {
                tutorTickerJob?.cancel()
                _tutorElapsedSeconds.value = 0
                _streamingTutorText.value = null
                _isTutorThinking.value = false
            }
        }
    }

    private suspend fun persistPackageMessage(
        packageId: String,
        stage: GazaStage,
        subjectTitle: String,
        isUser: Boolean,
        text: String,
        imageUri: String?
    ) {
        // Persist locally immediately so Room is up-to-date without waiting on Firestore network RTT.
        studyRepository.saveChatMessage(
            packageId = packageId,
            isUser = isUser,
            text = text,
            imageUri = imageUri,
            isSampleAiOutput = false
        )
        val remote = firestoreChatRepository
        if (!readCurrentAuthState().uid.isNullOrBlank() && remote != null) {
            viewModelScope.launch {
                val result = remote.saveMessage(
                    conversationId = packageId,
                    stage = stage,
                    subjectTitle = subjectTitle,
                    role = if (isUser) "user" else "model",
                    text = text,
                    imageUri = imageUri
                )
                if (result.isFailure) {
                    Log.w(TAG, "Cloud save failed, keeping local Room backup")
                }
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
        val packageId = activePackageId.value
        val uid = readCurrentAuthState().uid
        viewModelScope.launch {
            if (!uid.isNullOrBlank()) {
                firestoreChatRepository?.clearConversation(packageId)
            }
            studyRepository.clearChatHistory(packageId)
            _statusBannerMessage.value = "تم مسح محادثة رزمة ${_selectedGazaSubject.value} فقط."
        }
    }

    // --- AI Lab (Auth-gated on Generate) ---

    fun setAiLabMode(mode: AiLabMode) {
        _aiLabMode.value = mode
        _aiLabOutput.value = AiLabOutputState.Idle
    }

    fun cancelAiLabRequest() {
        labJob?.cancel()
        labTickerJob?.cancel()
        _labElapsedSeconds.value = 0
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

        val uid = readCurrentAuthState().uid
        if (uid.isNullOrBlank()) {
            _pendingAiAction.value = PendingAiAction.LabRun(topic, attachedImageUri)
            _showSignInScreen.value = true
            return
        }

        val stageTitle = _selectedGazaStage.value.title
        val subjectTitle = _selectedGazaSubject.value
        val speedMode = _aiSpeedMode.value

        labJob?.cancel()
        labTickerJob?.cancel()
        _labElapsedSeconds.value = 0
        labTickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                _labElapsedSeconds.update { it + 1 }
            }
        }

        labJob = viewModelScope.launch {
            _aiLabOutput.value = AiLabOutputState.Loading
            try {
                when (mode) {
                    AiLabMode.SUMMARY -> {
                        val result = aiRepository.generateSummary(
                            topicOrNotes = topic,
                            stageTitle = stageTitle,
                            subjectTitle = subjectTitle,
                            imageUri = attachedImageUri,
                            speedMode = speedMode
                        )
                        _aiLabOutput.value = AiLabOutputState.SummaryReady(result)
                    }
                    AiLabMode.FLASHCARDS -> {
                        val cards = aiRepository.generateFlashcards(
                            topicOrNotes = topic.ifBlank { "المفاهيم الرئيسية في رزمة $subjectTitle" },
                            stageTitle = stageTitle,
                            subjectTitle = subjectTitle,
                            count = 4,
                            speedMode = speedMode
                        )
                        _aiLabOutput.value = AiLabOutputState.FlashcardsReady(cards)
                    }
                    AiLabMode.QUIZ -> {
                        val questions = aiRepository.generateQuiz(
                            topicOrNotes = topic.ifBlank { "امتحان توجيهي وزاري سابق شامل في رزمة $subjectTitle" },
                            stageTitle = stageTitle,
                            subjectTitle = subjectTitle,
                            count = 5,
                            speedMode = speedMode
                        )
                        _aiLabOutput.value = AiLabOutputState.QuizReady(
                            title = topic.ifBlank { "توجيهي سابق • $subjectTitle" }.take(36),
                            questions = questions
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI Lab timed out", e)
                _aiLabOutput.value = AiLabOutputState.Error("انتهت مهلة المعالجة بعد 60 ثانية. حاول مرة أخرى.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Lab generation failed", e)
                _aiLabOutput.value = AiLabOutputState.Error(FirebaseAiRepository.formatErrorMessage(e))
            } finally {
                labTickerJob?.cancel()
                _labElapsedSeconds.value = 0
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
                examReference = "اختبار مراجعة المادة",
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
            val uid = readCurrentAuthState().uid
            if (!uid.isNullOrBlank()) {
                firestoreSummariesRepository?.saveSummary(
                    stage = _selectedGazaStage.value,
                    subjectTitle = _selectedGazaSubject.value,
                    title = title,
                    content = content,
                    keyTerms = keyTerms.joinToString(" • "),
                    sourceType = "ai_lab"
                )
            }
            _statusBannerMessage.value = if (!uid.isNullOrBlank()) {
                "تم حفظ الملخص محلياً ومزامنته مع Firestore."
            } else {
                "تم حفظ الملخص في المادة المختارة."
            }
        }
    }

    fun clearCloudSummaryError() {
        _cloudSummaryError.value = null
    }

    fun saveCloudStudySummary(
        stage: GazaStage,
        subjectTitle: String,
        title: String,
        content: String,
        keyTerms: String,
        sourceType: String = "manual",
        existingId: String? = null
    ) {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isEmpty() || cleanContent.isEmpty()) {
            _statusBannerMessage.value = "أدخل عنواناً ومحتوىً للملخص قبل الحفظ."
            return
        }

        val uid = readCurrentAuthState().uid
        if (uid.isNullOrBlank()) {
            _pendingAiAction.value = PendingAiAction.CloudSummarySave(
                stage = stage,
                subjectTitle = subjectTitle,
                title = cleanTitle,
                content = cleanContent,
                keyTerms = keyTerms.trim(),
                sourceType = sourceType
            )
            _showSignInScreen.value = true
            return
        }

        val repo = firestoreSummariesRepository
        if (repo == null) {
            _cloudSummaryError.value = "خدمة Firestore غير متاحة حالياً."
            return
        }

        viewModelScope.launch {
            _isSavingCloudSummary.value = true
            _cloudSummaryError.value = null
            val result = repo.saveSummary(
                stage = stage,
                subjectTitle = subjectTitle,
                title = cleanTitle,
                content = cleanContent,
                keyTerms = keyTerms,
                sourceType = sourceType,
                existingId = existingId
            )
            _isSavingCloudSummary.value = false
            if (result.isSuccess) {
                _statusBannerMessage.value = if (existingId != null) {
                    "تم تحديث الملخص ومزامنته مع Firestore."
                } else {
                    "تم حفظ الملخص ومزامنته في السحابة."
                }
            } else {
                _cloudSummaryError.value = result.exceptionOrNull()?.localizedMessage
                    ?: "تعذر حفظ الملخص في Firestore. تحقق من اتصالك."
            }
        }
    }

    fun deleteCloudStudySummary(summaryId: String) {
        val repo = firestoreSummariesRepository ?: return
        viewModelScope.launch {
            val result = repo.deleteSummary(summaryId)
            if (result.isSuccess) {
                _statusBannerMessage.value = "تم حذف الملخص السحابي."
            } else {
                _cloudSummaryError.value = "تعذر حذف الملخص من Firestore."
            }
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

    // --- Study Schedule (جدول الدراسة حسب المواد) ---

    fun generateSmartSchedule(
        selectedSubjects: List<String>,
        intensity: StudyIntensity,
        period: PreferredStudyPeriod
    ) {
        val stage = _selectedGazaStage.value
        val items = ScheduleGenerator.generateWeeklySchedule(
            stage = stage,
            selectedSubjects = selectedSubjects,
            intensity = intensity,
            preferredPeriod = period
        )
        viewModelScope.launch {
            studyRepository.replaceSchedule(items)
            _statusBannerMessage.value = "تم إنشاء جدول دراسة أسبوعي (${items.size} حصص) لمرحلة ${stage.badge}."
        }
    }

    fun addCustomScheduleItem(
        dayIndex: Int,
        subjectTitle: String,
        topic: String,
        timeSlot: String,
        durationMinutes: Int,
        focusType: String
    ) {
        val stage = _selectedGazaStage.value
        val safeDay = dayIndex.coerceIn(0, ScheduleGenerator.weekDays.lastIndex)
        viewModelScope.launch {
            studyRepository.addScheduleItem(
                StudyScheduleEntity(
                    stageId = stage.id,
                    dayIndex = safeDay,
                    dayLabel = ScheduleGenerator.weekDays[safeDay],
                    subjectTitle = subjectTitle.trim(),
                    topic = topic.trim(),
                    timeSlot = timeSlot.trim(),
                    durationMinutes = durationMinutes.coerceIn(10, 240),
                    focusType = focusType,
                    accentHex = 0xFF5EEAD4L,
                    isCompleted = false
                )
            )
            _statusBannerMessage.value = "تمت إضافة حصة «$subjectTitle» ليوم ${ScheduleGenerator.weekDays[safeDay]}."
        }
    }

    fun toggleScheduleCompleted(item: StudyScheduleEntity, completed: Boolean) {
        viewModelScope.launch {
            studyRepository.toggleScheduleCompleted(item, completed)
        }
    }

    fun deleteScheduleItem(itemId: Long) {
        viewModelScope.launch {
            studyRepository.deleteScheduleItem(itemId)
        }
    }

    fun prepareFocusFromSchedule(subjectTitle: String, durationMinutes: Int) {
        timerJob?.cancel()
        val safeMinutes = durationMinutes.coerceIn(10, 120)
        _focusTimer.value = FocusTimerState(
            selectedMinutes = safeMinutes,
            remainingSeconds = safeMinutes * 60,
            isRunning = false,
            activeSubject = subjectTitle
        )
        _statusBannerMessage.value = "تم تجهيز مؤقت التركيز لمادة «$subjectTitle» ($safeMinutes دقيقة)."
    }

    fun preparePackageForSubject(subjectTitle: String) {
        val matchedStage = GazaCurriculumCatalog.stages.firstOrNull { stage ->
            stage.subjects.any { it == subjectTitle || subjectTitle.startsWith(it) }
        }
        if (matchedStage != null) {
            _selectedGazaStage.value = matchedStage
            val exact = matchedStage.subjects.firstOrNull { it == subjectTitle || subjectTitle.startsWith(it) }
            if (exact != null) {
                _selectedGazaSubject.value = exact
            }
        }
    }
}
