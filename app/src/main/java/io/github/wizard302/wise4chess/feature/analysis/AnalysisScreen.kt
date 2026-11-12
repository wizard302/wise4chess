package io.github.wizard302.wise4chess.feature.analysis

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.wizard302.wise4chess.R
import io.github.wizard302.wise4chess.ui.PlaceholderScreen

@Composable
fun AnalysisScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.nav_analysis),
        description = stringResource(R.string.analysis_placeholder),
    )
}
