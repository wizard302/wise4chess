package io.github.wizard302.wise4chess.core.ui.board

import androidx.compose.ui.geometry.Offset
import io.github.wizard302.wise4chess.core.chess.Side

/** Maps between board squares (a1 = 0 … h8 = 63) and pixels for a board seen from [orientation]. */
internal class BoardGeometry(val boardSize: Float, val orientation: Side) {
    val square: Float = boardSize / 8f

    fun column(squareIndex: Int): Int {
        val file = squareIndex and 7
        return if (orientation == Side.WHITE) file else 7 - file
    }

    fun row(squareIndex: Int): Int {
        val rank = squareIndex ushr 3
        return if (orientation == Side.WHITE) 7 - rank else rank
    }

    fun topLeft(squareIndex: Int): Offset = Offset(column(squareIndex) * square, row(squareIndex) * square)

    fun center(squareIndex: Int): Offset = topLeft(squareIndex) + Offset(square / 2f, square / 2f)

    /** Square under [point], or -1 if it lies outside the board. */
    fun squareAt(point: Offset): Int {
        if (point.x < 0f || point.y < 0f || point.x >= boardSize || point.y >= boardSize) return -1
        return squareAt((point.x / square).toInt().coerceIn(0, 7), (point.y / square).toInt().coerceIn(0, 7))
    }

    fun squareAt(column: Int, row: Int): Int {
        val file = if (orientation == Side.WHITE) column else 7 - column
        val rank = if (orientation == Side.WHITE) 7 - row else row
        return rank * 8 + file
    }
}
