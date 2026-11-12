package io.github.wizard302.wise4chess.core.chess

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Move generator correctness: node counts for the standard perft positions
 * (https://www.chessprogramming.org/Perft_Results) and for Chess960 positions whose counts
 * were computed with python-chess.
 */
class PerftTest {
    private fun check(fen: String, vararg expected: Long) {
        val position = Position.fromFen(fen)
        for ((i, nodes) in expected.withIndex()) {
            assertEquals("perft(${i + 1}) of $fen", nodes, position.perft(i + 1))
        }
    }

    @Test
    fun startPosition() = check(Position.START_FEN, 20, 400, 8_902, 197_281, 4_865_609)

    @Test
    fun kiwipete() = check(
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        48, 2_039, 97_862, 4_085_603,
    )

    @Test
    fun enPassantPins() = check(
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        14, 191, 2_812, 43_238, 674_624, 11_030_083,
    )

    @Test
    fun promotionsAndCastlingUnderAttack() = check(
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        6, 264, 9_467, 422_333, 15_833_292,
    )

    @Test
    fun mirroredPromotionsAndCastling() = check(
        "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1",
        6, 264, 9_467, 422_333,
    )

    @Test
    fun position5() = check(
        "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        44, 1_486, 62_379, 2_103_487,
    )

    @Test
    fun position6() = check(
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
        46, 2_079, 89_890, 3_894_594,
    )

    @Test
    fun chess960() {
        check("bqnb1rkr/pp3ppp/3ppn2/2p5/5P2/P2P4/NPP1P1PP/BQ1BNRKR w HFhf - 2 9", 21, 528, 12_189, 326_672)
        check("2nnrbkr/p1qppppp/8/1ppb4/6PP/3PP3/PPP2P2/BQNNRBKR w HEhe - 1 9", 21, 807, 18_002, 667_366)
        check("b1q1rrkb/pppppppp/3nn3/8/P7/1PPP4/4PPPP/BQNNRKRB w GE - 1 9", 20, 479, 10_471, 273_318)
        check("qbbnnrkr/2pp2pp/p7/1p2pp2/8/P3PP2/1PPP1KPP/QBBNNR1R w hf - 0 9", 22, 593, 13_440, 382_958)
        check("1nbbnrkr/p1p1ppp1/3p4/1p3P1p/3Pq2P/8/PPP1P1P1/QNBBNRKR w HFhf - 0 9", 28, 1_120, 31_058, 1_171_749)
    }

    @Test
    fun chess960CastlingEdgeCases() {
        // King already on its castling square, inner rooks, rooks shielding the king.
        check("r1k1r2q/p1ppp1pp/8/8/8/8/P1PPP1PP/R1K1R2Q w KQkq - 0 1", 23, 522, 12_333, 285_754)
        check("1r2k1r1/8/8/8/8/8/8/1R2K1R1 w GBgb - 0 1", 25, 525, 12_297, 272_627)
        check("4k3/8/8/8/8/8/8/rR2K1Rr w GB - 0 1", 10, 194, 3_664, 80_533)
        check("5rk1/8/8/8/8/8/8/3q1RK1 w F - 0 1", 5, 152, 1_690, 50_856)
    }
}
