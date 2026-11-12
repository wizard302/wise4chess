package io.github.wizard302.wise4chess.core.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SquareTest {
    @Test
    fun cornersMapToExpectedIndices() {
        assertEquals(0, Square.parse("a1")!!.index)
        assertEquals(7, Square.parse("h1")!!.index)
        assertEquals(56, Square.parse("a8")!!.index)
        assertEquals(63, Square.parse("h8")!!.index)
    }

    @Test
    fun everySquareRoundTripsThroughItsName() {
        for (index in 0..63) {
            val square = Square(index)
            assertEquals(square, Square.parse(square.toString()))
            assertEquals(square, Square.of(square.file, square.rank))
        }
    }

    @Test
    fun squareColorsFollowTheBoard() {
        assertFalse(Square.parse("a1")!!.isLight)
        assertTrue(Square.parse("h1")!!.isLight)
        assertTrue(Square.parse("e4")!!.isLight)
        assertFalse(Square.parse("d4")!!.isLight)
    }

    @Test
    fun invalidNamesAreRejected() {
        for (text in listOf("", "e", "e9", "i1", "a0", "e44", "E4")) {
            assertNull(text, Square.parse(text))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeIndexThrows() {
        Square(64)
    }
}
