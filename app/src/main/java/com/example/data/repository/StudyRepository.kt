package com.example.data.repository

import com.example.data.curriculum.GazaCurriculumCatalog
import com.example.data.curriculum.PreferredStudyPeriod
import com.example.data.curriculum.ScheduleGenerator
import com.example.data.curriculum.StudyIntensity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.FlashcardEntity
import com.example.data.local.FocusSessionEntity
import com.example.data.local.QuizAttemptEntity
import com.example.data.local.StudyDao
import com.example.data.local.StudyDeckEntity
import com.example.data.local.StudyNoteEntity
import com.example.data.local.StudyScheduleEntity
import kotlinx.coroutines.flow.Flow

class StudyRepository(private val dao: StudyDao) {

    val decks: Flow<List<StudyDeckEntity>> = dao.observeDecks()
    val allFlashcards: Flow<List<FlashcardEntity>> = dao.observeAllFlashcards()
    val allNotes: Flow<List<StudyNoteEntity>> = dao.observeAllNotes()
    fun observeChatMessages(packageId: String): Flow<List<ChatMessageEntity>> = dao.observeChatMessages(packageId)
    val focusSessions: Flow<List<FocusSessionEntity>> = dao.observeFocusSessions()
    val quizAttempts: Flow<List<QuizAttemptEntity>> = dao.observeQuizAttempts()
    val scheduleItems: Flow<List<StudyScheduleEntity>> = dao.observeScheduleItems()

    suspend fun seedStarterContentIfEmpty() {
        if (dao.getScheduleCount() == 0) {
            val defaultStage = GazaCurriculumCatalog.stages.first()
            val initialSchedule = ScheduleGenerator.generateWeeklySchedule(
                stage = defaultStage,
                selectedSubjects = defaultStage.subjects.take(4),
                intensity = StudyIntensity.BALANCED,
                preferredPeriod = PreferredStudyPeriod.AFTERNOON
            )
            dao.insertScheduleItems(initialSchedule)
        }
        if (dao.getDeckCount() > 0) return

        val physicsDeckId = dao.insertDeck(
            StudyDeckEntity(
                title = "الفيزياء — رزم غزة (التوجيهي العلمي)",
                subtitle = "الزخم الخطي، التصادمات، والحركة الدورية",
                category = "التوجيهي العلمي",
                accentHex = 0xFFE2C068
            )
        )
        val mathDeckId = dao.insertDeck(
            StudyDeckEntity(
                title = "الرياضيات — رزم غزة (التوجيهي)",
                subtitle = "التفاضل وتطبيقاته، التكامل، والمصفوفات",
                category = "التوجيهي العلمي",
                accentHex = 0xFF5EEAD4
            )
        )
        val arabicDeckId = dao.insertDeck(
            StudyDeckEntity(
                title = "اللغة العربية — رزم غزة التعليمية",
                subtitle = "البلاغة، القواعد، والمطالعة والنصوص",
                category = "مبحث مشترك",
                accentHex = 0xFF7AA2F7
            )
        )

        dao.insertFlashcards(
            listOf(
                FlashcardEntity(
                    deckId = physicsDeckId,
                    question = "ما نص قانون حفظ الزخم الخطي في الأنظمة المعزولة؟",
                    answer = "يبقى الزخم الخطي الكلي لنظام معزول ثابتاً ما لم تؤثر فيه قوى خارجية محصلة (∑Pi = ∑Pf).",
                    hint = "المجموع الاتجاهي قبل التصادم = بعده",
                    isMastered = true,
                    reviewCount = 3
                ),
                FlashcardEntity(
                    deckId = physicsDeckId,
                    question = "كيف تميّز بين التصادم المرن وغير المرن في أسئلة التوجيهي؟",
                    answer = "في التصادم المرن يُحفظ الزخم والطاقة الحركية معاً، أما غير المرن فيُحفظ الزخم فقط ولا تُحفظ الطاقة الحركية.",
                    hint = "تحقّق من ΔK قبل وبعد التصادم",
                    isMastered = false,
                    reviewCount = 1
                ),
                FlashcardEntity(
                    deckId = mathDeckId,
                    question = "متى يكون للاقتـران قيمة قصوى محلية عند نقطة حرجة؟",
                    answer = "عندما تتغير إشارة المشتقة الأولى ق'(س) حول النقطة الحرجة؛ من موجب إلى سالب (عظمى) أو من سالب إلى موجب (صغرى).",
                    hint = "اختبار المشتقة الأولى أو الثانية",
                    isMastered = true,
                    reviewCount = 2
                ),
                FlashcardEntity(
                    deckId = mathDeckId,
                    question = "ما العلاقة الأساسية في النظرية الأساسية للتكامل؟",
                    answer = "التكامل المحدود للاقتران ق(س) من أ إلى ب يساوي ت(ب) - ت(أ) حيث ت'(س) = ق(س).",
                    hint = "الاقتران الأصلي وحدود التكامل",
                    isMastered = false,
                    reviewCount = 1
                ),
                FlashcardEntity(
                    deckId = arabicDeckId,
                    question = "ما الفرق بين الاستعارة التصريحية والاستعارة المكنية؟",
                    answer = "التصريحية يُصرّح فيها بلفظ المشبه به ويُحذف المشبه، أما المكنية فيُذكر المشبه ويُحذف المشبه به ويُرمز له بأحد لوازمه.",
                    hint = "انظر إلى اللفظ المصرّح به في الجملة",
                    isMastered = true,
                    reviewCount = 2
                )
            )
        )

        dao.insertNote(
            StudyNoteEntity(
                deckId = physicsDeckId,
                title = "خلاصة رزمة الفيزياء الأولى — الميكانيكا",
                summaryContent = "يركّز مبحث الفيزياء في رزم التوجيهي العلمي لقطاع غزة على العلاقة بين الدفع والتغير في الزخم (I = ΔP)، وتطبيقات حفظ الزخم في التصادمات في بعد واحد وبعدين.",
                keyTerms = "الزخم الخطي • الدفع • التصادم المرن • النظام المعزول",
                isSampleAiOutput = false
            )
        )

        dao.insertFocusSession(
            FocusSessionEntity(
                durationMinutes = 25,
                subjectTitle = "الفيزياء — رزم غزة (التوجيهي العلمي)"
            )
        )
    }

