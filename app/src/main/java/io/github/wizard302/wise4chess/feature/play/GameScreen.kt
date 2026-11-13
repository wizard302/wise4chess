package io.github.wizard302.wise4chess.feature.play

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wizard302.wise4chess.R
import io.github.wizard302.wise4chess.audio.SoundPlayer
import io.github.wizard302.wise4chess.core.chess.GameResult
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.ui.R as UiR
import io.github.wizard302.wise4chess.core.engine.Score
import io.github.wizard302.wise4chess.core.ui.board.BoardArrow
import io.github.wizard302.wise4chess.core.ui.board.ChessBoard
import io.github.wizard302.wise4chess.core.ui.board.EvalBar
import io.github.wizard302.wise4chess.core.ui.board.whiteShare
import io.github.wizard302.wise4chess.settings.LocalAppSettings

private enum class Prompt { RESIGN, DRAW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val soundPlayer = remember { SoundPlayer(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { soundPlayer.release() } }
    LaunchedEffect(Unit) {
        viewModel.sounds.collect {
            soundPlayer.play(it)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pauseClock() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.resumeClock() }
    val showEvaluation = LocalAppSettings.current.showEvaluation
    LaunchedEffect(showEvaluation) { viewModel.setEvaluationEnabled(showEvaluation) }

    var prompt by remember { mutableStateOf<Prompt?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    // The result is announced once; after closing the dialog the final position can be studied.
    var announced by remember { mutableStateOf<GameEnd?>(null) }
    // One stable reference, so the clock ticking does not redraw the board.
    val onMove = remember(viewModel) { viewModel::onMove }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.bot != null) R.string.game_vs_computer else R.string.game_two_players))
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::flipBoard) {
                        Icon(painterResource(UiR.drawable.ic_swap_vert), stringResource(R.string.action_flip))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(UiR.drawable.ic_more_vert), stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (state.bot == null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.face_to_face)) },
                                trailingIcon = {
                                    if (state.faceToFace) Icon(painterResource(UiR.drawable.ic_check), contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    viewModel.setFaceToFace(!state.faceToFace)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_game)) },
                            onClick = {
                                menuOpen = false
                                viewModel.newGame()
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        val material = remember(state.position, state.startPosition) {
            MaterialSummary.of(state.position, state.startPosition)
        }
        val top = state.orientation.opposite
        val bottom = state.orientation

        @Composable
        fun bar(side: Side, upsideDown: Boolean) {
            val bot = state.bot
            val name = when {
                bot == null -> stringResource(if (side == Side.WHITE) R.string.side_white else R.string.side_black)
                side == bot.humanSide -> stringResource(R.string.player_you)
                else -> {
                    val engine = state.botName.ifEmpty { bot.engine.displayName }
                    if (bot.elo != null) "$engine · ${bot.elo}" else engine
                }
            }
            PlayerBar(
                name = name,
                side = side,
                taken = if (side == Side.WHITE) material.takenByWhite else material.takenByBlack,
                lead = if (side == Side.WHITE) material.balance else -material.balance,
                clockMillis = if (side == Side.WHITE) state.whiteMillis else state.blackMillis,
                clockRunning = state.clockSide == side,
                toMove = state.end == null && state.isLive && state.position.sideToMove == side,
                thinking = state.botThinking && bot != null && side != bot.humanSide,
                upsideDown = upsideDown,
            )
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            MoveStrip(
                moves = state.moves,
                viewPly = state.viewPly,
                firstMoveNumber = state.firstMoveNumber,
                blackMovesFirst = state.blackMovesFirst,
                onSelect = viewModel::goTo,
            )
            Spacer(Modifier.weight(1f))
            bar(top, upsideDown = state.faceToFace)
            val arrows = remember(state.hints) {
                state.hints.mapIndexed { index, hint ->
                    BoardArrow(hint.move.from, hint.move.to, HINT_COLORS[index.coerceAtMost(2)], HINT_WIDTHS[index.coerceAtMost(2)])
                }.asReversed() // the best move is drawn last, on top of the others
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // The evaluation bar takes a sliver of the width; the board stays square.
                val barWidth = if (state.evaluationShown) 12.dp else 0.dp
                val boardSize = maxWidth - barWidth
                Row {
                    if (state.evaluationShown) {
                        val evaluation = state.evaluation
                        EvalBar(
                            whiteShare = evaluation?.let {
                                whiteShare((it as? Score.Centipawns)?.value, (it as? Score.Mate)?.moves)
                            },
                            orientation = state.orientation,
                            modifier = Modifier.width(barWidth).height(boardSize),
                        )
                    }
                    ChessBoard(
                        position = state.position,
                        onMove = onMove,
                        modifier = Modifier.size(boardSize),
                        orientation = state.orientation,
                        lastMove = state.lastMove,
                        interactive = state.canMove,
                        dragEnabled = LocalAppSettings.current.dragPieces,
                        upsideDownSide = if (state.faceToFace) top else null,
                        arrows = if (state.isLive) arrows else emptyList(),
                    )
                }
            }
            bar(bottom, upsideDown = false)
            if (state.hints.isNotEmpty() && state.isLive) {
                Text(
                    state.hints.joinToString("    ") { "${it.san} ${formatScore(it.score)}" },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                ControlButton(
                    UiR.drawable.ic_undo, stringResource(R.string.action_take_back), viewModel::takeBack,
                    Modifier.weight(1f), enabled = state.canTakeBack,
                )
                ControlButton(
                    UiR.drawable.ic_chevron_left, stringResource(R.string.action_previous), viewModel::stepBack,
                    Modifier.weight(1f), enabled = state.viewPly > 0,
                )
                ControlButton(
                    UiR.drawable.ic_chevron_right, stringResource(R.string.action_next), viewModel::stepForward,
                    Modifier.weight(1f), enabled = !state.isLive,
                )
                if (state.end == null) {
                    if (state.bot == null) {
                        ControlButton(
                            UiR.drawable.ic_handshake, stringResource(R.string.action_draw), { prompt = Prompt.DRAW },
                            Modifier.weight(1f),
                        )
                    } else {
                        ControlButton(
                            UiR.drawable.ic_lightbulb, stringResource(R.string.action_hint), viewModel::requestHint,
                            Modifier.weight(1f), enabled = state.canMove && !state.hintPending,
                        )
                    }
                    ControlButton(
                        UiR.drawable.ic_flag, stringResource(R.string.action_resign), { prompt = Prompt.RESIGN },
                        Modifier.weight(1f),
                    )
                } else {
                    ControlButton(
                        UiR.drawable.ic_restart_alt, stringResource(R.string.new_game), viewModel::newGame,
                        Modifier.weight(2f),
                    )
                }
            }
        }
    }

    val end = state.end
    if (end != null && announced != end) {
        AlertDialog(
            onDismissRequest = { announced = end },
            title = { Text(resultTitle(end.result, state.bot)) },
            text = { Text(reasonText(end, state.bot)) },
            confirmButton = {
                TextButton(onClick = {
                    announced = null
                    viewModel.newGame()
                }) { Text(stringResource(R.string.action_rematch)) }
            },
            dismissButton = {
                TextButton(onClick = { announced = end }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
    LaunchedEffect(end) {
        if (end == null) announced = null
    }

    if (state.engineFailed) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.engine_failed_title)) },
            text = { Text(stringResource(R.string.engine_failed_text)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
        )
    }

    when (prompt) {
        Prompt.RESIGN -> ConfirmDialog(
            title = stringResource(R.string.confirm_resign_title),
            text = if (state.bot != null) {
                null
            } else {
                stringResource(
                    R.string.confirm_resign_text,
                    stringResource(if (state.position.sideToMove == Side.WHITE) R.string.side_white else R.string.side_black),
                )
            },
            confirmLabel = stringResource(R.string.action_resign),
            onConfirm = viewModel::resign,
            onDismiss = { prompt = null },
        )
        Prompt.DRAW -> ConfirmDialog(
            title = stringResource(R.string.confirm_draw_title),
            text = null,
            confirmLabel = stringResource(R.string.action_draw),
            onConfirm = viewModel::agreeDraw,
            onDismiss = { prompt = null },
        )
        null -> {}
    }
}

/** Best suggestion in green, the alternatives quieter and thinner. */
private val HINT_COLORS = listOf(Color(0xD92E9E48), Color(0xB33D7FC4), Color(0x8C3D7FC4))
private val HINT_WIDTHS = listOf(0.18f, 0.13f, 0.1f)

/** "+0.3" in pawns from White's side, or "#3" for a mate in three ("#-3" when Black mates). */
fun formatScore(score: Score): String = when (score) {
    is Score.Centipawns -> "%+.1f".format(java.util.Locale.ROOT, score.value / 100.0)
    is Score.Mate -> "#${score.moves}"
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun resultTitle(result: GameResult, bot: BotSetup?): String {
    if (bot != null && result != GameResult.DRAW) {
        val humanWon = (result == GameResult.WHITE_WINS) == (bot.humanSide == Side.WHITE)
        return stringResource(if (humanWon) R.string.result_you_won else R.string.result_you_lost)
    }
    return stringResource(
        when (result) {
            GameResult.WHITE_WINS -> R.string.result_white_wins
            GameResult.BLACK_WINS -> R.string.result_black_wins
            else -> R.string.result_draw
        },
    )
}

@Composable
private fun reasonText(end: GameEnd, bot: BotSetup?): String {
    // Against the computer "the opponent" would be wrong when it is the person who gave up.
    if (bot != null && end.result != GameResult.DRAW) {
        val humanLost = (end.result == GameResult.WHITE_WINS) != (bot.humanSide == Side.WHITE)
        if (humanLost && end.reason == EndReason.RESIGNATION) return stringResource(R.string.reason_you_resigned)
        if (humanLost && end.reason == EndReason.TIMEOUT) return stringResource(R.string.reason_you_timeout)
    }
    return reasonText(end.reason)
}

@Composable
private fun reasonText(reason: EndReason): String = stringResource(
    when (reason) {
        EndReason.CHECKMATE -> R.string.reason_checkmate
        EndReason.STALEMATE -> R.string.reason_stalemate
        EndReason.INSUFFICIENT_MATERIAL -> R.string.reason_insufficient_material
        EndReason.SEVENTY_FIVE_MOVES -> R.string.reason_seventy_five_moves
        EndReason.FIVEFOLD_REPETITION -> R.string.reason_fivefold_repetition
        EndReason.RESIGNATION -> R.string.reason_resignation
        EndReason.DRAW_AGREED -> R.string.reason_draw_agreed
        EndReason.TIMEOUT -> R.string.reason_timeout
        EndReason.TIMEOUT_INSUFFICIENT_MATERIAL -> R.string.reason_timeout_insufficient_material
    },
)
