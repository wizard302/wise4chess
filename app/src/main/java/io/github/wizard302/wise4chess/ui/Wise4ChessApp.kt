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
import io.github.wizard302.wise4chess.feature.play.GameScreen
import io.github.wizard302.wise4chess.feature.play.GameSetup
import io.github.wizard302.wise4chess.feature.play.LocalGameViewModel
import io.github.wizard302.wise4chess.feature.play.PlayScreen
import io.github.wizard302.wise4chess.feature.play.TimeControl
import io.github.wizard302.wise4chess.feature.puzzles.PuzzlesScreen
import io.github.wizard302.wise4chess.feature.settings.SettingsScreen
import io.github.wizard302.wise4chess.navigation.AnalysisRoute
import io.github.wizard302.wise4chess.navigation.EditorRoute
import io.github.wizard302.wise4chess.navigation.LocalGameRoute
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
    val settingsRepository = (LocalContext.current.applicationContext as Wise4ChessApplication).container.settings
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Games and tools take the whole screen; the bar belongs to the five sections only.
    val onTopLevel = TopLevelDestination.entries.any { currentDestination?.hasRoute(it.route::class) == true }

    fun startLocalGame(setup: GameSetup) {
        navController.navigate(
            LocalGameRoute(
                fen = setup.startFen,
                chess960 = setup.chess960,
                minutes = setup.timeControl?.minutes ?: 0,
                incrementSeconds = setup.timeControl?.incrementSeconds ?: 0,
                faceToFace = setup.faceToFace,
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
                    PlayScreen(
                        onStartLocalGame = ::startLocalGame,
                        onOpenEditor = { navController.navigate(EditorRoute) },
                    )
                }
                composable<PuzzlesRoute> { PuzzlesScreen() }
                composable<LearnRoute> { LearnScreen() }
                composable<AnalysisRoute> { AnalysisScreen() }
                composable<MoreRoute> { MoreScreen(onOpenSettings = { navController.navigate(SettingsRoute) }) }

                composable<LocalGameRoute> { entry ->
                    val route = entry.toRoute<LocalGameRoute>()
                    val viewModel = viewModel {
                        LocalGameViewModel(
                            GameSetup(
                                startFen = route.fen,
                                chess960 = route.chess960,
                                timeControl = if (route.minutes > 0) TimeControl(route.minutes, route.incrementSeconds) else null,
                                faceToFace = route.faceToFace,
                            ),
                        )
                    }
                    GameScreen(viewModel, onClose = { navController.popBackStack() })
                }
                composable<EditorRoute> {
                    EditorScreen(
                        onClose = { navController.popBackStack() },
                        onPlay = ::startLocalGame,
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        settings = settings,
                        onDragPiecesChange = { enabled -> scope.launch { settingsRepository.setDragPieces(enabled) } },
                        onClose = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
