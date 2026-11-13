package io.github.wizard302.wise4chess.core.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.wizard302.wise4chess.core.chess.Move
import io.github.wizard302.wise4chess.core.chess.Piece
import io.github.wizard302.wise4chess.core.chess.PieceType
import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.chess.Square

private const val ANIMATION_MILLIS = 170

/** Test tag of the board, for UI tests that touch squares. */
const val CHESS_BOARD_TAG = "chess_board"

/** Pieces offered when a pawn promotes, nearest the promotion square first. */
private val PROMOTION_CHOICES = listOf(PieceType.QUEEN, PieceType.KNIGHT, PieceType.ROOK, PieceType.BISHOP)

private class Drag(val from: Int, val pointer: Offset)

private class PendingPromotion(val to: Int, val moves: List<Move>, val viaDrag: Boolean)

/** Remembers the previously shown position so that a change can be animated. */
private class BoardMemory(var position: Position) {
    /** Destination of a piece the user has just dropped by hand; it must not slide again. */
    var droppedOn: Int = -1
}

/**
 * An interactive chess board.
 *
 * The board is stateless about the game: it shows [position] and reports the move the user
 * chose through [onMove]; the caller then passes the resulting position back in. Pieces can be
 * moved by tapping the piece and then the target, or by dragging. Changes of [position] are
 * animated, whether they come from a move, a take-back or a jump through the game.
 *
 * @param orientation the side shown at the bottom.
 * @param lastMove highlighted on the board, usually the move that led to [position].
 * @param interactive whether the side to move may be moved by the user right now.
 * @param dragEnabled whether pieces can be dragged; tapping a piece and then a square always works.
 * @param arrows drawn over the pieces, for suggested moves.
 * @param upsideDownSide pieces of this side are drawn rotated by 180°, for two players facing
 *   each other across one device.
 */
