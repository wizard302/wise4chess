package io.github.wizard302.wise4chess.core.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotationTest {
    private fun sanOf(fen: String, uci: String): String {
        val position = Position.fromFen(fen)
        return position.toSan(position.parseUci(uci)!!)
    }

    @Test
    fun disambiguationUsesFileThenRankThenBoth() {
        assertEquals("Nbd2", sanOf("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1", "b1d2"))
        assertEquals("R1a3", sanOf("4k3/8/8/R7/8/8/8/R3K3 w - - 0 1", "a1a3"))
        // Three queens that can all reach e1: neither file nor rank alone identifies h4.
        assertEquals("Qh4e1", sanOf("8/k7/8/8/4Q2Q/8/8/K6Q w - - 0 1", "h4e1"))
        // A pinned knight is not a rival, so no disambiguation is written.
        assertEquals("Nd2", sanOf("4k3/8/8/8/8/4r3/4N3/1N2K3 w - - 0 1", "b1d2"))
    }

    @Test
    fun specialMoves() {
        assertEquals("exd6", sanOf("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1", "e5d6"))
        assertEquals("a8=N+", sanOf("8/P1k5/8/8/8/8/8/4K3 w - - 0 1", "a7a8n"))
        assertEquals("O-O", sanOf("4k3/8/8/8/8/8/8/4K2R w K - 0 1", "e1g1"))
        assertEquals("O-O-O+", sanOf("3k4/8/8/8/8/8/8/R3K3 w Q - 0 1", "e1c1"))
        assertEquals("Qh4#", sanOf("rnbqkbnr/pppp1ppp/8/4p3/5PP1/8/PPPPP2P/RNBQKBNR b KQkq - 0 2", "d8h4"))
    }

    @Test
    fun parseSanIsLenient() {
        val position = Position.fromFen("r3k2r/pPp2ppp/8/3pP3/8/5N2/8/R3K2R w KQkq d6 0 1")
        fun uci(san: String) = position.parseSan(san)?.toUci()
        assertEquals("e1g1", uci("O-O"))
        assertEquals("e1g1", uci("0-0"))
        assertEquals("e1c1", uci("O-O-O!?"))
        assertEquals("e1c1", uci("0-0-0+"))
        assertEquals("e5d6", uci("exd6"))
        assertEquals("e5d6", uci("exd6+!"))
        assertEquals("b7a8q", uci("bxa8=Q"))
        assertEquals("b7a8q", uci("bxa8Q"))
        assertEquals("b7a8n", uci("bxa8=N+"))
        assertEquals("b7b8r", uci("b8=R"))
        assertEquals("b7b8r", uci("b8r"))
        assertEquals("f3g5", uci("Ng5"))
        assertEquals("f3g5", uci("Nf3g5"))
        assertEquals("f3g5", uci("Nf3-g5"))
        assertEquals("a1b1", uci("Rab1"))
        assertEquals("a1d1", uci("Rad1"))
        assertEquals("h1f1", uci("Rhf1"))
        assertEquals("e5e6", uci("e6"))
        assertEquals("e5e6", uci("e5e6"))
    }

    @Test
    fun parseSanRejectsWhatItCannotResolve() {
        val position = Position.fromFen("r3k2r/pPp2ppp/8/3pP3/8/5N2/8/R3K2R w KQkq d6 0 1")
        for (text in listOf("", "+", "b8", "bxa8", "Ke3", "Nf6", "e7", "Zz9", "O-O-O-O", "bxa8=K", "exd6ep")) {
            assertNull("accepted '$text'", position.parseSan(text))
        }
        // Two knights reach d2 and the text does not say which.
        val twoKnights = Position.fromFen("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1")
        assertNull(twoKnights.parseSan("Nd2"))
        assertEquals("f3d2", twoKnights.parseSan("Nfd2")!!.toUci())
    }

    @Test
    fun bishopLetterIsNotConfusedWithTheBFile() {
        val position = Position.fromFen("4k3/8/8/8/8/2n5/1P6/B3K3 w - - 0 1")
        assertEquals("b2c3", position.parseSan("bxc3")!!.toUci())
        assertNull(position.parseSan("Bxc3")) // the bishop is blocked by its own pawn
        assertEquals(PieceType.PAWN, position.pieceAt(position.parseSan("b3")!!.from)!!.type)
    }

    @Test
    fun parseUciRejectsNonsense() {
        val position = Position.START
        for (text in listOf("", "e2", "e2e5", "e2e4q", "e7e5", "i2i4", "e2e4x", "e2e4qq")) {
            assertNull("accepted '$text'", position.parseUci(text))
        }
        assertNotNull(position.parseUci("g1f3"))
    }

    @Test
    fun findMoveMatchesCastlingByKingTargetOrRookSquare() {
        val position = Position.fromFen("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        val e1 = Square.parse("e1")!!
        val byKing = position.findMove(e1, Square.parse("g1")!!)
        val byRook = position.findMove(e1, Square.parse("h1")!!)
        assertTrue(byKing!!.isKingsideCastling)
        assertEquals(byKing, byRook)
        assertTrue(position.findMove(e1, Square.parse("a1")!!)!!.isQueensideCastling)
        assertEquals(false, position.findMove(e1, Square.parse("f1")!!)!!.isCastling)
        // A promotion needs its piece spelled out.
        val promo = Position.fromFen("8/P1k5/8/8/8/8/8/4K3 w - - 0 1")
        assertNull(promo.findMove(Square.parse("a7")!!, Square.parse("a8")!!))
        assertEquals(PieceType.ROOK, promo.findMove(Square.parse("a7")!!, Square.parse("a8")!!, PieceType.ROOK)!!.promotion)
        assertEquals(4, promo.legalMovesFrom(Square.parse("a7")!!).size)
    }
}
