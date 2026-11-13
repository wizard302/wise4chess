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
import io.github.wizard302.wise4chess.core.ui.R as UiR

/** The Play section: ways to start a game. */
@Composable
fun PlayScreen(onStartLocalGame: (GameSetup) -> Unit, onOpenEditor: () -> Unit) {
    var setupOpen by rememberSaveable { mutableStateOf(false) }

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
                onStartLocalGame(it)
            },
        )
    }
}

@Composable
private fun ModeCard(iconRes: Int, title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