@Composable
fun ChessBoard(
    position: Position,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
    orientation: Side = Side.WHITE,
    lastMove: Move? = null,
    interactive: Boolean = true,
    dragEnabled: Boolean = true,
    upsideDownSide: Side? = null,
    arrows: List<BoardArrow> = emptyList(),
    colors: BoardColors = BoardColors.Green,
    showCoordinates: Boolean = true,
    showLegalMoves: Boolean = true,
) {
    val painters = rememberPiecePainters()
    val textMeasurer = rememberTextMeasurer(cacheSize = 16)
    val currentOnMove by rememberUpdatedState(onMove)

    // Selection, drag and the promotion chooser belong to one position and reset with it.
    var selected by remember(position) { mutableIntStateOf(-1) }
    var drag by remember(position) { mutableStateOf<Drag?>(null) }
    var promotion by remember(position) { mutableStateOf<PendingPromotion?>(null) }

    val memory = remember { BoardMemory(position) }
    val transition = remember(position) { BoardTransition.between(memory.position, position, memory.droppedOn) }
    val progress = remember(position) { Animatable(if (transition.isEmpty) 1f else 0f) }
    LaunchedEffect(position) {
        progress.animateTo(1f, tween(ANIMATION_MILLIS, easing = FastOutSlowInEasing))
    }
    SideEffect {
        memory.position = position
        memory.droppedOn = -1
    }

    fun play(move: Move, viaDrag: Boolean) {
        if (viaDrag) memory.droppedOn = move.to.index
        currentOnMove(move)
    }

    /** Plays the move from [from] to [to], or opens the promotion chooser. False if there is none. */
    fun tryMove(from: Int, to: Int, viaDrag: Boolean): Boolean {
        val fromHere = position.legalMoves.filter { it.from.index == from }
        var candidates = fromHere.filter { !it.isCastling && it.to.index == to }
        // Castling is given by the king's target square. Putting the king on its rook also
        // works when dragging, and by tapping only where the squares are not the standard ones
        // (Chess960): in a normal game a tap on the rook means "select the rook instead".
        if (candidates.isEmpty()) candidates = fromHere.filter { it.isCastling && it.to.index == to }
        if (candidates.isEmpty()) {
            candidates = fromHere.filter {
                it.isCastling && it.castlingRookFrom!!.index == to && (viaDrag || !it.isStandardCastling())
            }
        }
        when (candidates.size) {
            0 -> return false
            1 -> play(candidates[0], viaDrag)
            else -> promotion = PendingPromotion(to, candidates, viaDrag)
        }
        return true
    }

    val origin = drag?.from ?: selected
    val targets = remember(position, origin) { MoveTargets.from(position, origin) }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .testTag(CHESS_BOARD_TAG)
            .pointerInput(position, interactive, dragEnabled, orientation) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val geometry = BoardGeometry(size.width.toFloat(), orientation)

                    val pending = promotion
                    if (pending != null) {
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        promotion = null
                        promotionChoiceAt(geometry, pending, up.position)?.let { play(it, pending.viaDrag) }
                        return@awaitEachGesture
                    }

                    val square = geometry.squareAt(down.position)
                    if (square < 0 || !interactive) return@awaitEachGesture
                    val previouslySelected = selected
                    if (previouslySelected >= 0 && previouslySelected != square &&
                        tryMove(previouslySelected, square, viaDrag = false)
                    ) {
                        return@awaitEachGesture
                    }
                    if (position.pieceAt(Square(square))?.side != position.sideToMove) {
                        selected = -1
                        return@awaitEachGesture
                    }
                    if (!dragEnabled) {
                        // A second tap on the selected piece puts it down.
                        selected = if (previouslySelected == square) -1 else square
                        return@awaitEachGesture
                    }
                    selected = square

                    var dragging = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (dragging) {
                                val target = geometry.squareAt(change.position)
                                drag = null
                                if (target >= 0 && target != square) tryMove(square, target, viaDrag = true)
                            } else if (previouslySelected == square) {
                                selected = -1 // a second tap on the selected piece puts it down
                            }
                            break
                        }
                        if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                        }
                        if (dragging) {
                            drag = Drag(square, change.position)
                            change.consume()
                        }
                    }
                    drag = null
                }
            },
    ) {
        val geometry = BoardGeometry(size.width, orientation)
        val sq = geometry.square
        val squareSize = Size(sq, sq)

        drawRect(colors.lightSquare)
        for (index in 0 until 64) {
            if (((index and 7) + (index ushr 3)) % 2 == 0) drawRect(colors.darkSquare, geometry.topLeft(index), squareSize)
        }

        if (lastMove != null) {
            drawRect(colors.lastMove, geometry.topLeft(lastMove.from.index), squareSize)
            drawRect(colors.lastMove, geometry.topLeft(lastMove.to.index), squareSize)
        }
        if (origin >= 0) drawRect(colors.selected, geometry.topLeft(origin), squareSize)
        if (position.isCheck) {
            val center = geometry.center(position.kingSquare(position.sideToMove).index)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to colors.check,
                    0.55f to colors.check.copy(alpha = 0.75f),
                    1f to Color.Transparent,
                    center = center,
                    radius = sq * 0.62f,
                ),
                radius = sq * 0.62f,
                center = center,
            )
        }

        if (showCoordinates) drawCoordinates(geometry, textMeasurer, colors)

        if (showLegalMoves && origin >= 0) {
            for (index in 0 until 64) {
                val bit = 1L shl index
                if (targets.quiet and bit != 0L) {
                    drawCircle(colors.legalMove, sq * 0.16f, geometry.center(index))
                } else if (targets.captures and bit != 0L) {
                    drawCircle(colors.legalMove, sq * 0.44f, geometry.center(index), style = Stroke(sq * 0.09f))
                }
            }
        }

        // The square under a dragged piece, so it is clear where it would land.
        val dragged = drag
        if (dragged != null) {
            val hover = geometry.squareAt(dragged.pointer)
            if (hover >= 0 && (targets.quiet or targets.captures) and (1L shl hover) != 0L) {
                drawRect(colors.selected, geometry.topLeft(hover), squareSize)
            }
        }

        val animating = progress.value < 1f
        for (index in 0 until 64) {
            val piece = position.pieceAt(Square(index)) ?: continue
            if (animating && transition.arriving and (1L shl index) != 0L) continue
            val alpha = if (dragged?.from == index) 0.3f else 1f
            drawPiece(painters[piece.ordinal], geometry.topLeft(index), sq, piece.side == upsideDownSide, alpha)
        }
        if (animating) {
            val t = progress.value
            for (fading in transition.fading) {
                drawPiece(
                    painters[fading.piece.ordinal], geometry.topLeft(fading.from), sq,
                    fading.piece.side == upsideDownSide, 1f - t,
                )
            }
            for (moving in transition.moving) {
                val start = geometry.topLeft(moving.from)
                val end = geometry.topLeft(moving.to)
                drawPiece(
                    painters[moving.piece.ordinal], start + (end - start) * t, sq,
                    moving.piece.side == upsideDownSide,
                )
            }
        }
        for (arrow in arrows) drawArrow(arrow, geometry)

        if (dragged != null) {
            position.pieceAt(Square(dragged.from))?.let { piece ->
                // Raised above the finger so the piece stays visible. It keeps its size: a vector
                // painter drawn at two sizes in one frame repaints every piece that shares it at
                // the wrong size.
                val lift = if (piece.side == upsideDownSide) sq * 0.5f else -sq * 0.5f
                val topLeft = dragged.pointer + Offset(-sq / 2f, -sq / 2f + lift)
                drawPiece(painters[piece.ordinal], topLeft, sq, piece.side == upsideDownSide)
            }
        }

        promotion?.let { pending ->
            drawRect(Color.Black.copy(alpha = 0.5f))
            val side = position.sideToMove
            val column = geometry.column(pending.to)
            val row = geometry.row(pending.to)
            val step = if (row == 0) 1 else -1
            for ((i, type) in PROMOTION_CHOICES.withIndex()) {
                val topLeft = Offset(column * sq, (row + i * step) * sq)
                drawCircle(Color(0xFFF4F4F1), sq * 0.47f, topLeft + Offset(sq / 2f, sq / 2f))
                drawPiece(painters[Piece.of(side, type).ordinal], topLeft, sq, side == upsideDownSide)
            }
        }
    }
}

