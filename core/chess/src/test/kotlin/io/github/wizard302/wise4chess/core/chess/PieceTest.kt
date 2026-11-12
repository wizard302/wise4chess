package io.github.wizard302.wise4chess.core.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PieceTest {
    @Test
    fun ofMatchesSideAndType() {
        for (side in Side.entries) {
            for (type in PieceType.entries) {
                val piece = Piece.of(side, type)
                assertEquals(side, piece.side)
                assertEquals(type, piece.type)
            }
        }
    }

    @Test
    fun fenCharsRoundTrip() {
        assertEquals("PNBRQKpnbrqk", Piece.entries.joinToString("") { it.fenChar.toString() })
        for (piece in Piece.entries) {
            assertEquals(piece, Piece.fromFenChar(piece.fenChar))
        }
    }

    @Test
    fun unknownFenCharIsRejected() {
        assertNull(Piece.fromFenChar('x'))
        assertNull(Piece.fromFenChar('1'))
    }

    @Test
    fun oppositeSide() {
        assertEquals(Side.BLACK, Side.WHITE.opposite)
        assertEquals(Side.WHITE, Side.BLACK.opposite)
    }
}
