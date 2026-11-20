package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.FlashcardEntity
import com.example.data.local.StudyDeckEntity
import com.example.data.local.StudyNoteEntity
import com.example.ui.components.AddFlashcardDialog
import com.example.ui.components.FlipFlashcard
import com.example.ui.state.DeckStudyMode

@Composable
fun DeckDetailScreen(
    deck: StudyDeckEntity?,
    cards: List<FlashcardEntity>,
    notes: List<StudyNoteEntity>,
    onBack: () -> Unit,
    onAddCard: (question: String, answer: String, hint: String) -> Unit,
    onToggleMastery: (FlashcardEntity, Boolean) -> Unit,
    onDeleteCard: (Long) -> Unit,
    onDeleteDeck: (Long) -> Unit,
    onStartDeckQuiz: (deckTitle: String, cards: List<FlashcardEntity>) -> Unit,
    modifier: Modifier = Modifier
) {
    var studyMode by remember { mutableStateOf<DeckStudyMode>(DeckStudyMode.Browse) }
    var showAddCard by remember { mutableStateOf(false) }
    var filterTab by remember { mutableIntStateOf(0) } // 0 = All, 1 = Needs Review, 2 = Mastered

    BackHandler {
        if (studyMode is DeckStudyMode.ActiveReview) {
            studyMode = DeckStudyMode.Browse
        } else {
            onBack()
        }
    }

    if (deck == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("المادة غير موجودة", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onBack) { Text("عودة") }
            }
        }
        return
    }

    if (showAddCard) {
        AddFlashcardDialog(
            onDismiss = { showAddCard = false },
            onConfirm = { question, answer, hint ->
                onAddCard(question, answer, hint)
                showAddCard = false
            }
        )
    }

    val accent = Color(deck.accentHex)
    val filteredCards = remember(cards, filterTab) {
        when (filterTab) {
            1 -> cards.filter { !it.isMastered }
            2 -> cards.filter { it.isMastered }
            else -> cards
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = {
                            if (studyMode is DeckStudyMode.ActiveReview) {
                                studyMode = DeckStudyMode.Browse
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("deck_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_content_desc)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = deck.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = accent
                        )
                        Text(
                            text = "${cards.size} بطاقة • ${notes.size} ملخص",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        onDeleteDeck(deck.id)
                        onBack()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_deck_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "حذف المادة",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (val mode = studyMode) {
                is DeckStudyMode.ActiveReview -> {
                    val activeCards = mode.cards
                    if (mode.currentIndex >= activeCards.size) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "أحسنت! أنهيت مراجعة البطاقات",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    text = "تمت مراجعة ${activeCards.size} بطاقات بنجاح.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { studyMode = DeckStudyMode.Browse },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("العودة إلى المادة")
                                }
                            }
                        }
                    } else {
                        val card = activeCards[mode.currentIndex]
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { (mode.currentIndex + 1).toFloat() / activeCards.size.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Text(
                                text = "البطاقة ${mode.currentIndex + 1} من ${activeCards.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )

                            FlipFlashcard(
                                question = card.question,
                                answer = card.answer,
                                hint = card.hint,
                                isFlipped = mode.isFlipped,
                                onFlip = {
                                    studyMode = mode.copy(isFlipped = !mode.isFlipped)
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onToggleMastery(card, false)
                                        studyMode = mode.copy(
                                            currentIndex = mode.currentIndex + 1,
                                            isFlipped = false,
                                            completedCount = mode.completedCount + 1
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("card_needs_review_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("أحتاج مراجعة")
                                }

                                Button(
                                    onClick = {
                                        onToggleMastery(card, true)
                                        studyMode = mode.copy(
                                            currentIndex = mode.currentIndex + 1,
                                            isFlipped = false,
                                            completedCount = mode.completedCount + 1
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("card_mastered_button")
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("أتقنتها")
                                }
                            }
                        }
                    }
                }

                DeckStudyMode.Browse -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val target = filteredCards.ifEmpty { cards }
                                if (target.isNotEmpty()) {
                                    studyMode = DeckStudyMode.ActiveReview(cards = target)
                                }
                            },
                            enabled = cards.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("start_flashcard_review_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (filterTab == 1) "مراجعة غير المتقنة" else "بدء المراجعة")
                        }

                        OutlinedButton(
                            onClick = { onStartDeckQuiz(deck.title, cards) },
                            enabled = cards.size >= 2,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("start_deck_quiz_button")
                        ) {
                            Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("اختبار")
                        }

                        OutlinedButton(
                            onClick = { showAddCard = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("deck_add_card_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("بطاقة")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filterTab == 0,
                            onClick = { filterTab = 0 },
                            label = { Text("الكل (${cards.size})") }
                        )
                        FilterChip(
                            selected = filterTab == 1,
                            onClick = { filterTab = 1 },
                            label = { Text("قيد المراجعة (${cards.count { !it.isMastered }})") }
                        )
                        FilterChip(
                            selected = filterTab == 2,
                            onClick = { filterTab = 2 },
                            label = { Text("متقنة (${cards.count { it.isMastered }})") }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredCards, key = { it.id }) { card ->
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
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = if (card.isMastered) {
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                                            } else {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            }
                                        ) {
                                            Text(
                                                text = if (card.isMastered) "متقنة" else "قيد المراجعة",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (card.isMastered) {
                                                    MaterialTheme.colorScheme.secondary
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onDeleteCard(card.id) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "حذف البطاقة",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = card.question,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = card.answer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (notes.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "الملخصات المحفوظة",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(notes, key = { "note_${it.id}" }) { note ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = note.title,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = note.summaryContent,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
