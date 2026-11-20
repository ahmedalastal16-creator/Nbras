package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.curriculum.GazaCurriculumCatalog
import com.example.data.curriculum.GazaStage
import com.example.ui.components.CreateDeckDialog
import com.example.ui.components.DeckLuxuryCard
import com.example.ui.components.GazaPackageSelectorRow
import com.example.ui.components.MasteryArcRing
import com.example.ui.components.StudyInsightsCanvasCard
import com.example.ui.state.DashboardStats
import com.example.ui.state.DeckWithStats
import com.example.ui.state.FocusTimerState
import java.util.Locale

@Composable
fun HomeScreen(
    stats: DashboardStats,
    decks: List<DeckWithStats>,
    focusTimer: FocusTimerState,
    selectedStage: GazaStage,
    selectedSubject: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSelectStage: (GazaStage) -> Unit,
    onSelectSubject: (String) -> Unit,
    onCreateGazaDeck: (stageTitle: String, subjectTitle: String) -> Unit,
    onOpenDeck: (Long) -> Unit,
    onOpenTutor: () -> Unit,
    onOpenLab: () -> Unit,
    onSelectFocusPreset: (Int) -> Unit,
    onSelectFocusSubject: (String) -> Unit,
    onToggleFocusTimer: () -> Unit,
    onResetFocusTimer: () -> Unit,
    onLogQuickFocus: () -> Unit,
    onCreateDeck: (title: String, subtitle: String, category: String, accentHex: Long) -> Unit,
    modifier: Modifier = Modifier
) {
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

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                LuxuryHeroHeader(
                    stats = stats,
                    selectedStage = selectedStage,
                    selectedSubject = selectedSubject,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onOpenTutor = onOpenTutor,
                    onOpenLab = onOpenLab
                )
            }

            item {
                GazaPackagesPortalCard(
                    selectedStage = selectedStage,
                    selectedSubject = selectedSubject,
                    onSelectStage = onSelectStage,
                    onSelectSubject = onSelectSubject,
                    onAddPackageAsDeck = {
                        onCreateGazaDeck(selectedStage.title, selectedSubject)
                    },
                    onAskTutorOnPackage = onOpenTutor
                )
            }

            item {
                FocusStudioCard(
                    timer = focusTimer,
                    subjectOptions = listOf(selectedSubject, "جلسة تركيز حرة") + decks.map { it.deck.title }.distinct(),
                    onSelectPreset = onSelectFocusPreset,
                    onSelectSubject = onSelectFocusSubject,
                    onToggleTimer = onToggleFocusTimer,
                    onResetTimer = onResetFocusTimer,
                    onLogQuickFocus = onLogQuickFocus
                )
            }

            item {
                StudyInsightsCanvasCard(
                    totalFocusMinutes = stats.totalFocusMinutes,
                    totalFocusSessions = stats.totalFocusSessions,
                    totalQuizzes = stats.totalQuizzes,
                    averageQuizPercent = stats.averageQuizPercent,
                    deckMasteries = decks
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "موادك الدراسية",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "بطاقات المراجعة والملخصات المحفوظة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showCreateDeck = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_add_deck_button"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مادة جديدة")
                    }
                }
            }

            if (decks.isEmpty()) {
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
                                imageVector = Icons.Filled.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "ابدأ ببناء مكتبتك الدراسية",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "أنشئ مادتك الأولى لتخزين البطاقات والملخصات.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(decks, key = { it.deck.id }) { item ->
                    DeckLuxuryCard(
                        deckWithStats = item,
                        onClick = { onOpenDeck(item.deck.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun LuxuryHeroHeader(
    stats: DashboardStats,
    selectedStage: GazaStage,
    selectedSubject: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenTutor: () -> Unit,
    onOpenLab: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_study_hero),
                contentDescription = stringResource(R.string.hero_banner_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(254.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(254.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xC80A0E17),
                                Color(0xEB0A0E17)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFE2C068).copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "نبراس • رزم غزة التعليمية (Gemini AI)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE2C068),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleTheme,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color(0xFFE2C068)
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "تبديل المظهر الفاخر",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedSubject,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFFF5F1E8)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${selectedStage.badge} • ${stats.masteredCards} من ${stats.totalCards} بطاقة متقنة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB3ACA0)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    MasteryArcRing(
                        progress = if (stats.totalCards == 0) 0f else stats.masteredCards.toFloat() / stats.totalCards.toFloat(),
                        label = "الإتقان",
                        valueText = "${stats.masteryPercent}%"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenTutor,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("hero_open_tutor_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE2C068),
                            contentColor = Color(0xFF0A0E17)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اسأل المعلم")
                    }
                    OutlinedButton(
                        onClick = onOpenLab,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("hero_open_lab_button"),
                        border = BorderStroke(1.dp, Color(0xFF5EEAD4).copy(alpha = 0.75f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF5EEAD4)
                        )
                    ) {
                        Text("تلخيص واختبار")
                    }
                }
            }
        }
    }
}

@Composable
private fun GazaPackagesPortalCard(
    selectedStage: GazaStage,
    selectedSubject: String,
    onSelectStage: (GazaStage) -> Unit,
    onSelectSubject: (String) -> Unit,
    onAddPackageAsDeck: () -> Unit,
    onAskTutorOnPackage: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "رزم وزارة التربية — قطاع غزة",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "اختر مرحلتك ومبحثك لتوجيه الذكاء الاصطناعي بدقة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(GazaCurriculumCatalog.OFFICIAL_PORTAL_URL)
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("البوابة")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.gaza_portal_cta),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            GazaPackageSelectorRow(
                selectedStage = selectedStage,
                selectedSubject = selectedSubject,
                onSelectStage = onSelectStage,
                onSelectSubject = onSelectSubject
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAddPackageAsDeck,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ كمادة")
                }
                Button(
                    onClick = onAskTutorOnPackage,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ذاكرها مع AI")
                }
            }
        }
    }
}

@Composable
private fun FocusStudioCard(
    timer: FocusTimerState,
    subjectOptions: List<String>,
    onSelectPreset: (Int) -> Unit,
    onSelectSubject: (String) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onLogQuickFocus: () -> Unit
) {
    val mins = timer.remainingSeconds / 60
    val secs = timer.remainingSeconds % 60
    val formatted = String.format(Locale.US, "%02d:%02d", mins, secs)
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (timer.isRunning) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = if (timer.isRunning) "جلسة تركيز نشطة الآن" else "مؤقت التركيز العميق",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = timer.activeSubject,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = onLogQuickFocus,
                    modifier = Modifier.testTag("log_focus_session_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تسجيل جلسة")
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(subjectOptions.distinct()) { subject ->
                    FilterChip(
                        selected = timer.activeSubject == subject,
                        onClick = { onSelectSubject(subject) },
                        label = { Text(subject, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier.size(86.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 7.dp.toPx()
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = primaryColor,
                            startAngle = -90f,
                            sweepAngle = (1f - timer.progress) * 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 25, 45).forEach { preset ->
                            FilterChip(
                                selected = timer.selectedMinutes == preset,
                                onClick = { onSelectPreset(preset) },
                                label = { Text("${preset}د") },
                                modifier = Modifier.testTag("focus_preset_$preset")
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onToggleTimer,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("focus_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (timer.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (timer.isRunning) "إيقاف مؤقت" else "بدء التركيز")
                        }
                        IconButton(
                            onClick = onResetTimer,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("focus_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "إعادة ضبط المؤقت"
                            )
                        }
                    }
                }
            }
        }
    }
}
