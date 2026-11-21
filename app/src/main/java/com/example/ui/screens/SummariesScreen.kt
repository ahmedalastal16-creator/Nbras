package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.VoicePlaybackState
import com.example.data.curriculum.GazaCurriculumCatalog
import com.example.data.curriculum.GazaStage
import com.example.data.local.StudyNoteEntity
import com.example.data.remote.CloudStudySummary
import com.example.ui.viewmodel.AuthAccountState

private enum class SummarySourceFilter(val id: String, val label: String) {
    ALL("all", "الكل"),
    AI_LAB("ai_lab", "المختبر الذكي"),
    TUTOR("tutor", "معلم نبراس"),
    MANUAL("manual", "مكتوب يدوياً")
}

@Composable
fun StudySummariesScreen(
    cloudSummaries: List<CloudStudySummary>,
    localNotes: List<StudyNoteEntity>,
    selectedStage: GazaStage,
    selectedSubject: String,
    authState: AuthAccountState,
    isSaving: Boolean,
    syncError: String?,
    voiceState: VoicePlaybackState,
    onBack: () -> Unit,
    onOpenSignIn: () -> Unit,
    onClearError: () -> Unit,
    onSaveSummary: (
        stage: GazaStage,
        subjectTitle: String,
        title: String,
        content: String,
        keyTerms: String,
        sourceType: String,
        existingId: String?
    ) -> Unit,
    onDeleteCloudSummary: (String) -> Unit,
    onToggleSpeak: (itemId: String, text: String) -> Unit,
    onAskTutorOnSummary: (subjectTitle: String, prompt: String) -> Unit,
    onCopiedNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf(SummarySourceFilter.ALL) }
    var showEditor by remember { mutableStateOf(false) }
    var editingSummary by remember { mutableStateOf<CloudStudySummary?>(null) }
    var prefillFromLocal by remember { mutableStateOf<StudyNoteEntity?>(null) }
    val clipboard = LocalClipboardManager.current

    if (showEditor) {
        val editTarget = editingSummary
        val localTarget = prefillFromLocal
        val initialStage = if (editTarget != null) {
            GazaCurriculumCatalog.stages.firstOrNull { it.id == editTarget.stageId } ?: selectedStage
        } else {
            selectedStage
        }
        CloudSummaryEditorDialog(
            initialStage = initialStage,
            initialSubject = editTarget?.subjectTitle ?: selectedSubject,
            initialTitle = editTarget?.title ?: localTarget?.title.orEmpty(),
            initialContent = editTarget?.content ?: localTarget?.summaryContent.orEmpty(),
            initialKeyTerms = editTarget?.keyTerms ?: localTarget?.keyTerms.orEmpty(),
            isEditing = editTarget != null,
            onDismiss = {
                showEditor = false
                editingSummary = null
                prefillFromLocal = null
            },
            onConfirm = { stage, subject, title, content, keyTerms ->
                onSaveSummary(
                    stage,
                    subject,
                    title,
                    content,
                    keyTerms,
                    editTarget?.sourceType ?: "manual",
                    editTarget?.id
                )
                showEditor = false
                editingSummary = null
                prefillFromLocal = null
            }
        )
    }

    val filteredCloud = remember(cloudSummaries, searchQuery, sourceFilter) {
        cloudSummaries.filter { item ->
            val matchesSource = sourceFilter == SummarySourceFilter.ALL || item.sourceType == sourceFilter.id
            val matchesQuery = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.subjectTitle.contains(searchQuery, ignoreCase = true) ||
                item.content.contains(searchQuery, ignoreCase = true) ||
                item.keyTerms.contains(searchQuery, ignoreCase = true)
            matchesSource && matchesQuery
        }
    }

    val filteredLocal = remember(localNotes, searchQuery, cloudSummaries) {
        val cloudTitles = cloudSummaries.map { it.title.trim() }.toSet()
        localNotes.filter { note ->
            !cloudTitles.contains(note.title.trim()) && (
                searchQuery.isBlank() ||
                    note.title.contains(searchQuery, ignoreCase = true) ||
                    note.summaryContent.contains(searchQuery, ignoreCase = true)
                )
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("summaries_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("summaries_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع"
                            )
                        }
                        Column {
                            Text(
                                text = "الملخصات السحابية",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "حفظ ومزامنة ملخصاتك عبر Firestore",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            editingSummary = null
                            prefillFromLocal = null
                            showEditor = true
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("summaries_add_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ملخص جديد")
                    }
                }
            }

            item {
                CloudSyncStatusBanner(
                    isSignedIn = authState.isSignedIn,
                    accountName = authState.displayName ?: authState.email,
                    cloudCount = cloudSummaries.size,
                    onOpenSignIn = onOpenSignIn
                )
            }

            if (syncError != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = syncError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onClearError) {
                                Text("إغلاق")
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث في العناوين أو المواد أو القوانين...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("summaries_search_input")
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(SummarySourceFilter.entries) { filter ->
                        FilterChip(
                            selected = sourceFilter == filter,
                            onClick = { sourceFilter = filter },
                            label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("summary_filter_${filter.id}")
                        )
                    }
                }
            }

            if (authState.isSignedIn) {
                if (filteredCloud.isEmpty()) {
                    item {
                        EmptySummariesCard(
                            title = if (cloudSummaries.isEmpty()) {
                                "لا توجد ملخصات سحابية بعد"
                            } else {
                                "لا توجد نتائج مطابقة للبحث"
                            },
                            subtitle = "اضغط «ملخص جديد» لتدوين ملخص ومزامنته فورياً على Firestore، أو احفظ ملخصاتك من المختبر الذكي."
                        )
                    }
                } else {
                    items(filteredCloud, key = { it.id }) { summary ->
                        val speakId = "cloud_sum_${summary.id}"
                        CloudSummaryCard(
                            summary = summary,
                            isSpeaking = voiceState.isSpeaking && voiceState.speakingItemId == speakId,
                            onToggleSpeak = {
                                onToggleSpeak(speakId, "${summary.title}. ${summary.content}")
                            },
                            onCopy = {
                                clipboard.setText(AnnotatedString("${summary.title}\n\n${summary.content}"))
                                onCopiedNotice("تم نسخ الملخص إلى الحافظة.")
                            },
                            onEdit = {
                                editingSummary = summary
                                prefillFromLocal = null
                                showEditor = true
                            },
                            onDelete = { onDeleteCloudSummary(summary.id) },
                            onAskTutor = {
                                onAskTutorOnSummary(
                                    summary.subjectTitle,
                                    "راجع معي هذا الملخص وأعطني أسئلة متوقعة عليه:\n${summary.title}\n${summary.content.take(500)}"
                                )
                            }
                        )
                    }
                }
            }

            if (filteredLocal.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (authState.isSignedIn) {
                            "ملخصات محفوظة محلياً (جاهزة للمزامنة السحابية)"
                        } else {
                            "ملخصاتك المحفوظة محلياً على الجهاز"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(filteredLocal, key = { "local_${it.id}" }) { note ->
                    val speakId = "local_sum_${note.id}"
                    LocalNoteSyncCard(
                        note = note,
                        isSpeaking = voiceState.isSpeaking && voiceState.speakingItemId == speakId,
                        onToggleSpeak = {
                            onToggleSpeak(speakId, "${note.title}. ${note.summaryContent}")
                        },
                        onSyncToCloud = {
                            onSaveSummary(
                                selectedStage,
                                selectedSubject,
                                note.title,
                                note.summaryContent,
                                note.keyTerms,
                                if (note.isSampleAiOutput) "ai_lab" else "manual",
                                null
                            )
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CloudSyncStatusBanner(
    isSignedIn: Boolean,
    accountName: String?,
    cloudCount: Int,
    onOpenSignIn: () -> Unit
) {
    val brush = Brush.horizontalGradient(
        colors = listOf(
            if (isSignedIn) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.surface
        )
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isSignedIn) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSignedIn) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = if (isSignedIn) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isSignedIn) {
                            "متزامن لحظياً مع Firestore ($cloudCount ملخص)"
                        } else {
                            "المزامنة السحابية متوقفة (وضع الضيف)"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSignedIn) {
                            "حساب: ${accountName ?: "طالب نبراس"} • محفوظ على السحابة"
                        } else {
                            "سجّل دخولك عبر Google أو Apple لمزامنة ملخصاتك والوصول إليها من أي جهاز."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!isSignedIn) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onOpenSignIn,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("summaries_signin_button")
                ) {
                    Text("دخول")
                }
            }
        }
    }
}

