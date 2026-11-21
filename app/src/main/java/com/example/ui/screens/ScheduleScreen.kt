package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.curriculum.GazaStage
import com.example.data.curriculum.PreferredStudyPeriod
import com.example.data.curriculum.ScheduleGenerator
import com.example.data.curriculum.StudyIntensity
import com.example.data.local.StudyDeckEntity
import com.example.data.local.StudyScheduleEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    scheduleItems: List<StudyScheduleEntity>,
    selectedStage: GazaStage,
    decks: List<StudyDeckEntity>,
    onGenerateSmartSchedule: (
        selectedSubjects: List<String>,
        intensity: StudyIntensity,
        period: PreferredStudyPeriod
    ) -> Unit,
    onAddCustomItem: (
        dayIndex: Int,
        subjectTitle: String,
        topic: String,
        timeSlot: String,
        durationMinutes: Int,
        focusType: String
    ) -> Unit,
    onToggleCompleted: (StudyScheduleEntity, Boolean) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onStartFocusSession: (subjectTitle: String, durationMinutes: Int) -> Unit,
    onAskTutorOnSubject: (subjectTitle: String, suggestionPrompt: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val availableSubjects = remember(selectedStage.id, decks) {
        (selectedStage.subjects + decks.map { it.title }).distinct()
    }
    var pickedSubjects by remember(selectedStage.id) {
        mutableStateOf(selectedStage.subjects.take(4).toSet())
    }
    var selectedIntensity by remember { mutableStateOf(StudyIntensity.BALANCED) }
    var selectedPeriod by remember { mutableStateOf(PreferredStudyPeriod.AFTERNOON) }
    var selectedDayFilter by remember { mutableIntStateOf(-1) } // -1 = All days
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedStage.id) {
        pickedSubjects = selectedStage.subjects.take(4).toSet()
    }

    val filteredItems = remember(scheduleItems, selectedDayFilter) {
        if (selectedDayFilter == -1) scheduleItems else scheduleItems.filter { it.dayIndex == selectedDayFilter }
    }
    val completedCount = remember(scheduleItems) { scheduleItems.count { it.isCompleted } }
    val totalMinutes = remember(scheduleItems) { scheduleItems.sumOf { it.durationMinutes } }
    val progress = if (scheduleItems.isEmpty()) 0f else completedCount.toFloat() / scheduleItems.size.toFloat()

    if (showAddDialog) {
        AddScheduleSessionDialog(
            availableSubjects = availableSubjects,
            initialDayIndex = if (selectedDayFilter in 0..6) selectedDayFilter else 0,
            onDismiss = { showAddDialog = false },
            onConfirm = { dayIdx, subject, topic, slot, duration, focusType ->
                onAddCustomItem(dayIdx, subject, topic, slot, duration, focusType)
                showAddDialog = false
            }
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
                .testTag("schedule_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "الجدول الدراسي الذكي",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "خطة أسبوعية منظمة حسب مواد ${selectedStage.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("add_schedule_session_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حصة")
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "إنجاز الأسبوع: $completedCount من ${scheduleItems.size} حصص",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${totalMinutes / 60} س ${totalMinutes % 60} د",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
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
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "صمّم جدولك حسب المواد والوقت",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "اختر المواد ذات الأولوية:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableSubjects.forEach { subject ->
                                val selected = pickedSubjects.contains(subject)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        pickedSubjects = if (selected && pickedSubjects.size > 1) {
                                            pickedSubjects - subject
                                        } else {
                                            pickedSubjects + subject
                                        }
                                    },
                                    label = {
                                        Text(subject, style = MaterialTheme.typography.labelSmall)
                                    }
                                )
                            }
                        }

                        Text(
                            text = "كثافة الدراسة اليومية:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(StudyIntensity.entries) { intensity ->
                                FilterChip(
                                    selected = selectedIntensity == intensity,
                                    onClick = { selectedIntensity = intensity },
                                    label = { Text(intensity.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Text(
                            text = "الفترة المفضلة:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PreferredStudyPeriod.entries) { period ->
                                FilterChip(
                                    selected = selectedPeriod == period,
                                    onClick = { selectedPeriod = period },
                                    label = { Text(period.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onGenerateSmartSchedule(
                                        pickedSubjects.toList(),
                                        selectedIntensity,
                                        selectedPeriod
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("generate_schedule_button")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("توليد الجدول")
                            }

                            OutlinedButton(
                                onClick = {
                                    val firstSubject = pickedSubjects.firstOrNull() ?: selectedStage.subjects.first()
                                    val subjectsJoined = pickedSubjects.joinToString("، ")
                                    val aiPrompt = "اقترح لي خطة مذاكرة ذكية وتوزيع ساعات لأسبوع كامل لمواد ($subjectsJoined) في ${selectedStage.title} حسب الرزم التعليمية."
                                    onAskTutorOnSubject(firstSubject, aiPrompt)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("schedule_ask_ai_button")
                            ) {
                                Text("استشارة نبراس")
                            }
                        }
                    }
                }
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("schedule_day_filter_row")
                ) {
                    item {
                        FilterChip(
                            selected = selectedDayFilter == -1,
                            onClick = { selectedDayFilter = -1 },
                            label = { Text("كل الأسبوع") }
                        )
                    }
                    items(ScheduleGenerator.weekDays.size) { dayIndex ->
                        FilterChip(
                            selected = selectedDayFilter == dayIndex,
                            onClick = { selectedDayFilter = dayIndex },
                            label = { Text(ScheduleGenerator.weekDays[dayIndex]) },
                            modifier = Modifier.testTag("schedule_day_chip_$dayIndex")
                        )
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = "لا توجد حصص مجدولة في هذا اليوم",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "اضغط «توليد الجدول» أعلاه أو أضف حصة مخصصة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    ScheduleSessionCard(
                        item = item,
                        onToggleCompleted = { onToggleCompleted(item, !item.isCompleted) },
                        onStartFocus = { onStartFocusSession(item.subjectTitle, item.durationMinutes) },
                        onAskTutor = {
                            onAskTutorOnSubject(
                                item.subjectTitle,
                                "ساعدني في إنجاز حصة: ${item.topic}"
                            )
                        },
                        onDelete = { onDeleteItem(item.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ScheduleSessionCard(
    item: StudyScheduleEntity,
    onToggleCompleted: () -> Unit,
    onStartFocus: () -> Unit,
    onAskTutor: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = Color(item.accentHex)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleCompleted),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.isCompleted) MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f) else accent.copy(alpha = 0.42f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent)
                )

                IconButton(
                    onClick = onToggleCompleted,
                    modifier = Modifier.testTag("schedule_toggle_${item.id}")
                ) {
                    Icon(
                        imageVector = if (item.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (item.isCompleted) "مكتملة" else "تحديد كمكتملة",
                        tint = if (item.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = accent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "${item.dayLabel} • ${item.timeSlot}",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = "${item.durationMinutes} د",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.subjectTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null
                    )
                    Text(
                        text = "${item.focusType}: ${item.topic}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "حذف الحصة",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onStartFocus,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ابدأ تركيز", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onAskTutor,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ذاكر مع نبراس", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddScheduleSessionDialog(
    availableSubjects: List<String>,
    initialDayIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (
        dayIndex: Int,
        subjectTitle: String,
        topic: String,
        timeSlot: String,
        durationMinutes: Int,
        focusType: String
    ) -> Unit
) {
    var selectedDay by remember { mutableIntStateOf(initialDayIndex) }
    var selectedSubject by remember { mutableStateOf(availableSubjects.firstOrNull() ?: "الرياضيات") }
    var topic by remember { mutableStateOf("") }
    var timeSlot by remember { mutableStateOf("04:00 م") }
    var duration by remember { mutableIntStateOf(45) }
    var focusType by remember { mutableStateOf("فهم وشرح الرزمة") }

    val focusOptions = listOf("فهم وشرح الرزمة", "حل مسائل وتمارين", "بطاقات مراجعة", "اختبار قصير")
    val durationOptions = listOf(30, 45, 60, 90)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة حصة للجدول") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("اليوم:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ScheduleGenerator.weekDays.size) { idx ->
                        FilterChip(
                            selected = selectedDay == idx,
                            onClick = { selectedDay = idx },
                            label = { Text(ScheduleGenerator.weekDays[idx], style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("المادة:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(availableSubjects) { sub ->
                        FilterChip(
                            selected = selectedSubject == sub,
                            onClick = { selectedSubject = sub },
                            label = { Text(sub, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("الدرس أو المهمة المطلوبة") },
                    placeholder = { Text("مثال: حل أسئلة الرزمة الثانية") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = timeSlot,
                    onValueChange = { timeSlot = it },
                    label = { Text("الوقت") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    durationOptions.forEach { mins ->
                        FilterChip(
                            selected = duration == mins,
                            onClick = { duration = mins },
                            label = { Text("$mins د", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    focusOptions.forEach { type ->
                        FilterChip(
                            selected = focusType == type,
                            onClick = { focusType = type },
                            label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        selectedDay,
                        selectedSubject,
                        topic.ifBlank { "مراجعة رزمة $selectedSubject" },
                        timeSlot.ifBlank { "04:00 م" },
                        duration,
                        focusType
                    )
                }
            ) {
                Text("إضافة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
