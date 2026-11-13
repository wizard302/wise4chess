package io.github.wizard302.wise4chess.core.engine

import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.parseSan
import io.github.wizard302.wise4chess.core.chess.toSan
import java.io.File
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs against a real engine when the environment variable WISE4CHESS_UCI_ENGINE points to an
 * executable (a Stockfish built for this machine, say); skipped otherwise.
 */
class RealEngineTest {
    private val path: String? = System.getenv("WISE4CHESS_UCI_ENGINE")

    @Test
    fun findsMateAndAnalysesSeveralLines() = runBlocking {
        assumeTrue("WISE4CHESS_UCI_ENGINE is not set", path != null && File(path).canExecute())
        withTimeout(60_000) {
            val engine = UciEngine(ProcessTransport(listOf(path!!)))
            engine.start()
            assertTrue(engine.name.isNotEmpty())

            // Scholar's mate is one move away.
            var position = Position.START
            val played = listOf("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6").map { san ->
                position.parseSan(san)!!.also { position = position.play(it) }
            }
            val updates = engine.search(EnginePosition(Position.START, played), SearchLimits(depth = 8)).toList()
            val best = updates.last() as SearchUpdate.BestMove
            assertEquals("Qxf7#", position.toSan(best.move!!))
            val last = updates.filterIsInstance<SearchUpdate.Info>().last().line
            assertEquals(Score.Mate(1), last.score)

            // An open-ended analysis with three lines, stopped from our side.
            val seen = HashSet<Int>()
            engine.search(EnginePosition(Position.START), SearchLimits.INFINITE, multiPv = 3)
                .takeWhile { update ->
                    if (update is SearchUpdate.Info) seen.add(update.line.multiPv)
                    !(update is SearchUpdate.Info && update.line.depth >= 8 && seen.size == 3)
                }
                .toList()
            assertEquals(setOf(1, 2, 3), seen)

            // The engine is idle again and limited strength works.
            engine.setStrength(1500)
            val weak = engine.search(EnginePosition(Position.START), SearchLimits(moveTimeMillis = 100)).toList()
            assertTrue((weak.last() as SearchUpdate.BestMove).move != null)
            engine.close()
        }
    }
}
