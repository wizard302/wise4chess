package io.github.wizard302.wise4chess.feature.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wizard302.wise4chess.R
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.ui.R as UiR
import io.github.wizard302.wise4chess.data.SavedGame
import kotlin.math.roundToInt
import kotlin.random.Random

/** The Play section: ways to start a game. */
@Composable
fun PlayScreen(
    onStartGame: (GameSetup) -> Unit,
    onOpenEditor: () -> Unit,
    savedGame: SavedGame? = null,
    onResume: () -> Unit = {},
) {
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var botSetupOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.nav_play),
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (savedGame != null) {
            val move = savedGame.moves.size / 2 + 1
            ModeCard(
                iconRes = UiR.drawable.ic_restart_alt,
                title = stringResource(R.string.play_continue),
                description = stringResource(
                    if (savedGame.botGame) R.string.play_continue_computer else R.string.play_continue_two_players,
                    move,
                ),
                onClick = onResume,
                highlighted = true,
            )
        }
        ModeCard(
            iconRes = UiR.drawable.ic_smart_toy,
            title = stringResource(R.string.play_computer),
            description = stringResource(R.string.play_computer_description),
            onClick = { botSetupOpen = true },
        )
        ModeCard(
            iconRes = UiR.drawable.ic_group,
            title = stringResource(R.string.play_two_players),
            description = stringResource(R.string.play_two_players_description),
            onClick = { setupOpen = true },
        )
        ModeCard(
            iconRes = UiR.drawable.ic_edit_square,
            title = stringResource(R.string.play_editor),
            description = stringResource(R.string.play_editor_description),
            onClick = onOpenEditor,
        )
    }

    if (setupOpen) {
        LocalGameSetupSheet(
            onDismiss = { setupOpen = false },
            onStart = {
                setupOpen = false
                onStartGame(it)
            },
        )
    }
    if (botSetupOpen) {
        BotGameSetupSheet(
            onDismiss = { botSetupOpen = false },
            onStart = {
                botSetupOpen = false
                onStartGame(it)
            },
        )
    }
}

@Composable
private fun ModeCard(
    iconRes: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Options for a two-player game. [startFen] is set when the game continues from the editor. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LocalGameSetupSheet(onDismiss: () -> Unit, onStart: (GameSetup) -> Unit, startFen: String? = null) {
    // Index into TimeControl.PRESETS; -1 plays without a clock.
    var timeIndex by rememberSaveable { mutableIntStateOf(-1) }
    var faceToFace by rememberSaveable { mutableStateOf(false) }
    var chess960 by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(stringResource(R.string.play_two_players), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.time_control), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = timeIndex == -1,
                    onClick = { timeIndex = -1 },
                    label = { Text(stringResource(R.string.no_clock)) },
                )
                TimeControl.PRESETS.forEachIndexed { index, control ->
                    FilterChip(selected = timeIndex == index, onClick = { timeIndex = index }, label = { Text(control.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = stringResource(R.string.face_to_face),
                description = stringResource(R.string.face_to_face_description),
                checked = faceToFace,
                onChange = { faceToFace = it },
            )
            if (startFen == null) {
                SwitchRow(
                    title = stringResource(R.string.chess960),
                    description = stringResource(R.string.chess960_description),
                    checked = chess960,
                    onChange = { chess960 = it },
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    onStart(
                        GameSetup(
                            startFen = startFen,
                            chess960 = chess960 && startFen == null,
                            timeControl = TimeControl.PRESETS.getOrNull(timeIndex),
                            faceToFace = faceToFace,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.start_game), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private const val BOT_ELO_STEP = 20

/** Options for a game against the computer. [startFen] is set when it continues from a position. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BotGameSetupSheet(onDismiss: () -> Unit, onStart: (GameSetup) -> Unit, startFen: String? = null) {
    // 0 White, 1 random, 2 Black.
    var sideChoice by rememberSaveable { mutableIntStateOf(0) }
    var engineIndex by rememberSaveable { mutableIntStateOf(0) }
    var elo by rememberSaveable { mutableIntStateOf(BotEngine.STOCKFISH.defaultElo) }
    val engine = BotEngine.entries[engineIndex]
    var timeIndex by rememberSaveable { mutableIntStateOf(-1) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(stringResource(R.string.play_computer), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.bot_engine), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotEngine.entries.forEachIndexed { index, candidate ->
                    FilterChip(
                        selected = engineIndex == index,
                        onClick = {
                            engineIndex = index
                            // Each engine has its own scale; start from a sensible point on it.
                            if (elo !in candidate.minElo..candidate.maxElo) elo = candidate.defaultElo
                        },
                        label = { Text(candidate.displayName) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.bot_side), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val labels = listOf(R.string.side_white, R.string.side_random, R.string.side_black)
                labels.forEachIndexed { index, label ->
                    FilterChip(selected = sideChoice == index, onClick = { sideChoice = index }, label = { Text(stringResource(label)) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bot_strength), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    if (elo >= engine.maxElo) stringResource(R.string.bot_strength_max) else stringResource(R.string.bot_strength_elo, elo),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = elo.toFloat(),
                onValueChange = { elo = (it / BOT_ELO_STEP).roundToInt() * BOT_ELO_STEP },
                valueRange = engine.minElo.toFloat()..engine.maxElo.toFloat(),
            )
            Text(stringResource(R.string.time_control), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = timeIndex == -1,
                    onClick = { timeIndex = -1 },
                    label = { Text(stringResource(R.string.no_clock)) },
                )
                TimeControl.PRESETS.forEachIndexed { index, control ->
                    FilterChip(selected = timeIndex == index, onClick = { timeIndex = index }, label = { Text(control.label) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val humanSide = when (sideChoice) {
                        0 -> Side.WHITE
                        2 -> Side.BLACK
                        else -> if (Random.nextBoolean()) Side.WHITE else Side.BLACK
                    }
                    onStart(
                        GameSetup(
                            startFen = startFen,
                            timeControl = TimeControl.PRESETS.getOrNull(timeIndex),
                            bot = BotSetup(humanSide, elo.takeIf { it < engine.maxElo }, engine),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.start_game), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
