package io.github.wizard302.wise4chess.feature.play

import io.github.wizard302.wise4chess.core.chess.GameResult
import io.github.wizard302.wise4chess.core.chess.Piece
import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.chess.parseSan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Game flow without a clock (the clock needs Android's time source and a main dispatcher). */
class LocalGameViewModelTest {
    private fun LocalGameViewModel.play(vararg sans: String) {
        for (san in sans) onMove(checkNotNull(state.value.position.parseSan(san)) { "illegal $san" })
    }

    @Test
    fun movesAreRecordedAndCanBeTakenBack() {
        val vm = LocalGameViewModel(GameSetup())
        assertEquals(Position.START, vm.state.value.position)
        assertFalse(vm.state.value.canTakeBack)
        assertNull(vm.state.value.whiteMillis)

        vm.play("e4", "e5", "Nf3")
        var state = vm.state.value
        assertEquals(listOf("e4", "e5", "Nf3"), state.moves)
        assertEquals(3, state.viewPly)
        assertTrue(state.isLive)
        assertEquals("g1f3", state.lastMove!!.toUci())
        assertEquals(Side.BLACK, state.position.sideToMove)

        vm.takeBack()
        state = vm.state.value
        assertEquals(listOf("e4", "e5"), state.moves)
        assertEquals("e7e5", state.lastMove!!.toUci())
        assertTrue(state.isLive)
    }

    @Test
    fun browsingEarlierPositionsBlocksMovesUntilLiveAgain() {
        val vm = LocalGameViewModel(GameSetup())
        vm.play("d4", "d5", "c4")
        vm.stepBack()
        var state = vm.state.value
        assertEquals(2, state.viewPly)
        assertFalse(state.isLive)
        assertEquals(3, state.moves.size)

        // A move made while browsing is ignored.
        vm.onMove(state.position.parseSan("Nf3")!!)
        assertEquals(listOf("d4", "d5", "c4"), vm.state.value.moves)

        vm.goTo(0)
        assertEquals(Position.START, vm.state.value.position)
        assertNull(vm.state.value.lastMove)
        vm.stepBack() // already at the start
        assertEquals(0, vm.state.value.viewPly)
        vm.goTo(99)
        assertTrue(vm.state.value.isLive)
        vm.stepForward() // already at the end
        state = vm.state.value
        assertEquals(3, state.viewPly)
        vm.play("e6")
        assertEquals(4, vm.state.value.moves.size)
    }

    @Test
    fun checkmateEndsTheGameAndCanBeTakenBack() {
        val vm = LocalGameViewModel(GameSetup())
        vm.play("f3", "e5", "g4", "Qh4#")
        var state = vm.state.value
        assertEquals(GameEnd(GameResult.BLACK_WINS, EndReason.CHECKMATE), state.end)
        assertTrue(state.canTakeBack)
        // No further moves are accepted.
        vm.onMove(Position.START.parseSan("e4")!!)
        assertEquals(4, vm.state.value.moves.size)

        vm.takeBack()
        state = vm.state.value
        assertNull(state.end)
        assertEquals(3, state.moves.size)
    }

    @Test
    fun resignationAndAgreedDrawAreFinal() {
        val resigned = LocalGameViewModel(GameSetup())
        resigned.play("e4")
        resigned.resign() // Black is to move and gives up
        assertEquals(GameEnd(GameResult.WHITE_WINS, EndReason.RESIGNATION), resigned.state.value.end)
        assertFalse(resigned.state.value.canTakeBack)
        resigned.agreeDraw() // too late
        assertEquals(EndReason.RESIGNATION, resigned.state.value.end!!.reason)

        val drawn = LocalGameViewModel(GameSetup())
        drawn.play("e4", "e5")
        drawn.agreeDraw()
        assertEquals(GameEnd(GameResult.DRAW, EndReason.DRAW_AGREED), drawn.state.value.end)

        drawn.newGame()
        val fresh = drawn.state.value
        assertNull(fresh.end)
        assertTrue(fresh.moves.isEmpty())
        assertEquals(Position.START, fresh.position)
    }

    @Test
    fun stalemateAndFivefoldRepetitionAreDetected() {
        val stalemate = LocalGameViewModel(GameSetup(startFen = "7k/8/6K1/8/8/8/5Q2/8 w - - 0 1"))
        stalemate.play("Qf7")
        assertEquals(GameEnd(GameResult.DRAW, EndReason.STALEMATE), stalemate.state.value.end)

        val repetition = LocalGameViewModel(GameSetup())
        repeat(4) { repetition.play("Nf3", "Nf6", "Ng1", "Ng8") }
        assertEquals(GameEnd(GameResult.DRAW, EndReason.FIVEFOLD_REPETITION), repetition.state.value.end)
    }

    @Test
    fun customStartPositionSetsMoveNumbering() {
        val vm = LocalGameViewModel(GameSetup(startFen = "4k3/8/8/8/8/8/8/4K2R b K - 0 23", faceToFace = true))
        val state = vm.state.value
        assertEquals(23, state.firstMoveNumber)
        assertTrue(state.blackMovesFirst)
        assertTrue(state.faceToFace)
        assertEquals(Side.WHITE, state.orientation)
        vm.flipBoard()
        vm.setFaceToFace(false)
        assertEquals(Side.BLACK, vm.state.value.orientation)
        assertFalse(vm.state.value.faceToFace)
    }

    @Test
    fun chess960StartsFromAShuffledBackRank() {
        val state = LocalGameViewModel(GameSetup(chess960 = true)).state.value
        val backRank = (0..7).map { state.position.pieceAt(io.github.wizard302.wise4chess.core.chess.Square(it)) }
        assertEquals(1, backRank.count { it == Piece.WHITE_KING })
        assertEquals(2, backRank.count { it == Piece.WHITE_ROOK })
        // Both rooks may castle, and the pawns stand where they always do.
        assertEquals(2, state.position.castlingRookSquares(Side.WHITE).size)
        assertTrue(state.position.toFen().contains("/pppppppp/8/8/8/8/PPPPPPPP/"))
    }

    @Test
    fun materialSummaryCountsCapturesAndLead() {
        // White has taken a queen and a pawn; Black has taken a rook and two pawns.
        val summary = MaterialSummary.of(Position.fromFen("rnb1kbnr/ppppppp1/8/8/8/8/PPPPPP2/RNBQKBN1 w Qkq - 0 1"))
        assertEquals(listOf(Piece.BLACK_QUEEN, Piece.BLACK_PAWN), summary.takenByWhite)
        assertEquals(listOf(Piece.WHITE_ROOK, Piece.WHITE_PAWN, Piece.WHITE_PAWN), summary.takenByBlack)
        assertEquals(3, summary.balance)
        assertEquals(0, MaterialSummary.of(Position.START).balance)
        // In a game from a set-up position only what was there at the start can have been taken.
        val start = Position.fromFen("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")
        val later = start.play(start.parseSan("exd5")!!)
        val fromSetup = MaterialSummary.of(later, start)
        assertEquals(listOf(Piece.BLACK_PAWN), fromSetup.takenByWhite)
        assertTrue(fromSetup.takenByBlack.isEmpty())
        assertEquals(1, fromSetup.balance)
    }

    @Test
    fun clockFormatting() {
        assertEquals("5:00", formatClock(300_000))
        assertEquals("4:59", formatClock(298_001))
        assertEquals("0:10", formatClock(10_000))
        assertEquals("0:09.9", formatClock(9_999))
        assertEquals("0:00.3", formatClock(350))
        assertEquals("0:00.0", formatClock(-5))
        assertEquals("1:00:00", formatClock(3_600_000))
        assertEquals("1:30:05", formatClock(5_405_000))
        assertEquals("15+10", TimeControl(15, 10).label)
        assertEquals(900_000L, TimeControl(15, 10).initialMillis)
    }
}
