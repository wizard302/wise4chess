package io.github.wizard302.wise4chess.core.ui.board

import androidx.compose.ui.geometry.Offset
import io.github.wizard302.wise4chess.core.chess.Piece
import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.chess.Square
import io.github.wizard302.wise4chess.core.chess.parseSan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTransitionTest {
    private fun sq(name: String) = Square.parse(name)!!.index

    private fun Position.after(san: String) = play(parseSan(san)!!)

    private fun BoardTransition.moves() = moving.map { Triple(it.piece, it.from, it.to) }.toSet()

    @Test
    fun samePositionNeedsNoAnimation() {
        assertTrue(BoardTransition.between(Position.START, Position.START, -1).isEmpty)
    }

    @Test
    fun quietMoveSlidesOnePiece() {
        val transition = BoardTransition.between(Position.START, Position.START.after("Nf3"), -1)
        assertEquals(setOf(Triple(Piece.WHITE_KNIGHT, sq("g1"), sq("f3"))), transition.moves())
        assertTrue(transition.fading.isEmpty())
        assertEquals(1L shl sq("f3"), transition.arriving)
    }

    @Test
    fun captureSlidesTheTakerAndFadesTheVictim() {
        val before = Position.fromFen("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")
        val transition = BoardTransition.between(before, before.after("exd5"), -1)
        assertEquals(setOf(Triple(Piece.WHITE_PAWN, sq("e4"), sq("d5"))), transition.moves())
        assertEquals(listOf(Piece.BLACK_PAWN to sq("d5")), transition.fading.map { it.piece to it.from })
    }

    @Test
    fun takeBackOfACaptureSlidesThePieceHome() {
        val before = Position.fromFen("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")
        val transition = BoardTransition.between(before.after("exd5"), before, -1)
        // The pawn returns to e4; the captured pawn simply reappears on d5.
        assertEquals(setOf(Triple(Piece.WHITE_PAWN, sq("d5"), sq("e4"))), transition.moves())
        assertTrue(transition.fading.isEmpty())
    }

    @Test
    fun castlingSlidesKingAndRook() {
        val before = Position.fromFen("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        val transition = BoardTransition.between(before, before.after("O-O"), -1)
        assertEquals(
            setOf(Triple(Piece.WHITE_KING, sq("e1"), sq("g1")), Triple(Piece.WHITE_ROOK, sq("h1"), sq("f1"))),
            transition.moves(),
        )
    }

    @Test
    fun chess960CastlingWhereKingAndRookSwapSquares() {
        val before = Position.fromFen("5k2/8/8/8/8/8/8/5KR1 w G - 0 1")
        val transition = BoardTransition.between(before, before.after("O-O"), -1)
        assertEquals(
            setOf(Triple(Piece.WHITE_KING, sq("f1"), sq("g1")), Triple(Piece.WHITE_ROOK, sq("g1"), sq("f1"))),
            transition.moves(),
        )
    }

    @Test
    fun promotionSlidesTheNewPieceFromThePawnSquare() {
        val before = Position.fromFen("8/P1k5/8/8/8/8/8/4K3 w - - 0 1")
        val transition = BoardTransition.between(before, before.after("a8=Q"), -1)
        assertEquals(setOf(Triple(Piece.WHITE_QUEEN, sq("a7"), sq("a8"))), transition.moves())
        assertTrue(transition.fading.isEmpty())
    }

    @Test
    fun enPassantFadesThePawnThatIsNotOnTheTargetSquare() {
        val before = Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        val transition = BoardTransition.between(before, before.after("exd6"), -1)
        assertEquals(setOf(Triple(Piece.WHITE_PAWN, sq("e5"), sq("d6"))), transition.moves())
        assertEquals(listOf(sq("d5")), transition.fading.map { it.from })
    }

    @Test
    fun aPieceDroppedByHandDoesNotSlideButTheCastlingRookDoes() {
        val before = Position.fromFen("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        val transition = BoardTransition.between(before, before.after("O-O"), droppedOn = sq("g1"))
        assertEquals(setOf(Triple(Piece.WHITE_ROOK, sq("h1"), sq("f1"))), transition.moves())
    }

    @Test
    fun unrelatedPositionIsNotAnimated() {
        val other = Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertTrue(BoardTransition.between(Position.START, other, -1).isEmpty)
    }

    @Test
    fun geometryMapsSquaresBothWays() {
        val white = BoardGeometry(800f, Side.WHITE)
        assertEquals(Offset(0f, 700f), white.topLeft(sq("a1")))
        assertEquals(Offset(700f, 0f), white.topLeft(sq("h8")))
        assertEquals(sq("e4"), white.squareAt(Offset(450f, 450f)))
        assertEquals(-1, white.squareAt(Offset(800f, 10f)))
        assertEquals(-1, white.squareAt(Offset(-1f, 10f)))
        val black = BoardGeometry(800f, Side.BLACK)
        assertEquals(Offset(700f, 0f), black.topLeft(sq("a1")))
        assertEquals(Offset(0f, 700f), black.topLeft(sq("h8")))
        for (index in 0 until 64) {
            assertEquals(index, white.squareAt(white.center(index)))
            assertEquals(index, black.squareAt(black.center(index)))
            assertEquals(index, black.squareAt(black.column(index), black.row(index)))
        }
    }
}
