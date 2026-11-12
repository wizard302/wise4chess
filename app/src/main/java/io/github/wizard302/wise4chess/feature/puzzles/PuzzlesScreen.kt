package io.github.wizard302.wise4chess.feature.puzzles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.wizard302.wise4chess.R
import io.github.wizard302.wise4chess.ui.PlaceholderScreen

@Composable
fun PuzzlesScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.nav_puzzles),
        description = stringResource(R.string.puzzles_placeholder),
    )
}
