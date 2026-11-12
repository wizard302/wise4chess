package io.github.wizard302.wise4chess.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.wizard302.wise4chess.R
import kotlinx.serialization.Serializable

@Serializable data object PlayRoute
@Serializable data object PuzzlesRoute
@Serializable data object LearnRoute
@Serializable data object AnalysisRoute
@Serializable data object MoreRoute

/** The sections of the bottom navigation bar, in display order. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    PLAY(PlayRoute, R.string.nav_play, R.drawable.ic_nav_play),
    PUZZLES(PuzzlesRoute, R.string.nav_puzzles, R.drawable.ic_nav_puzzles),
    LEARN(LearnRoute, R.string.nav_learn, R.drawable.ic_nav_learn),
    ANALYSIS(AnalysisRoute, R.string.nav_analysis, R.drawable.ic_nav_analysis),
    MORE(MoreRoute, R.string.nav_more, R.drawable.ic_nav_more),
}
