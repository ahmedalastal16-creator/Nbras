package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.audio.VoicePlaybackState
import com.example.data.curriculum.GazaStage
import com.example.data.local.ChatMessageEntity
import com.example.data.local.StudyDeckEntity
import com.example.data.remote.ConversationSummary
import com.example.data.repository.AiSpeedMode
import com.example.ui.components.ChooseDeckDialog
import com.example.ui.components.GazaCurriculumLiveBanner
import com.example.ui.components.GazaPackageSelectorRow
import com.example.ui.components.ModernAiThinkingCard
import com.example.ui.components.SampleOutputPill
import com.example.ui.viewmodel.AuthAccountState

@Composable
fun TutorScreen(
    messages: List<ChatMessageEntity>,
    savedConversations: List<ConversationSummary>,
    activePackageId: String,
    authState: AuthAccountState,
    decks: List<StudyDeckEntity>,
    selectedStage: GazaStage,
    selectedSubject: String,
    speedMode: AiSpeedMode,
    autoSpeakTutor: Boolean,
    voiceState: VoicePlaybackState,
    isThinking: Boolean,
    elapsedSeconds: Int,
    streamingText: String?,
    errorMessage: String?,
    onSelectStage: (GazaStage) -> Unit,
    onSelectSubject: (String) -> Unit,
    onSelectSavedConversation: (ConversationSummary) -> Unit,
    onOpenSignIn: () -> Unit,
    onSelectSpeedMode: (AiSpeedMode) -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onToggleSpeak: (itemId: String, text: String) -> Unit,
    onSendMessage: (prompt: String, imageUri: String?) -> Unit,
    onCancelRequest: () -> Unit,
    onSaveReplyToDeck: (deckId: Long, replyText: String, isSample: Boolean) -> Unit,
    onSaveReplyToCloud: (replyText: String) -> Unit = {},
    onCopiedNotice: (String) -> Unit,
    onRetryLastMessage: () -> Unit,
    onDismissError: () -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputPrompt by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var replyPendingSave by remember { mutableStateOf<ChatMessageEntity?>(null) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedImageUri = uri
        }
    }

    val speechInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = spokenList?.firstOrNull()?.trim()
            if (!recognized.isNullOrBlank()) {
                inputPrompt = if (inputPrompt.isBlank()) {
                    recognized
                } else {
                    "$inputPrompt $recognized"
                }
            }
        }
    }

    if (replyPendingSave != null) {
        ChooseDeckDialog(
            title = "حفظ الشرح في مادة",
            decks = decks,
            onSelectDeck = { deck ->
                val target = replyPendingSave
                if (target != null) {
                    onSaveReplyToDeck(deck.id, target.text, target.isSampleAiOutput)
                }
                replyPendingSave = null
            },
            onDismiss = { replyPendingSave = null }
        )
    }

    LaunchedEffect(isThinking) {
        if (isThinking) {
            inputPrompt = ""
            attachedImageUri = null
        }
    }

    LaunchedEffect(messages.size, !streamingText.isNullOrBlank(), isThinking) {
        val targetIndex = when {
            !streamingText.isNullOrBlank() || isThinking -> messages.size
            messages.isNotEmpty() -> messages.lastIndex
            else -> -1
        }
        if (targetIndex >= 0) {
            listState.scrollToItem(targetIndex)
        }
    }

    val quickPrompts = remember(selectedSubject, selectedStage.id) {
        listOf(
            "اقترح لي جدول دراسة أسبوعي لمواد ${selectedStage.badge}",
            "كيف أنظّم وقتي لدراسة رزمة $selectedSubject؟",
            "اشرح أهم أفكار رزمة $selectedSubject",
            "أعطني سؤالاً على نمط التوجيهي في $selectedSubject",
            "لخّص القوانين الأساسية في $selectedSubject"
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "معلم رزم غزة الذكي",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (authState.isSignedIn) {
                            "محادثة مستقلة ومحفوظة لرزمة $selectedSubject"
                        } else {
                            "تصفّح بحرية • يُطلب تسجيل الدخول عند إرسال السؤال"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!authState.isSignedIn) {
                        AssistChip(
                            onClick = onOpenSignIn,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("دخول") },
                            modifier = Modifier.testTag("tutor_signin_chip")
                        )
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_tutor_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "مسح محادثة هذه الرزمة",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            GazaCurriculumLiveBanner(
                stageTitle = selectedStage.title,
                subjectTitle = selectedSubject
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = speedMode == AiSpeedMode.FAST,
                        onClick = { onSelectSpeedMode(AiSpeedMode.FAST) },
                        leadingIcon = {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("فوري مجاني") },
                        modifier = Modifier.testTag("speed_mode_fast")
                    )
                }
                item {
                    FilterChip(
                        selected = speedMode == AiSpeedMode.DETAILED,
                        onClick = { onSelectSpeedMode(AiSpeedMode.DETAILED) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("شرح مفصّل") },
                        modifier = Modifier.testTag("speed_mode_detailed")
                    )
                }
                item {
                    FilterChip(
                        selected = autoSpeakTutor,
                        onClick = onToggleAutoSpeak,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("نطق تلقائي") },
                        modifier = Modifier.testTag("tutor_auto_speak_chip")
                    )
                }
            }

            GazaPackageSelectorRow(
                selectedStage = selectedStage,
                selectedSubject = selectedSubject,
                onSelectStage = onSelectStage,
                onSelectSubject = onSelectSubject
            )

            if (savedConversations.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("saved_conversations_row")
                ) {
                    items(savedConversations, key = { it.id }) { conv ->
                        val isCurrent = conv.id == activePackageId
                        FilterChip(
                            selected = isCurrent,
                            onClick = { onSelectSavedConversation(conv) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Forum,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "${conv.subjectTitle} (${conv.messageCount})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("saved_conv_${conv.id}")
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onRetryLastMessage,
                            modifier = Modifier.testTag("tutor_retry_button")
                        ) {
                            Text(
                                text = "إعادة",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        IconButton(onClick = onDismissError) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "إغلاق التنبيه",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && streamingText == null && !isThinking) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_ai_tutor_crest),
                            contentDescription = stringResource(R.string.ai_crest_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "اسأل نبراس عن رزمة $selectedSubject",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "اكتب سؤالك أو استخدم الميكروفون، وسيشرحه لك المعلم نصياً وبالصوت الواضح.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(quickPrompts) { suggestion ->
                                AssistChip(
                                    onClick = { inputPrompt = suggestion },
                                    label = {
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val msgSpeakId = "msg_${msg.id}"
                            val isSpeakingThis = voiceState.isSpeaking &&
                                (voiceState.speakingItemId == msgSpeakId ||
                                    (voiceState.speakingItemId == "tutor_auto" && msg.id == messages.lastOrNull { !it.isUser }?.id))
                            TutorMessageBubble(
                                message = msg,
                                isSpeakingThis = isSpeakingThis,
                                onToggleSpeak = { onToggleSpeak(msgSpeakId, msg.text) },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(msg.text))
                                    onCopiedNotice("تم نسخ الرد إلى الحافظة.")
                                },
                                onSaveToDeck = { replyPendingSave = msg },
                                onSaveToCloud = { onSaveReplyToCloud(msg.text) }
                            )
                        }

                        if (!streamingText.isNullOrBlank()) {
                            item(key = "streaming_reply") {
                                Surface(
                                    modifier = Modifier
                                        .widthIn(max = 520.dp)
                                        .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                                    shape = RoundedCornerShape(
                                        topStart = 20.dp,
                                        topEnd = 20.dp,
                                        bottomStart = 6.dp,
                                        bottomEnd = 20.dp
                                    ),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SampleOutputPill(label = "جارٍ الشرح • Gemini")
                                            TextButton(
                                                onClick = onCancelRequest,
                                                modifier = Modifier.testTag("tutor_cancel_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Stop,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("إيقاف")
                                            }
                                        }
                                        Text(
                                            text = streamingText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        } else if (isThinking) {
                            item(key = "thinking_indicator") {
                                ModernAiThinkingCard(
                                    subjectTitle = selectedSubject,
                                    elapsedSeconds = elapsedSeconds,
                                    phases = listOf(
                                        "نبراس يحلّل سؤالك في $selectedSubject...",
                                        "يصيغ الإجابة بأسلوب رزم غزة الواضح...",
                                        "يرتّب الخطوات والأمثلة لسهولة الفهم..."
                                    ),
                                    cancelButtonTag = "tutor_cancel_button",
                                    onCancel = onCancelRequest
                                )
                            }
                        }
                    }
                }
            }

            if (attachedImageUri != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = attachedImageUri,
                            contentDescription = "صورة مرفقة",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Text(
                            text = "تم إرفاق صورة من الرزمة أو الدفتر",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { attachedImageUri = null },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "إزالة الصورة"
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tutor_attach_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.attach_image_desc),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-PS")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث بسؤالك عن الدرس...")
                            }
                            speechInputLauncher.launch(intent)
                        } catch (_: Exception) {
                            onCopiedNotice("الإدخال الصوتي غير متوفر على هذا الجهاز؛ يمكنك كتابة السؤال.")
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tutor_mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "اسأل بصوتك",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = { Text("اسأل عن رزمة $selectedSubject...") },
                    maxLines = 3,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tutor_prompt_input")
                )

                FilledIconButton(
                    onClick = {
                        onSendMessage(inputPrompt, attachedImageUri?.toString())
                    },
                    enabled = !isThinking && (inputPrompt.isNotBlank() || attachedImageUri != null),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("tutor_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.send_message_desc)
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorMessageBubble(
    message: ChatMessageEntity,
    isSpeakingThis: Boolean,
    onToggleSpeak: () -> Unit,
    onCopy: () -> Unit,
    onSaveToDeck: () -> Unit,
    onSaveToCloud: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.isUser) 20.dp else 6.dp,
                bottomEnd = if (message.isUser) 6.dp else 20.dp
            ),
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(
                1.dp,
                if (message.isUser) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!message.isUser) {
                    SampleOutputPill(label = "رزم غزة • Gemini AI")
                }
                if (!message.imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = Uri.parse(message.imageUri),
                        contentDescription = "صورة الملاحظة المرسلة",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!message.isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onToggleSpeak,
                            modifier = Modifier.testTag("tutor_speak_button_${message.id}")
                        ) {
                            Icon(
                                imageVector = if (isSpeakingThis) {
                                    Icons.Filled.Stop
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = null,
                                tint = if (isSpeakingThis) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSpeakingThis) "إيقاف الصوت" else "استمع للشرح",
                                color = if (isSpeakingThis) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onCopy,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "نسخ",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onSaveToDeck,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BookmarkAdd,
                                    contentDescription = "حفظ في المادة",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onSaveToCloud,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("tutor_save_cloud_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudUpload,
                                    contentDescription = "مزامنة في الملخصات السحابية",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
