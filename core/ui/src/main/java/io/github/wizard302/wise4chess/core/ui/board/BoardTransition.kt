package io.github.wizard302.wise4chess.core.ui.board

import io.github.wizard302.wise4chess.core.chess.Piece
import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.Square
import kotlin.math.abs
import kotlin.math.max

internal class PieceMotion(val piece: Piece, val from: Int, val to: Int)

/**
 * What has to be animated to get from one position to another: pieces that slide and pieces
 * that vanish. It is worked out from the difference between the two placements, so moves,
 * take-backs and castling in any variant all animate without special cases.
 */
internal class BoardTransition private constructor(
    val moving: List<PieceMotion>,
    /** Pieces that are gone in the new position; [PieceMotion.to] is unused. */
    val fading: List<PieceMotion>,
) {
    /** Bitboard of the squares that sliding pieces are heading for. */
    val arriving: Long = moving.fold(0L) { bits, motion -> bits or (1L shl motion.to) }

    val isEmpty: Boolean get() = moving.isEmpty() && fading.isEmpty()

    companion object {
        private val NONE = BoardTransition(emptyList(), emptyList())

        /** Beyond this many changed pieces it is a different position, not a move: no animation. */
        private const val MAX_APPEARING = 4

        /**
         * @param droppedOn square where the user has just dropped a piece by hand, or -1; that
         *   piece is already where it belongs and does not slide.
         */
        fun between(old: Position, new: Position, droppedOn: Int): BoardTransition {
            if (old == new) return NONE
            val gone = ArrayList<PieceMotion>()
            val appeared = ArrayList<PieceMotion>()
            for (index in 0 until 64) {
                val before = old.pieceAt(Square(index))
                val after = new.pieceAt(Square(index))
                if (before == after) continue
                if (before != null) gone.add(PieceMotion(before, index, index))
                if (after != null) appeared.add(PieceMotion(after, index, index))
            }
            if (appeared.size > MAX_APPEARING) return NONE

            val moving = ArrayList<PieceMotion>()
            for (arrival in appeared) {
                // The same piece from the nearest vacated square; failing that any piece of that
                // side, which is a promotion or its take-back.
                val source = gone.filter { it.piece == arrival.piece }.minByOrNull { distance(it.from, arrival.to) }
                    ?: gone.filter { it.piece.side == arrival.piece.side }.minByOrNull { distance(it.from, arrival.to) }
                    ?: continue
                gone.remove(source)
                if (arrival.to != droppedOn) moving.add(PieceMotion(arrival.piece, source.from, arrival.to))
            }
            return BoardTransition(moving, gone)
        }

        private fun distance(a: Int, b: Int): Int = max(abs((a and 7) - (b and 7)), abs((a ushr 3) - (b ushr 3)))
    }
}
