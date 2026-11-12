package io.github.wizard302.wise4chess.core.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    private fun GameNode.play(vararg sans: String): GameNode {
        var node = this
        for (san in sans) {
            node = node.addMove(checkNotNull(node.position.parseSan(san)) { "illegal $san in ${node.position}" })
        }
        return node
    }

    @Test
    fun mainLineAndVariations() {
        val game = Game()
        val e5 = game.root.play("e4", "e5")
        val nf3 = e5.play("Nf3")
        val c5 = game.root.mainChild!!.play("c5") // 1... c5 as a variation
        val bc4 = e5.play("Bc4")

        assertEquals(listOf("e4", "e5", "Nf3"), game.mainLine().map { it.san })
        assertSame(nf3, game.lastNode)
        assertEquals(3, nf3.ply)
        assertTrue(nf3.isMainLine)
        assertFalse(c5.isMainLine)
        assertFalse(c5.play("Nf3").isMainLine)
        assertEquals(listOf("e4", "c5"), c5.pathFromRoot().map { it.san })
        assertTrue(game.root.isRoot)
        assertNull(game.root.san)

        // Adding an existing move returns the existing node instead of duplicating it.
        assertSame(e5, game.root.mainChild!!.play("e5"))
        assertEquals(2, game.root.mainChild!!.children.size)

        bc4.promoteToMain()
        assertEquals(listOf("e4", "e5", "Bc4"), game.mainLine().map { it.san })
        assertFalse(nf3.isMainLine)

        bc4.remove()
        assertEquals(listOf("e4", "e5", "Nf3"), game.mainLine().map { it.san })
    }

    @Test(expected = IllegalArgumentException::class)
    fun illegalMoveIsRejected() {
        val afterE4 = Position.START.play(Position.START.parseSan("e4")!!)
        // A move generated for another position is not legal here.
        afterE4.play(Position.START.parseSan("e4")!!)
    }

    @Test
    fun threefoldAndFivefoldRepetition() {
        val game = Game()
        var node = game.root
        assertEquals(1, node.repetitionCount())
        node = node.play("Nf3", "Nf6", "Ng1", "Ng8")
        assertEquals(2, node.repetitionCount())
        assertFalse(node.canClaimDraw)
        node = node.play("Nf3", "Nf6", "Ng1", "Ng8")
        assertEquals(3, node.repetitionCount())
        assertTrue(node.canClaimDraw)
        assertNull(node.outcome)
        node = node.play("Nf3", "Nf6", "Ng1", "Ng8", "Nf3", "Nf6", "Ng1", "Ng8")
        assertEquals(5, node.repetitionCount())
        assertEquals(Outcome(Termination.FIVEFOLD_REPETITION, null), node.outcome)
    }

    @Test
    fun losingCastlingRightsMakesAPositionDifferent() {
        // The pieces return to the same squares, but White can no longer castle.
        val game = Game(Position.fromFen("4k3/8/8/8/8/8/8/4K2R w K - 0 1"))
        val node = game.root.play("Rh2", "Kd8", "Rh1", "Ke8")
        assertEquals(1, node.repetitionCount())
        assertEquals(2, node.play("Rh2", "Kd8", "Rh1", "Ke8").repetitionCount())
    }

    @Test
    fun enPassantRightMakesAPositionDifferentOnlyWhenTheCaptureIsLegal() {
        // After 1. e4 the e3 square is recorded, but no black pawn can take there.
        val afterE4 = Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
        val same = Position.fromFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 5 9")
        assertTrue(afterE4.isSamePosition(same))
        assertFalse(afterE4 == same)
        // Here the capture is possible, so the positions differ.
        val canTake = Position.fromFen("4k3/8/8/8/3pP3/8/8/4K3 b - e3 0 1")
        val cannotTake = Position.fromFen("4k3/8/8/8/3pP3/8/8/4K3 b - - 0 1")
        assertFalse(canTake.isSamePosition(cannotTake))
    }

    @Test
    fun outcomes() {
        assertNull(Position.START.outcome)
        assertEquals(
            Outcome(Termination.CHECKMATE, Side.BLACK),
            Position.fromFen("rnb1kbnr/pppp1ppp/8/4p3/5PPq/8/PPPPP2P/RNBQKBNR w KQkq - 1 3").outcome,
        )
        assertEquals(
            Outcome(Termination.STALEMATE, null),
            Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").outcome,
        )
        assertEquals(
            Outcome(Termination.INSUFFICIENT_MATERIAL, null),
            Position.fromFen("4k3/8/8/8/8/8/8/4KB2 w - - 0 1").outcome,
        )
        assertEquals(
            Outcome(Termination.SEVENTY_FIVE_MOVES, null),
            Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 150 90").outcome,
        )
        val fiftyMoves = Game(Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 100 60"))
        assertTrue(fiftyMoves.root.canClaimDraw)
        assertNull(fiftyMoves.root.outcome)
        assertEquals(GameResult.BLACK_WINS, GameResult.of(Outcome(Termination.CHECKMATE, Side.BLACK)))
        assertEquals(GameResult.DRAW, GameResult.of(Outcome(Termination.STALEMATE, null)))
    }

    @Test
    fun insufficientMaterial() {
        fun insufficient(fen: String) = Position.fromFen(fen).isInsufficientMaterial
        assertTrue(insufficient("4k3/8/8/8/8/8/8/4K3 w - - 0 1"))
        assertTrue(insufficient("4k3/8/8/8/8/8/8/4KN2 w - - 0 1"))
        assertTrue(insufficient("4kb2/8/8/8/8/8/8/4K3 w - - 0 1"))
        // Bishops on the same colour, however many, cannot mate.
        assertTrue(insufficient("4kb2/8/8/8/8/8/8/2B1K3 w - - 0 1"))
        assertFalse(insufficient("4kb2/8/8/8/8/8/8/4KB2 w - - 0 1"))
        assertFalse(insufficient("4k3/8/8/8/8/8/8/3NKN2 w - - 0 1"))
        assertFalse(insufficient("4k3/8/8/8/8/8/8/3BKN2 w - - 0 1"))
        assertFalse(insufficient("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"))
        assertFalse(insufficient("4k3/8/8/8/8/8/8/R3K3 w - - 0 1"))
    }

    @Test
    fun resultIsStoredInTheTags() {
        val game = Game()
        assertEquals(GameResult.UNKNOWN, game.result)
        game.result = GameResult.DRAW
        assertEquals("1/2-1/2", game.tags["Result"])
        assertEquals(GameResult.DRAW, game.result)
    }
}
