package io.github.wizard302.wise4chess.feature.editor

import io.github.wizard302.wise4chess.core.chess.Piece
import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorViewModelTest {
    private fun sq(name: String) = Square.parse(name)!!.index

    @Test
    fun startsFromTheStartingPosition() {
        val state = EditorViewModel().state.value
        assertEquals(Position.START_FEN, state.fen)
        assertNotNull(state.position)
        assertNull(state.problem)
        assertEquals(CastlingRight.entries.toSet(), state.availableCastling)
    }

    @Test
    fun moveToolPicksUpAndPutsDown() {
        val vm = EditorViewModel()
        vm.onSquare(sq("e2"), dragged = false)
        assertEquals(sq("e2"), vm.state.value.selectedSquare)
        vm.onSquare(sq("e4"), dragged = false)
        val state = vm.state.value
        assertEquals(-1, state.selectedSquare)
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1", state.fen)
        // An empty square cannot be picked up.
        vm.onSquare(sq("e5"), dragged = false)
        assertEquals(-1, vm.state.value.selectedSquare)
    }

    @Test
    fun placeAndEraseTools() {
        val vm = EditorViewModel()
        vm.clear()
        assertEquals("8/8/8/8/8/8/8/8 w - - 0 1", vm.state.value.fen)
        assertEquals(EditorProblem.KINGS, vm.state.value.problem)

        vm.selectTool(EditorTool.Place(Piece.WHITE_KING))
        vm.onSquare(sq("e1"), dragged = false)
        vm.selectTool(EditorTool.Place(Piece.BLACK_KING))
        vm.onSquare(sq("e8"), dragged = false)
        assertNull(vm.state.value.problem)

        // Dragging paints; a tap on the same piece takes it off again.
        vm.selectTool(EditorTool.Place(Piece.WHITE_PAWN))
        for (name in listOf("a2", "b2", "c2")) vm.onSquare(sq(name), dragged = true)
        vm.onSquare(sq("c2"), dragged = true)
        assertEquals("4k3/8/8/8/8/8/PPP5/4K3 w - - 0 1", vm.state.value.fen)
        vm.onSquare(sq("c2"), dragged = false)
        assertEquals("4k3/8/8/8/8/8/PP6/4K3 w - - 0 1", vm.state.value.fen)

        vm.selectTool(EditorTool.Erase)
        vm.onSquare(sq("a2"), dragged = false)
        vm.onSquare(sq("b2"), dragged = true)
        assertEquals("4k3/8/8/8/8/8/8/4K3 w - - 0 1", vm.state.value.fen)
    }

    @Test
    fun problemsAreNamed() {
        val vm = EditorViewModel()
        vm.selectTool(EditorTool.Place(Piece.WHITE_PAWN))
        vm.onSquare(sq("a8"), dragged = false)
        assertEquals(EditorProblem.PAWN_ON_BACK_RANK, vm.state.value.problem)
        assertNull(vm.state.value.position)

        assertTrue(vm.loadFen("4k3/8/8/8/8/8/8/4K2R w - - 0 1"))
        vm.selectTool(EditorTool.Move)
        vm.onSquare(sq("h1"), dragged = false)
        vm.onSquare(sq("e2"), dragged = false) // rook now attacks the black king, and White is to move
        assertEquals(EditorProblem.OPPONENT_IN_CHECK, vm.state.value.problem)
        vm.setSideToMove(Side.BLACK)
        assertNull(vm.state.value.problem)
    }

    @Test
    fun castlingRightsFollowThePlacement() {
        val vm = EditorViewModel()
        assertTrue(vm.loadFen("r3k2r/8/8/8/8/8/8/R3K2R w Kq - 0 1"))
        var state = vm.state.value
        assertEquals(setOf(CastlingRight.WHITE_KINGSIDE, CastlingRight.BLACK_QUEENSIDE), state.castling)
        assertEquals(CastlingRight.entries.toSet(), state.availableCastling)
        vm.toggleCastling(CastlingRight.WHITE_QUEENSIDE)
        vm.toggleCastling(CastlingRight.BLACK_QUEENSIDE)
        assertEquals("r3k2r/8/8/8/8/8/8/R3K2R w KQ - 0 1", vm.state.value.fen)

        // Moving the h1 rook away makes the kingside right unavailable, whatever the switch says.
        vm.onSquare(sq("h1"), dragged = false)
        vm.onSquare(sq("h4"), dragged = false)
        state = vm.state.value
        assertFalse(CastlingRight.WHITE_KINGSIDE in state.availableCastling)
        assertEquals("r3k2r/8/8/8/7R/8/8/R3K3 w Q - 0 1", state.fen)
    }

    @Test
    fun loadingFenKeepsToolAndOrientationAndRejectsGarbage() {
        val vm = EditorViewModel()
        vm.flip()
        vm.selectTool(EditorTool.Erase)
        assertFalse(vm.loadFen("not a position"))
        assertEquals(Position.START_FEN, vm.state.value.fen)
        assertTrue(vm.loadFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 b - - 0 1"))
        val state = vm.state.value
        assertEquals("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 b - - 0 1", state.fen)
        assertEquals(Side.BLACK, state.orientation)
        assertEquals(EditorTool.Erase, state.tool)
        vm.reset()
        assertEquals(Position.START_FEN, vm.state.value.fen)
    }
}