@Composable
private fun CloudSummaryCard(
    summary: CloudStudySummary,
    isSpeaking: Boolean,
    onToggleSpeak: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAskTutor: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val sourceLabel = when (summary.sourceType) {
        "ai_lab" -> "المختبر الذكي"
        "tutor" -> "معلم نبراس"
        else -> "ملخص شخصي"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .clickable { expanded = !expanded }
            .testTag("cloud_summary_card_${summary.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = summary.subjectTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "طي الملخص" else "توسيع الملخص",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = summary.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedVisibility(visible = expanded && summary.keyTerms.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "مصطلحات وقوانين: ${summary.keyTerms}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onToggleSpeak) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSpeaking) "إيقاف" else "استمع")
                    }
                    TextButton(onClick = onAskTutor) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ناقش")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "تعديل", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "حذف",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalNoteSyncCard(
    note: StudyNoteEntity,
    isSpeaking: Boolean,
    onToggleSpeak: () -> Unit,
    onSyncToCloud: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = note.summaryContent,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleSpeak) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSpeaking) "إيقاف" else "استمع")
                }
                OutlinedButton(
                    onClick = onSyncToCloud,
                    modifier = Modifier.testTag("sync_local_note_${note.id}")
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("رفع إلى Firestore")
                }
            }
        }
    }
}

