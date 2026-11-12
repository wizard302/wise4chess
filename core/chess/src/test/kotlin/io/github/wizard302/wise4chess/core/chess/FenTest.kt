package io.github.wizard302.wise4chess.core.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FenTest {
    @Test
    fun startPosition() {
        val position = Position.START
        assertEquals(Position.START_FEN, position.toFen())
        assertEquals(Piece.WHITE_KING, position.pieceAt(Square.parse("e1")!!))
        assertEquals(Piece.BLACK_QUEEN, position.pieceAt(Square.parse("d8")!!))
        assertNull(position.pieceAt(Square.parse("e4")!!))
        assertEquals(Square.parse("e8"), position.kingSquare(Side.BLACK))
        assertEquals(20, position.legalMoves.size)
    }

    @Test
    fun missingTrailingFieldsGetDefaults() {
        assertEquals(
            "4k3/8/8/8/8/8/8/4K3 b - - 0 1",
            Position.fromFen("4k3/8/8/8/8/8/8/4K3 b").toFen(),
        )
        assertEquals("4k3/8/8/8/8/8/8/4K3 w - - 0 1", Position.fromFen("  4k3/8/8/8/8/8/8/4K3  ").toFen())
    }

    @Test
    fun malformedFensAreRejected() {
        val bad = listOf(
            "",
            "8/8/8/8/8/8/8/8 w - - 0 1", // no kings
            "4k3/8/8/8/8/8/8/4K2K w - - 0 1", // two white kings
            "4k3/8/8/8/8/8/8 w - - 0 1", // seven ranks
            "4k3/9/8/8/8/8/8/4K3 w - - 0 1", // bad digit
            "4k3/7/8/8/8/8/8/4K3 w - - 0 1", // short rank
            "4k3/8p/8/8/8/8/8/4K3 w - - 0 1", // long rank
            "4k2P/8/8/8/8/8/8/4K3 w - - 0 1", // pawn on the last rank
            "4k3/8/8/8/8/8/8/4K3 x - - 0 1", // bad side
            "4k3/8/8/8/8/8/8/4K3 w X - 0 1", // bad castling flag
            "4k3/8/8/8/8/8/8/4K3 w - z9 0 1", // bad en passant square
            "4k3/8/8/8/8/8/8/4K3 w - - -1 1", // negative counter
            "4k3/8/8/8/8/8/8/4K3 w - - x 1", // counter is not a number
            "4k3/8/8/8/8/8/4R3/4K3 w - - 0 1", // side not to move is in check
            "4k3/8/8/8/8/8/8/4K2x w - - 0 1", // unknown piece
        )
        for (fen in bad) assertNull("accepted: '$fen'", Position.fromFenOrNull(fen))
    }

    @Test(expected = InvalidFenException::class)
    fun fromFenThrowsOnBadInput() {
        Position.fromFen("not a fen")
    }

    @Test
    fun castlingRightsThatThePlacementContradictsAreDropped() {
        // White's king has left the back rank; Black has no rook on the kingside.
        assertEquals(
            "r3k3/8/8/8/8/8/3K4/R6R w q - 0 1",
            Position.fromFen("r3k3/8/8/8/8/8/3K4/R6R w KQkq - 0 1").toFen(),
        )
        assertEquals("4k3/8/8/8/8/8/8/4K3 w - - 0 1", Position.fromFen("4k3/8/8/8/8/8/8/4K3 w KQkq - 0 1").toFen())
    }

    @Test
    fun enPassantSquareIsKeptOnlyWhenAPawnJustAdvanced() {
        val afterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        assertEquals(afterE4, Position.fromFen(afterE4).toFen())
        // No pawn on e4: the square is meaningless and is dropped.
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1",
            Position.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq e3 0 1").toFen(),
        )
        // Wrong rank for the side to move.
        assertNull(Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e6 0 1").enPassantSquare)
    }

    @Test
    fun shredderAndXFenCastlingAreEquivalent() {
        val xfen = Position.fromFen("bqnb1rkr/pp3ppp/3ppn2/2p5/5P2/P2P4/NPP1P1PP/BQ1BNRKR w KQkq - 2 9")
        val shredder = Position.fromFen("bqnb1rkr/pp3ppp/3ppn2/2p5/5P2/P2P4/NPP1P1PP/BQ1BNRKR w HFhf - 2 9")
        assertEquals(xfen, shredder)
        assertEquals("bqnb1rkr/pp3ppp/3ppn2/2p5/5P2/P2P4/NPP1P1PP/BQ1BNRKR w KQkq - 2 9", shredder.toFen())
        // An inner rook keeps its file letter: here only the b1 rook may castle, a1 is further out.
        val inner = Position.fromFen("4k3/8/8/8/8/8/8/RR2K3 w B - 0 1")
        assertEquals("4k3/8/8/8/8/8/8/RR2K3 w B - 0 1", inner.toFen())
        assertEquals(listOf(Square.parse("b1")), inner.castlingRookSquares(Side.WHITE))
        assertNotNull(inner.parseSan("O-O-O"))
    }
}
