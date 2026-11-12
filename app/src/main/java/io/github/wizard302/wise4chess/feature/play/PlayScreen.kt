package io.github.wizard302.wise4chess.feature.play

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.wizard302.wise4chess.R
import io.github.wizard302.wise4chess.ui.PlaceholderScreen

@Composable
fun PlayScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.nav_play),
        description = stringResource(R.string.play_placeholder),
    )
}
