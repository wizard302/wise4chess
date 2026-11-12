package io.github.wizard302.wise4chess.core.chess

import org.junit.Assert.assertEquals
import org.junit.Test

class Chess960Test {
    @Test
    fun allStartingPositionsMatchTheReference() {
        val expected = checkNotNull(javaClass.getResourceAsStream("/fixtures/chess960.txt"))
            .bufferedReader().readLines()
        assertEquals(Chess960.POSITION_COUNT, expected.size)
        for (index in 0 until Chess960.POSITION_COUNT) {
            assertEquals("index $index", expected[index], Chess960.backRank(index))
        }
        assertEquals(Chess960.POSITION_COUNT, expected.toSet().size)
    }

    @Test
    fun standardIndexIsTheClassicalPosition() {
        assertEquals(Position.START, Chess960.startPosition(Chess960.STANDARD_INDEX))
    }

    @Test
    fun everyStartingPositionHasBothCastlingRightsAndTwentyMoves() {
        for (index in 0 until Chess960.POSITION_COUNT) {
            val position = Chess960.startPosition(index)
            assertEquals("index $index", 2, position.castlingRookSquares(Side.WHITE).size)
            assertEquals("index $index", 2, position.castlingRookSquares(Side.BLACK).size)
            assertEquals("index $index", Chess960.startFen(index), position.toFen())
        }
    }

    @Test
    fun castlingIsWrittenForBothKindsOfEngine() {
        // King on f1 between rooks on b1 and g1: both castling moves are available at once.
        val position = Position.fromFen("5k2/8/8/8/8/8/8/1R3KR1 w GB - 0 1")
        val kingside = position.parseSan("O-O")!!
        assertEquals("f1g1", kingside.toUci(chess960 = true))
        assertEquals("f1g1", kingside.toUci())
        assertEquals(Square.parse("g1"), kingside.castlingRookFrom)
        assertEquals(Square.parse("f1"), kingside.castlingRookTo)
        val queenside = position.parseSan("O-O-O")!!
        assertEquals("f1b1", queenside.toUci(chess960 = true))
        assertEquals("f1c1", queenside.toUci())
        assertEquals("5k2/8/8/8/8/8/8/2KR2R1 b - - 1 1", position.play(queenside).toFen())
        assertEquals("5k2/8/8/8/8/8/8/1R3RK1 b - - 1 1", position.play(kingside).toFen())
        // A plain king step stays a plain king step.
        assertEquals(false, position.parseUci("f1e1")!!.isCastling)
    }
}
