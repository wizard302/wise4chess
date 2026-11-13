package io.github.wizard302.wise4chess.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.wizard302.wise4chess.R
import io.github.wizard302.wise4chess.core.ui.R as UiR
import kotlinx.serialization.Serializable

@Serializable data object PlayRoute
@Serializable data object PuzzlesRoute
@Serializable data object LearnRoute
@Serializable data object AnalysisRoute
@Serializable data object MoreRoute

/**
 * A game on this device. [minutes] of 0 means no clock. With [botGame] the computer plays
 * the side opposite to [humanIsWhite] at [botElo], where 0 is its full strength. With [resume]
 * the stored game in progress is continued and the other fields are ignored.
 */
@Serializable
data class GameRoute(
    val fen: String? = null,
    val chess960: Boolean = false,
    val minutes: Int = 0,
    val incrementSeconds: Int = 0,
    val faceToFace: Boolean = false,
    val botGame: Boolean = false,
    val humanIsWhite: Boolean = true,
    val botElo: Int = 0,
    val botEngine: String = "STOCKFISH",
    val resume: Boolean = false,
)

@Serializable data object EditorRoute

@Serializable data object SettingsRoute

/** The sections of the bottom navigation bar, in display order. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    PLAY(PlayRoute, R.string.nav_play, UiR.drawable.ic_chess_pawn),
    PUZZLES(PuzzlesRoute, R.string.nav_puzzles, UiR.drawable.ic_extension),
    LEARN(LearnRoute, R.string.nav_learn, UiR.drawable.ic_school),
    ANALYSIS(AnalysisRoute, R.string.nav_analysis, UiR.drawable.ic_analytics),
    MORE(MoreRoute, R.string.nav_more, UiR.drawable.ic_more_horiz),
}
