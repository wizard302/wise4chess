package io.github.wizard302.wise4chess.engines.cuckoo

import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.parseSan
import io.github.wizard302.wise4chess.core.chess.toSan
import io.github.wizard302.wise4chess.core.engine.EnginePosition
import io.github.wizard302.wise4chess.core.engine.Score
import io.github.wizard302.wise4chess.core.engine.SearchLimits
import io.github.wizard302.wise4chess.core.engine.SearchUpdate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CuckooChessTest {
    private fun <T> test(block: suspend () -> T): T = runBlocking { withTimeout(60_000) { block() } }

    private fun gameAfter(vararg sans: String): EnginePosition {
        var position = Position.START
        val moves = sans.map { san -> position.parseSan(san)!!.also { position = position.play(it) } }
        return EnginePosition(Position.START, moves)
    }

    @Test
    fun handshakeReportsNameAndStrengthRange() = test {
        val engine = CuckooChess.launch()
        engine.start()
        assertTrue(engine.name.startsWith("CuckooChess"))
        assertEquals(CuckooChess.ELO_RANGE, engine.eloRange)
        engine.close()
    }

    @Test
    fun findsMateInOne() = test {
        val engine = CuckooChess.launch()
        engine.start()
        val game = gameAfter("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6")
        val updates = engine.search(game, SearchLimits(depth = 4)).toList()
        val best = updates.last() as SearchUpdate.BestMove
        assertEquals("Qxf7#", game.current.toSan(best.move!!))
        val lines = updates.filterIsInstance<SearchUpdate.Info>()
        assertTrue(lines.isNotEmpty())
        assertEquals(Score.Mate(1), lines.last().line.score)
        engine.close()
    }

    @Test
    fun playsLegalMovesAtItsWeakestAndWithAClock() = test {
        val engine = CuckooChess.launch()
        engine.start()
        engine.setStrength(-625)
        var position = Position.START
        val moves = ArrayList<io.github.wizard302.wise4chess.core.chess.Move>()
        // The engine plays both sides of a short game; every reply must be legal.
        repeat(12) {
            val limits = SearchLimits(whiteTimeMillis = 2_000, blackTimeMillis = 2_000, whiteIncrementMillis = 50, blackIncrementMillis = 50)
            val best = engine.search(EnginePosition(Position.START, moves.toList()), limits).toList().last() as SearchUpdate.BestMove
            val move = best.move ?: return@repeat
            assertTrue(position.isLegal(move))
            moves.add(move)
            position = position.play(move)
        }
        assertEquals(12, moves.size)
        engine.close()
    }

    @Test
    fun anOpenEndedSearchCanBeAbandonedAndTheEngineUsedAgain() = test {
        val engine = CuckooChess.launch()
        engine.start()
        engine.setStrength(null)
        val first = engine.search(EnginePosition(Position.START), SearchLimits.INFINITE).first { it is SearchUpdate.Info }
        assertNotNull(first)
        val again = engine.search(gameAfter("d4"), SearchLimits(moveTimeMillis = 100)).toList()
        assertTrue((again.last() as SearchUpdate.BestMove).move != null)
        engine.newGame()
        engine.close()
    }
}
