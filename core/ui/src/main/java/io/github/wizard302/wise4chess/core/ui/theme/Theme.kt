package io.github.wizard302.wise4chess.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** App theme. Dark is the default look; the light scheme is selectable from settings later. */
@Composable
fun Wise4ChessTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
