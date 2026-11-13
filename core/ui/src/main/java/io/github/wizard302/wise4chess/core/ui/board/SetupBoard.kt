package io.github.wizard302.wise4chess.core.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.wizard302.wise4chess.core.chess.Piece
import io.github.wizard302.wise4chess.core.chess.Side

/**
 * A board for arranging pieces freely, with no rules applied: the placement need not be a
 * legal position.
 *
 * @param pieces 64 entries, a1 first.
 * @param onSquare called for the square a touch lands on and then for every further square the
 *   finger is dragged across (`dragged` is true for those), so pieces can be painted.
 */
@Composable
fun SetupBoard(
    pieces: List<Piece?>,
    onSquare: (square: Int, dragged: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    orientation: Side = Side.WHITE,
    selectedSquare: Int = -1,
    colors: BoardColors = BoardColors.Green,
) {
    val painters = rememberPiecePainters()
    val textMeasurer = rememberTextMeasurer(cacheSize = 16)
    val currentOnSquare by rememberUpdatedState(onSquare)

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(orientation) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val geometry = BoardGeometry(size.width.toFloat(), orientation)
                    var last = geometry.squareAt(down.position)
                    if (last >= 0) currentOnSquare(last, false)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val square = geometry.squareAt(change.position)
                        if (square >= 0 && square != last) {
                            last = square
                            currentOnSquare(square, true)
                        }
                        change.consume() // keep a surrounding scroll container still
                    }
                }
            },
    ) {
        val geometry = BoardGeometry(size.width, orientation)
        val sq = geometry.square
        drawRect(colors.lightSquare)
        for (index in 0 until 64) {
            if (((index and 7) + (index ushr 3)) % 2 == 0) drawRect(colors.darkSquare, geometry.topLeft(index), Size(sq, sq))
        }
        if (selectedSquare >= 0) drawRect(colors.selected, geometry.topLeft(selectedSquare), Size(sq, sq))
        drawCoordinates(geometry, textMeasurer, colors)
        for (index in 0 until 64) {
            val piece = pieces[index] ?: continue
            drawPiece(painters[piece.ordinal], geometry.topLeft(index), sq, upsideDown = false)
        }
    }
}
