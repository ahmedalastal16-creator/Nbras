package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.credentials.CredentialManager
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ai.configureAppCheck
import com.example.ui.components.VoicePlayerBar
import com.example.ui.screens.AiLabScreen
import com.example.ui.screens.DeckDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.StudySummariesScreen
import com.example.ui.screens.TutorScreen
import com.example.ui.theme.NibrasTheme
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.viewmodel.NibrasViewModel

private object NibrasRoutes {
    const val HOME = "home"
    const val TUTOR = "tutor"
    const val LAB = "lab"
    const val SCHEDULE = "schedule"
    const val LIBRARY = "library"
    const val SUMMARIES = "summaries"
    const val DECK_DETAIL = "deck/{deckId}"
    fun deckDetail(deckId: Long) = "deck/$deckId"
}

private data class TopDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopDestination(
        route = NibrasRoutes.HOME,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    TopDestination(
        route = NibrasRoutes.TUTOR,
        labelRes = R.string.nav_tutor,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_tutor"
    ),
    TopDestination(
        route = NibrasRoutes.LAB,
        labelRes = R.string.nav_lab,
        selectedIcon = Icons.Filled.Science,
        unselectedIcon = Icons.Outlined.Science,
        testTag = "nav_lab"
    ),
    TopDestination(
        route = NibrasRoutes.SCHEDULE,
        labelRes = R.string.nav_schedule,
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        testTag = "nav_schedule"
    ),
    TopDestination(
        route = NibrasRoutes.LIBRARY,
        labelRes = R.string.nav_library,
        selectedIcon = Icons.Filled.Book,
        unselectedIcon = Icons.Outlined.Book,
        testTag = "nav_library"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(ObsidianCanvas.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb())
        )
        setContent {
            val viewModel: NibrasViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val didApplyInitialEdgeToEdge = remember { mutableStateOf(false) }

            LaunchedEffect(isDarkTheme) {
                if (!didApplyInitialEdgeToEdge.value) {
                    didApplyInitialEdgeToEdge.value = true
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = if (isDarkTheme) {
                            SystemBarStyle.dark(ObsidianCanvas.toArgb())
                        } else {
                            SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                        },
                        navigationBarStyle = if (isDarkTheme) {
                            SystemBarStyle.dark(Color.Transparent.toArgb())
                        } else {
                            SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                        }
                    )
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NibrasTheme(darkTheme = isDarkTheme) {
                    NibrasApp(viewModel = viewModel, isDarkTheme = isDarkTheme)
                }
            }
        }
    }
}

