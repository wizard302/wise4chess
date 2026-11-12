package io.github.wizard302.wise4chess.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// One accent (green) on neutral surfaces; everything else stays out of the board's way.
internal val DarkColors = darkColorScheme(
    primary = Color(0xFF7CC47F),
    onPrimary = Color(0xFF0D2B11),
    primaryContainer = Color(0xFF244D29),
    onPrimaryContainer = Color(0xFFBFE8C0),
    secondary = Color(0xFFB4C8B3),
    onSecondary = Color(0xFF203522),
    secondaryContainer = Color(0xFF2B352B),
    onSecondaryContainer = Color(0xFFD5E5D3),
    background = Color(0xFF131513),
    onBackground = Color(0xFFE3E5E0),
    surface = Color(0xFF131513),
    onSurface = Color(0xFFE3E5E0),
    surfaceVariant = Color(0xFF262A26),
    onSurfaceVariant = Color(0xFFA9AEA7),
    surfaceContainerLowest = Color(0xFF0E100E),
    surfaceContainerLow = Color(0xFF181B18),
    surfaceContainer = Color(0xFF1C1F1C),
    surfaceContainerHigh = Color(0xFF242824),
    surfaceContainerHighest = Color(0xFF2E322E),
    outline = Color(0xFF6F756D),
    outlineVariant = Color(0xFF343934),
    error = Color(0xFFF2857B),
    onError = Color(0xFF4A0D08),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2F7D36),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBCD),
    onPrimaryContainer = Color(0xFF0E3113),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE8D9),
    onSecondaryContainer = Color(0xFF1A2A19),
    background = Color(0xFFF9FAF6),
    onBackground = Color(0xFF191C18),
    surface = Color(0xFFF9FAF6),
    onSurface = Color(0xFF191C18),
    surfaceVariant = Color(0xFFE3E6DE),
    onSurfaceVariant = Color(0xFF50564D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F5EF),
    surfaceContainer = Color(0xFFEDEFE9),
    surfaceContainerHigh = Color(0xFFE7E9E3),
    surfaceContainerHighest = Color(0xFFE1E4DD),
    outline = Color(0xFF7D8479),
    outlineVariant = Color(0xFFCBCFC5),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)
