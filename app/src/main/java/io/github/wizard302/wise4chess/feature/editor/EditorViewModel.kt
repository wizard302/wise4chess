package io.github.wizard302.wise4chess.feature.editor

import androidx.lifecycle.ViewModel
import io.github.wizard302.wise4chess.core.chess.Piece
import io.github.wizard302.wise4chess.core.chess.PieceType
import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.chess.Square
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What a touch on the board does. */
sealed interface EditorTool {
    /** Pick a piece up with one tap and put it down with the next. */
    data object Move : EditorTool

    data object Erase : EditorTool

    data class Place(val piece: Piece) : EditorTool
}

/** Why the arrangement cannot be played from. */
enum class EditorProblem { KINGS, PAWN_ON_BACK_RANK, OPPONENT_IN_CHECK }

/** The four castling rights of standard chess. */
enum class CastlingRight(val fenChar: Char, val side: Side, val rookFile: Int) {
    WHITE_KINGSIDE('K', Side.WHITE, 7),
    WHITE_QUEENSIDE('Q', Side.WHITE, 0),
    BLACK_KINGSIDE('k', Side.BLACK, 7),
    BLACK_QUEENSIDE('q', Side.BLACK, 0),
}

data class EditorUiState(
    /** 64 entries, a1 first. */
    val pieces: List<Piece?>,
    val sideToMove: Side,
    /** Rights the user has switched on; only [availableCastling] ones take effect. */
    val castling: Set<CastlingRight>,
    val tool: EditorTool,
    /** Square of the piece picked up with the move tool, or -1. */
    val selectedSquare: Int,
    val orientation: Side,
) {
    /** Rights the placement allows: king and rook on their original squares. */
    val availableCastling: Set<CastlingRight> = CastlingRight.entries.filterTo(LinkedHashSet()) { right ->
        val rank = if (right.side == Side.WHITE) 0 else 7
        pieces[rank * 8 + 4] == Piece.of(right.side, PieceType.KING) &&
            pieces[rank * 8 + right.rookFile] == Piece.of(right.side, PieceType.ROOK)
    }

    val fen: String = buildString {
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val piece = pieces[rank * 8 + file]
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) append(empty)
                    empty = 0
                    append(piece.fenChar)
                }
            }
            if (empty > 0) append(empty)
            if (rank > 0) append('/')
        }
        append(if (sideToMove == Side.WHITE) " w " else " b ")
        val rights = CastlingRight.entries.filter { it in castling && it in availableCastling }
        append(if (rights.isEmpty()) "-" else rights.joinToString("") { it.fenChar.toString() })
        append(" - 0 1")
    }

    /** The arrangement as a playable position, or null if it is not one. */
    val position: Position? = Position.fromFenOrNull(fen)

    val problem: EditorProblem? = when {
        position != null -> null
        pieces.count { it == Piece.WHITE_KING } != 1 || pieces.count { it == Piece.BLACK_KING } != 1 -> EditorProblem.KINGS
        (0..7).any { pieces[it]?.type == PieceType.PAWN || pieces[56 + it]?.type == PieceType.PAWN } ->
            EditorProblem.PAWN_ON_BACK_RANK
        // With both kings present and no stray pawns, the remaining reason is a king left in check.
        else -> EditorProblem.OPPONENT_IN_CHECK
    }
}

class EditorViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(fromPosition(Position.START, EditorTool.Move, Side.WHITE))
    val state: StateFlow<EditorUiState> = mutableState

    private fun fromPosition(position: Position, tool: EditorTool, orientation: Side): EditorUiState {
        val rights = CastlingRight.entries.filterTo(LinkedHashSet()) { right ->
            val rank = if (right.side == Side.WHITE) 0 else 7
            Square(rank * 8 + right.rookFile) in position.castlingRookSquares(right.side) &&
                position.kingSquare(right.side).index == rank * 8 + 4
        }
        return EditorUiState(
            pieces = List(64) { position.pieceAt(Square(it)) },
            sideToMove = position.sideToMove,
            castling = rights,
            tool = tool,
            selectedSquare = -1,
            orientation = orientation,
        )
    }

    fun selectTool(tool: EditorTool) {
        mutableState.value = state.value.copy(tool = tool, selectedSquare = -1)
    }

    fun onSquare(square: Int, dragged: Boolean) {
        val current = state.value
        val pieces = current.pieces.toMutableList()
        var selected = -1
        when (val tool = current.tool) {
            is EditorTool.Place ->
                // A tap on the same piece removes it again; dragging only ever paints.
                pieces[square] = if (!dragged && pieces[square] == tool.piece) null else tool.piece
            EditorTool.Erase -> pieces[square] = null
            EditorTool.Move -> {
                if (dragged) return
                val from = current.selectedSquare
                if (from < 0) {
                    if (pieces[square] != null) selected = square
                } else if (from != square) {
                    pieces[square] = pieces[from]
                    pieces[from] = null
                }
            }
        }
        mutableState.value = current.copy(pieces = pieces, selectedSquare = selected)
    }

    fun setSideToMove(side: Side) {
        mutableState.value = state.value.copy(sideToMove = side)
    }

    fun toggleCastling(right: CastlingRight) {
        val current = state.value
        mutableState.value = current.copy(
            castling = if (right in current.castling) current.castling - right else current.castling + right,
        )
    }

    fun flip() {
        mutableState.value = state.value.copy(orientation = state.value.orientation.opposite)
    }

    fun clear() {
        mutableState.value = state.value.copy(pieces = List(64) { null }, castling = emptySet(), selectedSquare = -1)
    }

    fun reset() = load(Position.START)

    /** Loads [fen]; false if it is not a valid position. */
    fun loadFen(fen: String): Boolean {
        load(Position.fromFenOrNull(fen) ?: return false)
        return true
    }

    private fun load(position: Position) {
        mutableState.value = fromPosition(position, state.value.tool, state.value.orientation)
    }
}