@Composable
fun NibrasApp(
    viewModel: NibrasViewModel = viewModel(),
    isDarkTheme: Boolean = true
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val showSignInScreen by viewModel.showSignInScreen.collectAsStateWithLifecycle()
    val pendingAiAction by viewModel.pendingAiAction.collectAsStateWithLifecycle()

    val decks by viewModel.decksWithStats.collectAsStateWithLifecycle()
    val scheduleItems by viewModel.scheduleItems.collectAsStateWithLifecycle()
    val allCards by viewModel.allFlashcards.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val focusTimer by viewModel.focusTimer.collectAsStateWithLifecycle()
    val selectedStage by viewModel.selectedGazaStage.collectAsStateWithLifecycle()
    val selectedSubject by viewModel.selectedGazaSubject.collectAsStateWithLifecycle()
    val activePackageId by viewModel.activePackageId.collectAsStateWithLifecycle()
    val savedConversations by viewModel.savedConversations.collectAsStateWithLifecycle()
    val aiSpeedMode by viewModel.aiSpeedMode.collectAsStateWithLifecycle()
    val autoSpeakTutor by viewModel.autoSpeakTutor.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isTutorThinking by viewModel.isTutorThinking.collectAsStateWithLifecycle()
    val tutorElapsedSeconds by viewModel.tutorElapsedSeconds.collectAsStateWithLifecycle()
    val streamingTutorText by viewModel.streamingTutorText.collectAsStateWithLifecycle()
    val tutorErrorMessage by viewModel.tutorErrorMessage.collectAsStateWithLifecycle()
    val aiLabMode by viewModel.aiLabMode.collectAsStateWithLifecycle()
    val aiLabOutput by viewModel.aiLabOutput.collectAsStateWithLifecycle()
    val labElapsedSeconds by viewModel.labElapsedSeconds.collectAsStateWithLifecycle()
    val cloudSummaries by viewModel.cloudSummaries.collectAsStateWithLifecycle()
    val cloudSummaryError by viewModel.cloudSummaryError.collectAsStateWithLifecycle()
    val isSavingCloudSummary by viewModel.isSavingCloudSummary.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    LaunchedEffect(voiceState.ttsErrorMessage) {
        val err = voiceState.ttsErrorMessage
        if (err != null) {
            snackbarHostState.showSnackbar(err)
        }
    }

    if (showSignInScreen) {
        BackHandler {
            viewModel.dismissSignInScreenAsGuest()
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { signInPadding ->
            SignInScreen(
                pendingPromptPreview = pendingAiAction?.previewText,
                onAuthSuccess = viewModel::onGoogleSignInSucceeded,
                onContinueAsGuest = viewModel::dismissSignInScreenAsGuest,
                modifier = Modifier.padding(signInPadding)
            )
        }
        return
    }

    val isTopLevelRoute = topDestinations.any { it.route == currentRoute }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 640.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail && isTopLevelRoute) {
                    Column {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                        NavigationBar(
                            modifier = Modifier.navigationBarsPadding(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && isTopLevelRoute) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    if (voiceState.isSpeaking) {
                        VoicePlayerBar(
                            voiceState = voiceState,
                            onSetSpeed = viewModel::setSpeechRate,
                            onStop = viewModel::stopSpeaking
                        )
                    }

                    NavHost(
                        navController = navController,
                        startDestination = NibrasRoutes.HOME,
                        enterTransition = {
                            fadeIn(animationSpec = tween(220)) +
                                scaleIn(initialScale = 0.98f, animationSpec = tween(220))
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(180))
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(220)) +
                                scaleIn(initialScale = 0.98f, animationSpec = tween(220))
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(180))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(NibrasRoutes.HOME) {
                            HomeScreen(
                                stats = stats,
                                decks = decks,
                                scheduleItems = scheduleItems,
                                focusTimer = focusTimer,
                                selectedStage = selectedStage,
                                selectedSubject = selectedSubject,
                                cloudSummariesCount = cloudSummaries.size,
                                isDarkTheme = isDarkTheme,
                                authState = authState,
                                onOpenSignIn = viewModel::openSignInScreen,
                                onSignOut = { viewModel.signOut(credentialManager) },
                                onToggleTheme = viewModel::toggleTheme,
                                onSelectStage = viewModel::selectGazaStage,
                                onSelectSubject = viewModel::selectGazaSubject,
                                onCreateGazaDeck = viewModel::createDeckFromGazaPackage,
                                onOpenDeck = { deckId ->
                                    navController.navigate(NibrasRoutes.deckDetail(deckId))
                                },
                                onOpenTutor = { navigateTopLevel(NibrasRoutes.TUTOR) },
                                onOpenLab = { navigateTopLevel(NibrasRoutes.LAB) },
                                onOpenSchedule = { navigateTopLevel(NibrasRoutes.SCHEDULE) },
                                onOpenSummaries = { navController.navigate(NibrasRoutes.SUMMARIES) },
                                onSelectFocusPreset = viewModel::selectFocusPreset,
                                onSelectFocusSubject = viewModel::selectFocusSubject,
                                onToggleFocusTimer = viewModel::toggleFocusTimer,
                                onResetFocusTimer = viewModel::resetFocusTimer,
                                onLogQuickFocus = viewModel::logQuickFocusSession,
                                onCreateDeck = viewModel::createDeck
                            )
                        }

                        composable(NibrasRoutes.TUTOR) {
                            key(authState.uid ?: "guest") {
                                TutorScreen(
                                    messages = chatMessages,
                                    savedConversations = savedConversations,
                                    activePackageId = activePackageId,
                                    authState = authState,
                                    decks = decks.map { it.deck },
                                    selectedStage = selectedStage,
                                    selectedSubject = selectedSubject,
                                    speedMode = aiSpeedMode,
                                    autoSpeakTutor = autoSpeakTutor,
                                    voiceState = voiceState,
                                    isThinking = isTutorThinking,
                                    elapsedSeconds = tutorElapsedSeconds,
                                    streamingText = streamingTutorText,
                                    errorMessage = tutorErrorMessage,
                                    onSelectStage = viewModel::selectGazaStage,
                                    onSelectSubject = viewModel::selectGazaSubject,
                                    onSelectSavedConversation = { conv ->
                                        viewModel.selectSavedPackageConversation(
                                            packageId = conv.id,
                                            stageId = conv.stageId,
                                            subjectTitle = conv.subjectTitle
                                        )
                                    },
                                    onOpenSignIn = viewModel::openSignInScreen,
                                    onSelectSpeedMode = viewModel::setAiSpeedMode,
                                    onToggleAutoSpeak = viewModel::toggleAutoSpeakTutor,
                                    onToggleSpeak = viewModel::toggleSpeak,
                                    onSendMessage = viewModel::sendTutorMessage,
                                    onCancelRequest = viewModel::cancelTutorRequest,
                                    onSaveReplyToDeck = viewModel::saveTutorReplyToDeck,
                                    onSaveReplyToCloud = { replyText ->
                                        val firstLine = replyText
                                            .lineSequence()
                                            .firstOrNull { it.isNotBlank() }
                                            ?.take(80)
                                            ?: "شرح $selectedSubject"
                                        viewModel.saveCloudStudySummary(
                                            stage = selectedStage,
                                            subjectTitle = selectedSubject,
                                            title = firstLine,
                                            content = replyText,
                                            keyTerms = "$selectedSubject • معلم نبراس",
                                            sourceType = "tutor",
                                            existingId = null
                                        )
                                    },
                                    onCopiedNotice = viewModel::showStatusMessage,
                                    onRetryLastMessage = viewModel::retryLastTutorMessage,
                                    onDismissError = viewModel::dismissTutorError,
                                    onClearChat = viewModel::clearChatHistory
                                )
                            }
                        }

                        composable(NibrasRoutes.LAB) {
                            AiLabScreen(
                                mode = aiLabMode,
                                outputState = aiLabOutput,
                                decks = decks.map { it.deck },
                                selectedStage = selectedStage,
                                selectedSubject = selectedSubject,
                                speedMode = aiSpeedMode,
                                voiceState = voiceState,
                                elapsedSeconds = labElapsedSeconds,
                                onSelectStage = viewModel::selectGazaStage,
                                onSelectSubject = viewModel::selectGazaSubject,
                                onSelectSpeedMode = viewModel::setAiSpeedMode,
                                onSelectMode = viewModel::setAiLabMode,
                                onRunAiLab = viewModel::runAiLab,
                                onCancelLabRequest = viewModel::cancelAiLabRequest,
                                onToggleSpeak = viewModel::toggleSpeak,
                                onSelectQuizOption = viewModel::selectQuizOption,
                                onAdvanceQuiz = viewModel::advanceQuiz,
                                onResetOutput = viewModel::resetAiLabOutput,
                                onSaveSummaryToDeck = { deckId, summary ->
                                    viewModel.saveSummaryToDeck(
                                        deckId = deckId,
                                        title = summary.title,
                                        overview = summary.overview,
                                        bullets = summary.bulletPoints,
                                        keyTerms = summary.keyTerms,
                                        isSample = summary.isSampleResponse
                                    )
                                },
                                onSaveSummaryToCloud = { summary ->
                                    val fullText = buildString {
                                        append(summary.overview)
                                        if (summary.bulletPoints.isNotEmpty()) {
                                            append("\n\n")
                                            summary.bulletPoints.forEachIndexed { idx, point ->
                                                append("${idx + 1}. $point\n")
                                            }
                                        }
                                    }
                                    viewModel.saveCloudStudySummary(
                                        stage = selectedStage,
                                        subjectTitle = selectedSubject,
                                        title = summary.title,
                                        content = fullText,
                                        keyTerms = summary.keyTerms.joinToString(" • "),
                                        sourceType = "ai_lab",
                                        existingId = null
                                    )
                                },
                                onSaveFlashcardsToDeck = viewModel::saveGeneratedCardsToDeck,
                                onCopiedNotice = viewModel::showStatusMessage,
                                onOpenSchedule = { navigateTopLevel(NibrasRoutes.SCHEDULE) }
                            )
                        }

                        composable(NibrasRoutes.SCHEDULE) {
                            ScheduleScreen(
                                scheduleItems = scheduleItems,
                                selectedStage = selectedStage,
                                decks = decks.map { it.deck },
                                onGenerateSmartSchedule = viewModel::generateSmartSchedule,
                                onAddCustomItem = viewModel::addCustomScheduleItem,
                                onToggleCompleted = viewModel::toggleScheduleCompleted,
                                onDeleteItem = viewModel::deleteScheduleItem,
                                onStartFocusSession = { subject, mins ->
                                    viewModel.prepareFocusFromSchedule(subject, mins)
                                    navigateTopLevel(NibrasRoutes.HOME)
                                },
                                onAskTutorOnSubject = { subject, suggestionPrompt ->
                                    viewModel.preparePackageForSubject(subject)
                                    navigateTopLevel(NibrasRoutes.TUTOR)
                                    if (!suggestionPrompt.isNullOrBlank()) {
                                        viewModel.sendTutorMessage(suggestionPrompt, null)
                                    }
                                }
                            )
                        }

                        composable(NibrasRoutes.LIBRARY) {
                            LibraryScreen(
                                decks = decks,
                                notes = allNotes,
                                cloudSummariesCount = cloudSummaries.size,
                                voiceState = voiceState,
                                onToggleSpeak = viewModel::toggleSpeak,
                                onOpenDeck = { deckId ->
                                    navController.navigate(NibrasRoutes.deckDetail(deckId))
                                },
                                onOpenSummaries = {
                                    navController.navigate(NibrasRoutes.SUMMARIES)
                                },
                                onCreateDeck = viewModel::createDeck,
                                onDeleteNote = viewModel::deleteNote
                            )
                        }

                        composable(NibrasRoutes.SUMMARIES) {
                            StudySummariesScreen(
                                cloudSummaries = cloudSummaries,
                                localNotes = allNotes,
                                selectedStage = selectedStage,
                                selectedSubject = selectedSubject,
                                authState = authState,
                                isSaving = isSavingCloudSummary,
                                syncError = cloudSummaryError,
                                voiceState = voiceState,
                                onBack = { navController.popBackStack() },
                                onOpenSignIn = viewModel::openSignInScreen,
                                onClearError = viewModel::clearCloudSummaryError,
                                onSaveSummary = viewModel::saveCloudStudySummary,
                                onDeleteCloudSummary = viewModel::deleteCloudStudySummary,
                                onToggleSpeak = viewModel::toggleSpeak,
                                onAskTutorOnSummary = { subject, prompt ->
                                    viewModel.preparePackageForSubject(subject)
                                    navigateTopLevel(NibrasRoutes.TUTOR)
                                    viewModel.sendTutorMessage(prompt, null)
                                },
                                onCopiedNotice = viewModel::showStatusMessage
                            )
                        }

                        composable(
                            route = NibrasRoutes.DECK_DETAIL,
                            arguments = listOf(navArgument("deckId") { type = NavType.LongType })
                        ) { entry ->
                            val deckId = entry.arguments?.getLong("deckId") ?: -1L
                            val deck = decks.firstOrNull { it.deck.id == deckId }?.deck
                            val deckCards = allCards.filter { it.deckId == deckId }
                            val deckNotes = allNotes.filter { it.deckId == deckId }

                            DeckDetailScreen(
                                deck = deck,
                                cards = deckCards,
                                notes = deckNotes,
                                onBack = { navController.popBackStack() },
                                onAddCard = { q, a, hint ->
                                    viewModel.addFlashcard(deckId, q, a, hint)
                                },
                                onToggleMastery = viewModel::toggleFlashcardMastery,
                                onDeleteCard = viewModel::deleteFlashcard,
                                onDeleteDeck = viewModel::deleteDeck,
                                onStartDeckQuiz = { title, cardsList ->
                                    viewModel.startDeckQuiz(title, cardsList)
                                    navigateTopLevel(NibrasRoutes.LAB)
                                }
                            )
                        }
                    }
            }
        }
    }
}
}