@Composable
private fun EmptySummariesCard(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudSummaryEditorDialog(
    initialStage: GazaStage,
    initialSubject: String,
    initialTitle: String,
    initialContent: String,
    initialKeyTerms: String,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        stage: GazaStage,
        subjectTitle: String,
        title: String,
        content: String,
        keyTerms: String
    ) -> Unit
) {
    var selectedStage by remember { mutableStateOf(initialStage) }
    var selectedSubject by remember {
        mutableStateOf(
            if (initialStage.subjects.contains(initialSubject)) initialSubject
            else initialStage.subjects.first()
        )
    }
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }
    var keyTerms by remember { mutableStateOf(initialKeyTerms) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "تعديل الملخص السحابي" else "حفظ ملخص دراسي جديد") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("المرحلة التعليمية:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GazaCurriculumCatalog.stages, key = { it.id }) { stage ->
                        FilterChip(
                            selected = selectedStage.id == stage.id,
                            onClick = {
                                selectedStage = stage
                                if (!stage.subjects.contains(selectedSubject)) {
                                    selectedSubject = stage.subjects.first()
                                }
                            },
                            label = { Text(stage.badge, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("المادة:", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedStage.subjects.forEach { subj ->
                        FilterChip(
                            selected = selectedSubject == subj,
                            onClick = { selectedSubject = subj },
                            label = { Text(subj, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الملخص") },
                    placeholder = { Text("مثال: قوانين حفظ الزخم والتصادمات") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("summary_editor_title_input")
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("محتوى الملخص") },
                    placeholder = { Text("اكتب النقاط الأساسية والتعريفات والأمثلة...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("summary_editor_content_input")
                )

                OutlinedTextField(
                    value = keyTerms,
                    onValueChange = { keyTerms = it },
                    label = { Text("مصطلحات وقوانين مهمة (اختياري)") },
                    placeholder = { Text("الزخم • الدفع • النظام المعزول") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedStage, selectedSubject, title, content, keyTerms) },
                enabled = title.trim().isNotEmpty() && content.trim().isNotEmpty(),
                modifier = Modifier.testTag("summary_editor_save_button")
            ) {
                Text(if (isEditing) "تحديث وحفظ" else "حفظ ومزامنة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
