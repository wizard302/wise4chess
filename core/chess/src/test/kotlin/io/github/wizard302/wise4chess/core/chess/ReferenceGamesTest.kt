package io.github.wizard302.wise4chess.core.chess

import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays games produced by python-chess (see tools/chess-fixtures/generate.py) and checks
 * that every move reads, writes and plays exactly as the reference says.
 */
class ReferenceGamesTest {
    @Test
    fun movesNotationAndPositionsMatchTheReference() {
        val stream = checkNotNull(javaClass.getResourceAsStream("/fixtures/games.tsv.gz")) { "fixture missing" }
        var games = 0
        var moves = 0
        var position = Position.START
        var chess960 = false
        GZIPInputStream(stream).bufferedReader().useLines { lines ->
            for (line in lines) {
                val fields = line.split('\t')
                if (fields[0] == "#") {
                    chess960 = fields[1] == "chess960"
                    position = Position.fromFen(fields[2])
                    assertEquals(fields[2], position.toFen())
                    games++
                    continue
                }
                val (uci, san, fenAfter, flags) = fields
                val context = "$uci ($san) in ${position.toFen()}"
                val move = position.parseUci(uci)
                assertNotNull("UCI not understood: $context", move)
                move!!
                assertEquals(context, uci, move.toUci(chess960))
                assertEquals(context, san, position.toSan(move))
                assertEquals(context, move, position.parseSan(san))

                val after = position.play(move)
                assertEquals(context, fenAfter, after.toFen())
                assertEquals(context, after, Position.fromFen(fenAfter))
                assertEquals(context, after.hash, Position.fromFen(fenAfter).hash)
                assertEquals(context, '+' in flags || '#' in flags, after.isCheck)
                assertEquals(context, '#' in flags, after.isCheckmate)
                assertEquals(context, 's' in flags, after.isStalemate)
                assertEquals(context, 'i' in flags, after.isInsufficientMaterial)
                position = after
                moves++
            }
        }
        assertTrue("fixture looks truncated: $games games, $moves moves", games > 100 && moves > 10_000)
    }
}