/** King from the e-file castling with a rook from the a- or h-file. */
private fun Move.isStandardCastling(): Boolean = from.file == 4 && castlingRookFrom!!.file.let { it == 0 || it == 7 }

private fun promotionChoiceAt(geometry: BoardGeometry, pending: PendingPromotion, point: Offset): Move? {
    if (geometry.squareAt(point) < 0) return null
    val column = (point.x / geometry.square).toInt()
    val row = (point.y / geometry.square).toInt()
    val targetRow = geometry.row(pending.to)
    if (column != geometry.column(pending.to)) return null
    val index = if (targetRow == 0) row else targetRow - row
    val type = PROMOTION_CHOICES.getOrNull(index) ?: return null
    return pending.moves.firstOrNull { it.promotion == type }
}

internal fun DrawScope.drawPiece(painter: Painter, topLeft: Offset, size: Float, upsideDown: Boolean, alpha: Float = 1f) {
    withTransform({
        translate(topLeft.x, topLeft.y)
        if (upsideDown) rotate(180f, pivot = Offset(size / 2f, size / 2f))
    }) {
        with(painter) { draw(Size(size, size), alpha = alpha) }
    }
}

/** File letters along the bottom edge and rank numbers along the left edge, inside the squares. */
internal fun DrawScope.drawCoordinates(geometry: BoardGeometry, textMeasurer: TextMeasurer, colors: BoardColors) {
    val sq = geometry.square
    val inset = sq * 0.06f
    val style = TextStyle(fontSize = (sq * 0.2f).toSp(), fontWeight = FontWeight.SemiBold)
    for (i in 0 until 8) {
        val rankSquare = geometry.squareAt(0, i)
        val rankText = textMeasurer.measure(('1' + (rankSquare ushr 3)).toString(), style)
        drawText(rankText, coordinateColor(rankSquare, colors), Offset(inset, i * sq + inset * 0.5f))

        val fileSquare = geometry.squareAt(i, 7)
        val fileText = textMeasurer.measure(('a' + (fileSquare and 7)).toString(), style)
        drawText(
            fileText,
            coordinateColor(fileSquare, colors),
            Offset((i + 1) * sq - fileText.size.width - inset, size.height - fileText.size.height - inset * 0.3f),
        )
    }
}

/** Coordinates are written in the colour of the opposite kind of square. */
private fun coordinateColor(square: Int, colors: BoardColors): Color =
    if (((square and 7) + (square ushr 3)) % 2 == 0) colors.lightSquare else colors.darkSquare

/** Squares the piece on the origin square can move to, split by whether something is taken. */
private class MoveTargets(val quiet: Long, val captures: Long) {
    companion object {
        private val NONE = MoveTargets(0L, 0L)

        fun from(position: Position, origin: Int): MoveTargets {
            if (origin < 0) return NONE
            var quiet = 0L
            var captures = 0L
            for (move in position.legalMoves) {
                if (move.from.index != origin) continue
                val bit = 1L shl move.to.index
                if (!move.isCastling && (move.isEnPassant || position.pieceAt(move.to) != null)) {
                    captures = captures or bit
                } else {
                    quiet = quiet or bit
                }
            }
            return MoveTargets(quiet, captures)
        }
    }
}
