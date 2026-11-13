package io.github.wizard302.wise4chess.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wizard302.wise4chess.Wise4ChessApplication
import io.github.wizard302.wise4chess.feature.analysis.AnalysisScreen
import io.github.wizard302.wise4chess.feature.learn.LearnScreen
import io.github.wizard302.wise4chess.feature.more.MoreScreen
import io.github.wizard302.wise4chess.feature.editor.EditorScreen
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.feature.play.BotEngine
import io.github.wizard302.wise4chess.feature.play.BotSetup
import io.github.wizard302.wise4chess.feature.play.GameScreen
import io.github.wizard302.wise4chess.feature.play.GameSetup
import io.github.wizard302.wise4chess.feature.play.GameViewModel
import io.github.wizard302.wise4chess.feature.play.PlayScreen
import io.github.wizard302.wise4chess.feature.play.TimeControl
import io.github.wizard302.wise4chess.feature.puzzles.PuzzlesScreen
import io.github.wizard302.wise4chess.feature.settings.SettingsScreen
import io.github.wizard302.wise4chess.navigation.AnalysisRoute
import io.github.wizard302.wise4chess.navigation.EditorRoute
import io.github.wizard302.wise4chess.navigation.GameRoute
import io.github.wizard302.wise4chess.navigation.LearnRoute
import io.github.wizard302.wise4chess.navigation.MoreRoute
import io.github.wizard302.wise4chess.navigation.PlayRoute
import io.github.wizard302.wise4chess.navigation.PuzzlesRoute
import io.github.wizard302.wise4chess.navigation.SettingsRoute
import io.github.wizard302.wise4chess.navigation.TopLevelDestination
import io.github.wizard302.wise4chess.settings.AppSettings
import io.github.wizard302.wise4chess.settings.LocalAppSettings
import kotlinx.coroutines.launch

@Composable
fun Wise4ChessApp() {
    val container = (LocalContext.current.applicationContext as Wise4ChessApplication).container
    val settingsRepository = container.settings
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Games and tools take the whole screen; the bar belongs to the five sections only.
    val onTopLevel = TopLevelDestination.entries.any { currentDestination?.hasRoute(it.route::class) == true }

    fun startGame(setup: GameSetup) {
        navController.navigate(
            GameRoute(
                fen = setup.startFen,
                chess960 = setup.chess960,
                minutes = setup.timeControl?.minutes ?: 0,
                incrementSeconds = setup.timeControl?.incrementSeconds ?: 0,
                faceToFace = setup.faceToFace,
                botGame = setup.bot != null,
                humanIsWhite = setup.bot?.humanSide != Side.BLACK,
                botElo = setup.bot?.elo ?: 0,
                botEngine = (setup.bot?.engine ?: BotEngine.STOCKFISH).name,
            ),
        )
    }

    CompositionLocalProvider(LocalAppSettings provides settings) {
        Scaffold(
            // Every screen deals with the system bars itself.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (onTopLevel) {
                    NavigationBar {
                        TopLevelDestination.entries.forEach { destination ->
                            val label = stringResource(destination.labelRes)
                            NavigationBarItem(
                                selected = currentDestination?.hierarchy
                                    ?.any { it.hasRoute(destination.route::class) } == true,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        // One copy of each section; switching tabs keeps their state.
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                                label = { Text(label, maxLines = 1) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = PlayRoute,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable<PlayRoute> {
                    val savedGame by container.savedGame.current.collectAsStateWithLifecycle()
                    PlayScreen(
                        savedGame = savedGame,
                        onResume = { navController.navigate(GameRoute(resume = true)) },
                        onStartGame = ::startGame,
                        onOpenEditor = { navController.navigate(EditorRoute) },
                    )
                }
                composable<PuzzlesRoute> { PuzzlesScreen() }
                composable<LearnRoute> { LearnScreen() }
                composable<AnalysisRoute> { AnalysisScreen() }
                composable<MoreRoute> { MoreScreen(onOpenSettings = { navController.navigate(SettingsRoute) }) }

                composable<GameRoute> { entry ->
                    val route = entry.toRoute<GameRoute>()
                    val viewModel = viewModel {
                        // Read once, when the game screen is first created.
                        val saved = if (route.resume) container.savedGame.current.value else null
                        val setup = if (saved != null) {
                            GameSetup(
                                startFen = saved.startFen,
                                chess960 = saved.chess960,
                                timeControl = if (saved.minutes > 0) TimeControl(saved.minutes, saved.incrementSeconds) else null,
                                faceToFace = saved.faceToFace,
                                bot = if (saved.botGame) {
                                    BotSetup(
                                        if (saved.humanIsWhite) Side.WHITE else Side.BLACK,
                                        saved.botElo.takeIf { it > 0 },
                                        botEngineOf(saved.botEngine),
                                    )
                                } else {
                                    null
                                },
                            )
                        } else {
                            GameSetup(
                                startFen = route.fen,
                                chess960 = route.chess960,
                                timeControl = if (route.minutes > 0) TimeControl(route.minutes, route.incrementSeconds) else null,
                                faceToFace = route.faceToFace,
                                bot = if (route.botGame) {
                                    BotSetup(
                                        if (route.humanIsWhite) Side.WHITE else Side.BLACK,
                                        route.botElo.takeIf { it > 0 },
                                        botEngineOf(route.botEngine),
                                    )
                                } else {
                                    null
                                },
                            )
                        }
                        val opponent = setup.bot?.engine ?: BotEngine.STOCKFISH
                        GameViewModel(
                            setup = setup,
                            launchEngine = container.launcherFor(opponent),
                            // Advice always comes from Stockfish, whoever the opponent is.
                            launchAnalysisEngine = if (opponent == BotEngine.STOCKFISH) null else container.launchStockfish,
                            store = container.savedGame,
                            restored = saved,
                        )
                    }
                    GameScreen(viewModel, onClose = { navController.popBackStack() })
                }
                composable<EditorRoute> {
                    EditorScreen(
                        onClose = { navController.popBackStack() },
                        onPlay = ::startGame,
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        settings = settings,
                        onChange = { changed -> scope.launch { settingsRepository.update(changed) } },
                        onClose = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

/** The engine stored under [name]; anything unknown falls back to Stockfish. */
private fun botEngineOf(name: String): BotEngine = BotEngine.entries.firstOrNull { it.name == name } ?: BotEngine.STOCKFISH
