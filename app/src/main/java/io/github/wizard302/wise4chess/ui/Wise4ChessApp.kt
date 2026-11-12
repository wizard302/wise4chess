package io.github.wizard302.wise4chess.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.wizard302.wise4chess.feature.analysis.AnalysisScreen
import io.github.wizard302.wise4chess.feature.learn.LearnScreen
import io.github.wizard302.wise4chess.feature.more.MoreScreen
import io.github.wizard302.wise4chess.feature.play.PlayScreen
import io.github.wizard302.wise4chess.feature.puzzles.PuzzlesScreen
import io.github.wizard302.wise4chess.navigation.AnalysisRoute
import io.github.wizard302.wise4chess.navigation.LearnRoute
import io.github.wizard302.wise4chess.navigation.MoreRoute
import io.github.wizard302.wise4chess.navigation.PlayRoute
import io.github.wizard302.wise4chess.navigation.PuzzlesRoute
import io.github.wizard302.wise4chess.navigation.TopLevelDestination

@Composable
fun Wise4ChessApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = PlayRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<PlayRoute> { PlayScreen() }
            composable<PuzzlesRoute> { PuzzlesScreen() }
            composable<LearnRoute> { LearnScreen() }
            composable<AnalysisRoute> { AnalysisScreen() }
            composable<MoreRoute> { MoreScreen() }
        }
    }
}
