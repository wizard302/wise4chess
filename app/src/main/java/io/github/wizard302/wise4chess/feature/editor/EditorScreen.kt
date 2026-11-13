package io.github.wizard302.wise4chess.feature.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wizard302.wise4chess.R
import io.github.wizard302.wise4chess.core.chess.Piece
import io.github.wizard302.wise4chess.core.chess.PieceType
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.ui.R as UiR
import io.github.wizard302.wise4chess.core.ui.board.PieceBackdrop
import io.github.wizard302.wise4chess.core.ui.board.SetupBoard
import io.github.wizard302.wise4chess.core.ui.board.pieceDrawable
import io.github.wizard302.wise4chess.feature.play.BotGameSetupSheet
import io.github.wizard302.wise4chess.feature.play.GameSetup
import io.github.wizard302.wise4chess.feature.play.LocalGameSetupSheet
import kotlinx.coroutines.launch

private val PALETTE = listOf(
    PieceType.KING, PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(onClose: () -> Unit, onPlay: (GameSetup) -> Unit, viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var botSetupOpen by rememberSaveable { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.editor_fen_copied)
    val invalidMessage = stringResource(R.string.editor_fen_invalid)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::flip) {
                        Icon(painterResource(UiR.drawable.ic_swap_vert), stringResource(R.string.action_flip))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(UiR.drawable.ic_more_vert), stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_start_position)) },
                            onClick = {
                                menuOpen = false
                                viewModel.reset()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_clear)) },
                            onClick = {
                                menuOpen = false
                                viewModel.clear()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_copy_fen)) },
                            onClick = {
                                menuOpen = false
                                clipboard(context).setPrimaryClip(ClipData.newPlainText("FEN", state.fen))
                                scope.launch { snackbar.showSnackbar(copiedMessage) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_paste_fen)) },
                            onClick = {
                                menuOpen = false
                                val text = clipboard(context).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                if (text == null || !viewModel.loadFen(text)) {
                                    scope.launch { snackbar.showSnackbar(invalidMessage) }
                                }
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            val top = state.orientation.opposite
            PaletteRow(top, state.tool, viewModel::selectTool, trailing = EditorTool.Erase)
            SetupBoard(
                pieces = state.pieces,
                onSquare = viewModel::onSquare,
                modifier = Modifier.fillMaxWidth(),
                orientation = state.orientation,
                selectedSquare = state.selectedSquare,
            )
            PaletteRow(state.orientation, state.tool, viewModel::selectTool, trailing = EditorTool.Move)

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.editor_side_to_move), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (side in Side.entries) {
                        FilterChip(
                            selected = state.sideToMove == side,
                            onClick = { viewModel.setSideToMove(side) },
                            label = { Text(stringResource(if (side == Side.WHITE) R.string.side_white else R.string.side_black)) },
                        )
                    }
                }
                Text(stringResource(R.string.editor_castling), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (right in CastlingRight.entries) {
                        val sideName = stringResource(if (right.side == Side.WHITE) R.string.side_white else R.string.side_black)
                        val available = right in state.availableCastling
                        FilterChip(
                            selected = available && right in state.castling,
                            enabled = available,
                            onClick = { viewModel.toggleCastling(right) },
                            label = { Text("$sideName ${if (right.rookFile == 7) "O-O" else "O-O-O"}") },
                        )
                    }
                }
                Text(
                    state.fen,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.problem?.let { problem ->
                    Text(
                        stringResource(
                            when (problem) {
                                EditorProblem.KINGS -> R.string.editor_problem_kings
                                EditorProblem.PAWN_ON_BACK_RANK -> R.string.editor_problem_pawns
                                EditorProblem.OPPONENT_IN_CHECK -> R.string.editor_problem_check
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.editor_play_from_here), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { botSetupOpen = true },
                        enabled = state.position != null,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Text(stringResource(R.string.editor_against_computer))
                    }
                    OutlinedButton(
                        onClick = { setupOpen = true },
                        enabled = state.position != null,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Text(stringResource(R.string.editor_two_players))
                    }
                }
            }
        }
    }

    if (setupOpen) {
        LocalGameSetupSheet(
            onDismiss = { setupOpen = false },
            onStart = {
                setupOpen = false
                onPlay(it)
            },
            startFen = state.fen,
        )
    }
    if (botSetupOpen) {
        BotGameSetupSheet(
            onDismiss = { botSetupOpen = false },
            onStart = {
                botSetupOpen = false
                onPlay(it)
            },
            startFen = state.fen,
        )
    }
}

private fun clipboard(context: Context): ClipboardManager =
    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

/** Six pieces of one colour and one tool, as wide as the board. */
@Composable
private fun PaletteRow(side: Side, tool: EditorTool, onSelect: (EditorTool) -> Unit, trailing: EditorTool) {
    Row(Modifier.fillMaxWidth().background(PieceBackdrop)) {
        for (type in PALETTE) {
            val piece = Piece.of(side, type)
            PaletteCell(selected = tool == EditorTool.Place(piece), onClick = { onSelect(EditorTool.Place(piece)) }) {
                Image(painterResource(pieceDrawable(piece)), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        PaletteCell(selected = tool == trailing, onClick = { onSelect(trailing) }) {
            Icon(
                painterResource(if (trailing == EditorTool.Erase) UiR.drawable.ic_ink_eraser else UiR.drawable.ic_pan_tool),
                contentDescription = stringResource(
                    if (trailing == EditorTool.Erase) R.string.editor_tool_erase else R.string.editor_tool_move,
                ),
                tint = if (tool == trailing) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF1B1E1B),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PaletteCell(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
