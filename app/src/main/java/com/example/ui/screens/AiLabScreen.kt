package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.audio.VoicePlaybackState
import com.example.data.curriculum.GazaStage
import com.example.data.local.StudyDeckEntity
import com.example.data.repository.AiSpeedMode
import com.example.data.repository.AiSummaryResult
import com.example.data.repository.GeneratedFlashcard
import com.example.ui.components.AiSpeedModeRow
import com.example.ui.components.ChooseDeckDialog
import com.example.ui.components.GazaCurriculumLiveBanner
import com.example.ui.components.GazaPackageSelectorRow
import com.example.ui.components.ModernAiThinkingCard
import com.example.ui.components.SampleOutputPill
import com.example.ui.state.AiLabMode
import com.example.ui.state.AiLabOutputState

@Composable
fun AiLabScreen(
    mode: AiLabMode,
    outputState: AiLabOutputState,
    decks: List<StudyDeckEntity>,
    selectedStage: GazaStage,
    selectedSubject: String,
    speedMode: AiSpeedMode,
    voiceState: VoicePlaybackState,
    elapsedSeconds: Int,
    onSelectStage: (GazaStage) -> Unit,
    onSelectSubject: (String) -> Unit,
    onSelectSpeedMode: (AiSpeedMode) -> Unit,
    onSelectMode: (AiLabMode) -> Unit,
    onRunAiLab: (inputNotes: String, imageUri: String?) -> Unit,
    onCancelLabRequest: () -> Unit,
    onToggleSpeak: (itemId: String, text: String) -> Unit,
    onSelectQuizOption: (Int) -> Unit,
    onAdvanceQuiz: () -> Unit,
    onResetOutput: () -> Unit,
    onSaveSummaryToDeck: (deckId: Long, summary: AiSummaryResult) -> Unit,
    onSaveSummaryToCloud: (summary: AiSummaryResult) -> Unit,
    onSaveFlashcardsToDeck: (deckId: Long, cards: List<GeneratedFlashcard>) -> Unit,
    onCopiedNotice: (String) -> Unit,
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    var notesInput by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showSaveSummaryDialog by remember { mutableStateOf(false) }
    var showSaveCardsDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val sampleTopics = remember(selectedSubject) {
        listOf(
            "أسئلة توجيهي وزاري سابق — $selectedSubject",
            "الرزمة الأولى في $selectedSubject",
            "أهم القوانين والمفاهيم في $selectedSubject",
            "نموذج امتحان شامل — $selectedSubject"
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) attachedImageUri = uri
    }

    if (showSaveSummaryDialog && outputState is AiLabOutputState.SummaryReady) {
        ChooseDeckDialog(
            title = "حفظ الملخص في مادة",
            decks = decks,
            onSelectDeck = { deck ->
                onSaveSummaryToDeck(deck.id, outputState.summary)
                showSaveSummaryDialog = false
            },
            onDismiss = { showSaveSummaryDialog = false }
        )
    }

    if (showSaveCardsDialog && outputState is AiLabOutputState.FlashcardsReady) {
        ChooseDeckDialog(
            title = "حفظ البطاقات في مادة",
            decks = decks,
            onSelectDeck = { deck ->
                onSaveFlashcardsToDeck(deck.id, outputState.cards)
                showSaveCardsDialog = false
            },
            onDismiss = { showSaveCardsDialog = false }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("ai_lab_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "المختبر الذكي لرزم غزة",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "توليد ملخصات وبطاقات واختبارات وزارية عبر Gemini AI",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                GazaCurriculumLiveBanner(
                    stageTitle = selectedStage.title,
                    subjectTitle = selectedSubject
                )
            }

            item {
                GazaPackageSelectorRow(
                    selectedStage = selectedStage,
                    selectedSubject = selectedSubject,
                    onSelectStage = onSelectStage,
                    onSelectSubject = onSelectSubject
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مقترح ذكي: جدول دراسة حسب موادك",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "وزّع مواد ${selectedStage.badge} على الأسبوع بضغطة زر.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onOpenSchedule,
                            modifier = Modifier.testTag("lab_open_schedule_button")
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("الجدول")
                        }
                    }
                }
            }

            item {
                AiSpeedModeRow(
                    speedMode = speedMode,
                    onSelectSpeedMode = onSelectSpeedMode
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == AiLabMode.SUMMARY,
                        onClick = { onSelectMode(AiLabMode.SUMMARY) },
                        leadingIcon = {
                            Icon(Icons.Filled.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("تلخيص") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lab_mode_summary")
                    )
                    FilterChip(
                        selected = mode == AiLabMode.FLASHCARDS,
                        onClick = { onSelectMode(AiLabMode.FLASHCARDS) },
                        leadingIcon = {
                            Icon(Icons.Filled.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("بطاقات") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lab_mode_flashcards")
                    )
                    FilterChip(
                        selected = mode == AiLabMode.QUIZ,
                        onClick = { onSelectMode(AiLabMode.QUIZ) },
                        leadingIcon = {
                            Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("امتحان توجيهي") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lab_mode_quiz")
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sampleTopics) { topic ->
                                AssistChip(
                                    onClick = { notesInput = topic },
                                    label = { Text(topic, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = notesInput,
                            onValueChange = { notesInput = it },
                            label = { Text("عنوان الدرس أو نص من رزمة $selectedSubject") },
                            placeholder = { Text("مثال: درس التصادمات أو الوحدة الأولى...") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lab_notes_input")
                        )

                        if (attachedImageUri != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AsyncImage(
                                        model = attachedImageUri,
                                        contentDescription = "صورة مرفقة للمختبر",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Text(
                                        text = "تم إرفاق صورة صفحة من الرزمة",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { attachedImageUri = null }) {
                                        Icon(Icons.Filled.Close, contentDescription = "إزالة الصورة")
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("lab_attach_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("صورة")
                            }

                            Button(
                                onClick = {
                                    onRunAiLab(notesInput, attachedImageUri?.toString())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("lab_generate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when (mode) {
                                        AiLabMode.SUMMARY -> "توليد الملخص"
                                        AiLabMode.FLASHCARDS -> "توليد البطاقات"
                                        AiLabMode.QUIZ -> "توليد امتحان توجيهي سابق"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            when (outputState) {
                AiLabOutputState.Idle -> {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "اختر مبحثك من رزم غزة أعلاه وأدخل اسم الدرس أو صورة من الرزمة ليقوم Gemini بإنشاء ملخص أو بطاقات أو اختبار بنمط امتحانات التوجيهي القديمة.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(22.dp)
                            )
                        }
                    }
                }

                AiLabOutputState.Loading -> {
                    item {
                        val phases = when (mode) {
                            AiLabMode.SUMMARY -> listOf(
                                "نبراس يستخلص أهم أفكار $selectedSubject...",
                                "ينظّم النقاط والقوانين المفتاحية...",
                                "يضيف تنبيهاً امتحانياً وفق رزم غزة..."
                            )
                            AiLabMode.FLASHCARDS -> listOf(
                                "يصمّم بطاقات استرجاع نشط لمبحث $selectedSubject...",
                                "يضبط الأسئلة والإجابات القصيرة الدقيقة..."
                            )
                            AiLabMode.QUIZ -> listOf(
                                "يستحضر نماذج امتحانات التوجيهي الوزارية القديمة في $selectedSubject...",
                                "يصوغ الأسئلة والمشتتات بأسلوب الدورات السابقة...",
                                "يُعد التعليل الوزاري المعتمد لكل سؤال..."
                            )
                        }
                        ModernAiThinkingCard(
                            subjectTitle = selectedSubject,
                            elapsedSeconds = elapsedSeconds,
                            phases = phases,
                            cancelButtonTag = "lab_cancel_button",
                            onCancel = onCancelLabRequest
                        )
                    }
                }

                is AiLabOutputState.Error -> {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = outputState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                is AiLabOutputState.SummaryReady -> {
                    item {
                        val summarySpeakText = buildString {
                            appendLine(outputState.summary.title)
                            appendLine(outputState.summary.overview)
                            outputState.summary.bulletPoints.forEach { appendLine(it) }
                            if (outputState.summary.examTip.isNotBlank()) {
                                appendLine("تنبيه امتحاني: ${outputState.summary.examTip}")
                            }
                        }
                        SummaryResultCard(
                            summary = outputState.summary,
                            isSpeakingThis = voiceState.isSpeaking && voiceState.speakingItemId == "lab_summary",
                            onToggleSpeak = { onToggleSpeak("lab_summary", summarySpeakText) },
                            onSaveClick = { showSaveSummaryDialog = true },
                            onSaveToCloudClick = { onSaveSummaryToCloud(outputState.summary) },
                            onCopyClick = {
                                val text = buildString {
                                    appendLine(outputState.summary.title)
                                    appendLine(outputState.summary.overview)
                                    outputState.summary.bulletPoints.forEachIndexed { i, b ->
                                        appendLine("${i + 1}. $b")
                                    }
                                }
                                clipboard.setText(AnnotatedString(text))
                                onCopiedNotice("تم نسخ الملخص إلى الحافظة.")
                            }
                        )
                    }
                }

                is AiLabOutputState.FlashcardsReady -> {
                    item {
                        GeneratedFlashcardsSection(
                            cards = outputState.cards,
                            voiceState = voiceState,
                            onToggleSpeak = onToggleSpeak,
                            onSaveAllClick = { showSaveCardsDialog = true }
                        )
                    }
                }

                is AiLabOutputState.QuizReady -> {
                    item {
                        QuizRunnerCard(
                            state = outputState,
                            voiceState = voiceState,
                            onToggleSpeak = onToggleSpeak,
                            onSelectOption = onSelectQuizOption,
                            onAdvance = onAdvanceQuiz,
                            onReset = onResetOutput
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryResultCard(
    summary: AiSummaryResult,
    isSpeakingThis: Boolean,
    onToggleSpeak: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveToCloudClick: () -> Unit,
    onCopyClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SampleOutputPill(label = "ملخص رزم غزة • Gemini")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleSpeak,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("lab_speak_summary_button")
                    ) {
                        Icon(
                            imageVector = if (isSpeakingThis) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isSpeakingThis) "إيقاف قراءة الملخص" else "استمع للملخص",
                            tint = if (isSpeakingThis) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(
                        onClick = onCopyClick,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "نسخ الملخص"
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveClick,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("save_summary_to_deck_button")
                ) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ في مادة")
                }
                Button(
                    onClick = onSaveToCloudClick,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("save_summary_to_cloud_button")
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مزامنة Firestore")
                }
            }

            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = summary.overview,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            summary.bulletPoints.forEachIndexed { idx, point ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${idx + 1}. $point",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                summary.keyTerms.forEach { term ->
                    AssistChip(onClick = {}, label = { Text(term) })
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ) {
                Text(
                    text = summary.examTip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun GeneratedFlashcardsSection(
    cards: List<GeneratedFlashcard>,
    voiceState: VoicePlaybackState,
    onToggleSpeak: (itemId: String, text: String) -> Unit,
    onSaveAllClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SampleOutputPill(label = "بطاقات رزم غزة • Gemini")
            Button(
                onClick = onSaveAllClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("save_generated_cards_button")
            ) {
                Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("حفظ (${cards.size}) في مادة")
            }
        }

        cards.forEachIndexed { idx, card ->
            val cardSpeakId = "lab_card_$idx"
            val isSpeaking = voiceState.isSpeaking && voiceState.speakingItemId == cardSpeakId
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = card.question,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onToggleSpeak(cardSpeakId, "${card.question}. الإجابة: ${card.answer}") },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isSpeaking) "إيقاف القراءة" else "استمع للبطاقة",
                                tint = if (isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Text(
                        text = card.answer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun QuizRunnerCard(
    state: AiLabOutputState.QuizReady,
    voiceState: VoicePlaybackState,
    onToggleSpeak: (itemId: String, text: String) -> Unit,
    onSelectOption: (Int) -> Unit,
    onAdvance: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        if (state.isCompleted) {
            val percent = if (state.questions.isEmpty()) 0 else (state.score * 100) / state.questions.size
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "اكتمل الاختبار!",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "النتيجة: ${state.score} من ${state.questions.size} ($percent%)",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = onReset,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("اختبار جديد")
                }
            }
        } else {
            val currentQ = state.questions[state.currentIndex]
            val quizSpeakId = "lab_quiz_${state.currentIndex}"
            val isSpeakingQuiz = voiceState.isSpeaking && voiceState.speakingItemId == quizSpeakId
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "السؤال ${state.currentIndex + 1} من ${state.questions.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val textToRead = if (state.selectedOptionIndex != null) {
                                    "${currentQ.question}. التوضيح: ${currentQ.explanation}"
                                } else {
                                    currentQ.question
                                }
                                onToggleSpeak(quizSpeakId, textToRead)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (isSpeakingQuiz) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isSpeakingQuiz) "إيقاف القراءة" else "قراءة السؤال",
                                tint = if (isSpeakingQuiz) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                        }
                        SampleOutputPill(
                            label = currentQ.examReference.ifBlank { "نمط توجيهي وزاري سابق" }
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { (state.currentIndex + 1).toFloat() / state.questions.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                )

                Text(
                    text = currentQ.question,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                currentQ.options.forEachIndexed { idx, option ->
                    val selected = state.selectedOptionIndex
                    val isAnswered = selected != null
                    val borderColor = when {
                        !isAnswered -> MaterialTheme.colorScheme.outline
                        idx == currentQ.correctIndex -> MaterialTheme.colorScheme.secondary
                        idx == selected -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    val bgColor = when {
                        !isAnswered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        idx == currentQ.correctIndex -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        idx == selected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("quiz_option_$idx")
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = !isAnswered) { onSelectOption(idx) },
                        shape = RoundedCornerShape(14.dp),
                        color = bgColor,
                        border = BorderStroke(1.2.dp, borderColor)
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }

                if (state.selectedOptionIndex != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "التوضيح: ${currentQ.explanation}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                    Button(
                        onClick = onAdvance,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("quiz_next_button")
                    ) {
                        Text(
                            if (state.currentIndex == state.questions.lastIndex) {
                                "عرض النتيجة النهائية"
                            } else {
                                "السؤال التالي"
                            }
                        )
                    }
                }
            }
        }
    }
}
