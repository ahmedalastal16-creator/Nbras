package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.VoicePlaybackState
import com.example.data.local.StudyNoteEntity
import com.example.ui.components.CreateDeckDialog
import com.example.ui.components.DeckLuxuryCard
import com.example.ui.components.SampleOutputPill
import com.example.ui.state.DeckWithStats

@Composable
fun LibraryScreen(
    decks: List<DeckWithStats>,
    notes: List<StudyNoteEntity>,
    cloudSummariesCount: Int,
    voiceState: VoicePlaybackState,
    onToggleSpeak: (itemId: String, text: String) -> Unit,
    onOpenDeck: (Long) -> Unit,
    onOpenSummaries: () -> Unit,
    onCreateDeck: (title: String, subtitle: String, category: String, accentHex: Long) -> Unit,
    onDeleteNote: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateDeck by remember { mutableStateOf(false) }

    if (showCreateDeck) {
        CreateDeckDialog(
            onDismiss = { showCreateDeck = false },
            onConfirm = { title, subtitle, category, accentHex ->
                onCreateDeck(title, subtitle, category, accentHex)
                showCreateDeck = false
            }
        )
    }

    val filteredDecks = remember(decks, searchQuery) {
        if (searchQuery.isBlank()) decks
        else decks.filter {
            it.deck.title.contains(searchQuery, ignoreCase = true) ||
                it.deck.category.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredNotes = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) notes
        else notes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.summaryContent.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "المكتبة الدراسية",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${decks.size} مواد • ${notes.size} ملخصات محفوظة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showCreateDeck = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("library_add_deck_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مادة")
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.48f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    text = "الملخصات الدراسية السحابية",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$cloudSummariesCount ملخصات متزامنة عبر Firestore",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenSummaries,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("library_open_summaries_button")
                        ) {
                            Text("فتح الواجهة")
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث في المواد أو الملخصات...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_search_input")
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("المواد (${filteredDecks.size})") },
                        modifier = Modifier.testTag("library_tab_decks")
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("الملخصات (${filteredNotes.size})") },
                        modifier = Modifier.testTag("library_tab_notes")
                    )
                }
            }

            if (selectedTab == 0) {
                if (filteredDecks.isEmpty()) {
                    item {
                        EmptyLibraryNotice("لا توجد مواد مطابقة لبحثك حالياً.")
                    }
                } else {
                    items(filteredDecks, key = { it.deck.id }) { item ->
                        DeckLuxuryCard(
                            deckWithStats = item,
                            onClick = { onOpenDeck(item.deck.id) }
                        )
                    }
                }
            } else {
                if (filteredNotes.isEmpty()) {
                    item {
                        EmptyLibraryNotice("لم تحفظ ملخصات بعد. يمكنك حفظ الملخصات من المختبر الذكي.")
                    }
                } else {
                    items(filteredNotes, key = { it.id }) { note ->
                        val noteSpeakId = "note_${note.id}"
                        val isSpeakingNote = voiceState.isSpeaking && voiceState.speakingItemId == noteSpeakId
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (note.isSampleAiOutput) {
                                        SampleOutputPill()
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onToggleSpeak(noteSpeakId, "${note.title}. ${note.summaryContent}") },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = if (isSpeakingNote) {
                                                    Icons.Filled.Stop
                                                } else {
                                                    Icons.AutoMirrored.Filled.VolumeUp
                                                },
                                                contentDescription = if (isSpeakingNote) "إيقاف القراءة" else "استمع للملخص",
                                                tint = if (isSpeakingNote) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.secondary
                                                }
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteNote(note.id) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "حذف الملخص",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = note.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = note.summaryContent,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (note.keyTerms.isNotBlank()) {
                                    Text(
                                        text = note.keyTerms,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun EmptyLibraryNotice(message: String) {
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
                imageVector = Icons.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