    suspend fun createDeck(title: String, subtitle: String, category: String, accentHex: Long): Long {
        return dao.insertDeck(
            StudyDeckEntity(
                title = title.trim(),
                subtitle = subtitle.trim().ifBlank { "مجموعة مراجعة من رزم غزة" },
                category = category.trim().ifBlank { "رزم غزة" },
                accentHex = accentHex
            )
        )
    }

    suspend fun deleteDeck(deckId: Long) = dao.deleteDeckById(deckId)

    suspend fun addFlashcard(deckId: Long, question: String, answer: String, hint: String = "") {
        dao.insertFlashcard(
            FlashcardEntity(
                deckId = deckId,
                question = question.trim(),
                answer = answer.trim(),
                hint = hint.trim()
            )
        )
    }

    suspend fun addFlashcards(cards: List<FlashcardEntity>) = dao.insertFlashcards(cards)

    suspend fun toggleMastery(card: FlashcardEntity, mastered: Boolean) {
        dao.updateFlashcard(
            card.copy(
                isMastered = mastered,
                reviewCount = card.reviewCount + 1
            )
        )
    }

    suspend fun deleteFlashcard(cardId: Long) = dao.deleteFlashcardById(cardId)

    suspend fun saveNote(
        deckId: Long,
        title: String,
        summaryContent: String,
        keyTerms: String,
        isSampleAiOutput: Boolean
    ) {
        dao.insertNote(
            StudyNoteEntity(
                deckId = deckId,
                title = title.trim(),
                summaryContent = summaryContent.trim(),
                keyTerms = keyTerms.trim(),
                isSampleAiOutput = isSampleAiOutput
            )
        )
    }

    suspend fun deleteNote(noteId: Long) = dao.deleteNoteById(noteId)

    suspend fun saveChatMessage(
        packageId: String,
        isUser: Boolean,
        text: String,
        imageUri: String? = null,
        isSampleAiOutput: Boolean = false
    ) {
        dao.insertChatMessage(
            ChatMessageEntity(
                packageId = packageId,
                isUser = isUser,
                text = text,
                imageUri = imageUri,
                isSampleAiOutput = isSampleAiOutput
            )
        )
    }

    suspend fun clearChatHistory(packageId: String) = dao.clearChatHistory(packageId)

    suspend fun recordFocusSession(durationMinutes: Int, subjectTitle: String) {
        dao.insertFocusSession(
            FocusSessionEntity(
                durationMinutes = durationMinutes,
                subjectTitle = subjectTitle
            )
        )
    }

    suspend fun recordQuizAttempt(deckTitle: String, score: Int, totalQuestions: Int) {
        dao.insertQuizAttempt(
            QuizAttemptEntity(
                deckTitle = deckTitle,
                score = score,
                totalQuestions = totalQuestions
            )
        )
    }

    suspend fun replaceSchedule(items: List<StudyScheduleEntity>) {
        dao.clearScheduleItems()
        if (items.isNotEmpty()) {
            dao.insertScheduleItems(items)
        }
    }

    suspend fun addScheduleItem(item: StudyScheduleEntity): Long = dao.insertScheduleItem(item)

    suspend fun toggleScheduleCompleted(item: StudyScheduleEntity, completed: Boolean) {
        dao.updateScheduleItem(item.copy(isCompleted = completed))
    }

    suspend fun deleteScheduleItem(itemId: Long) = dao.deleteScheduleItemById(itemId)
}
