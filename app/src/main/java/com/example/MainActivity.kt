package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Science
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.ui.screens.AiLabScreen
import com.example.ui.screens.DeckDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.TutorScreen
import com.example.ui.theme.NibrasTheme
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.viewmodel.NibrasViewModel

private object NibrasRoutes {
    const val HOME = "home"
    const val TUTOR = "tutor"
    const val LAB = "lab"
    const val LIBRARY = "library"
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

    val decks by viewModel.decksWithStats.collectAsStateWithLifecycle()
    val allCards by viewModel.allFlashcards.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val focusTimer by viewModel.focusTimer.collectAsStateWithLifecycle()
    val selectedStage by viewModel.selectedGazaStage.collectAsStateWithLifecycle()
    val selectedSubject by viewModel.selectedGazaSubject.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isTutorThinking by viewModel.isTutorThinking.collectAsStateWithLifecycle()
    val streamingTutorText by viewModel.streamingTutorText.collectAsStateWithLifecycle()
    val tutorErrorMessage by viewModel.tutorErrorMessage.collectAsStateWithLifecycle()
    val aiLabMode by viewModel.aiLabMode.collectAsStateWithLifecycle()
    val aiLabOutput by viewModel.aiLabOutput.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
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

                NavHost(
                    navController = navController,
                    startDestination = NibrasRoutes.HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(NibrasRoutes.HOME) {
                        HomeScreen(
                            stats = stats,
                            decks = decks,
                            focusTimer = focusTimer,
                            selectedStage = selectedStage,
                            selectedSubject = selectedSubject,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = viewModel::toggleTheme,
                            onSelectStage = viewModel::selectGazaStage,
                            onSelectSubject = viewModel::selectGazaSubject,
                            onCreateGazaDeck = viewModel::createDeckFromGazaPackage,
                            onOpenDeck = { deckId ->
                                navController.navigate(NibrasRoutes.deckDetail(deckId))
                            },
                            onOpenTutor = { navigateTopLevel(NibrasRoutes.TUTOR) },
                            onOpenLab = { navigateTopLevel(NibrasRoutes.LAB) },
                            onSelectFocusPreset = viewModel::selectFocusPreset,
                            onSelectFocusSubject = viewModel::selectFocusSubject,
                            onToggleFocusTimer = viewModel::toggleFocusTimer,
                            onResetFocusTimer = viewModel::resetFocusTimer,
                            onLogQuickFocus = viewModel::logQuickFocusSession,
                            onCreateDeck = viewModel::createDeck
                        )
                    }

                    composable(NibrasRoutes.TUTOR) {
                        TutorScreen(
                            messages = chatMessages,
                            decks = decks.map { it.deck },
                            selectedStage = selectedStage,
                            selectedSubject = selectedSubject,
                            isThinking = isTutorThinking,
                            streamingText = streamingTutorText,
                            errorMessage = tutorErrorMessage,
                            onSelectStage = viewModel::selectGazaStage,
                            onSelectSubject = viewModel::selectGazaSubject,
                            onSendMessage = viewModel::sendTutorMessage,
                            onSaveReplyToDeck = viewModel::saveTutorReplyToDeck,
                            onCopiedNotice = viewModel::showStatusMessage,
                            onDismissError = viewModel::dismissTutorError,
                            onClearChat = viewModel::clearChatHistory
                        )
                    }

                    composable(NibrasRoutes.LAB) {
                        AiLabScreen(
                            mode = aiLabMode,
                            outputState = aiLabOutput,
                            decks = decks.map { it.deck },
                            selectedStage = selectedStage,
                            selectedSubject = selectedSubject,
                            onSelectStage = viewModel::selectGazaStage,
                            onSelectSubject = viewModel::selectGazaSubject,
                            onSelectMode = viewModel::setAiLabMode,
                            onRunAiLab = viewModel::runAiLab,
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
                            onSaveFlashcardsToDeck = viewModel::saveGeneratedCardsToDeck,
                            onCopiedNotice = viewModel::showStatusMessage
                        )
                    }

                    composable(NibrasRoutes.LIBRARY) {
                        LibraryScreen(
                            decks = decks,
                            notes = allNotes,
                            onOpenDeck = { deckId ->
                                navController.navigate(NibrasRoutes.deckDetail(deckId))
                            },
                            onCreateDeck = viewModel::createDeck,
                            onDeleteNote = viewModel::deleteNote
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
