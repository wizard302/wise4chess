package io.github.wizard302.wise4chess.feature.learn

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.wizard302.wise4chess.R
import io.github.wizard302.wise4chess.ui.PlaceholderScreen

@Composable
fun LearnScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.nav_learn),
        description = stringResource(R.string.learn_placeholder),
    )
}
