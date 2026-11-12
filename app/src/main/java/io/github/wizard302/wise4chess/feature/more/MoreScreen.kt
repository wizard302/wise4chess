package io.github.wizard302.wise4chess.feature.more

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.wizard302.wise4chess.R
import io.github.wizard302.wise4chess.ui.PlaceholderScreen

@Composable
fun MoreScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.nav_more),
        description = stringResource(R.string.more_placeholder),
    )
}
