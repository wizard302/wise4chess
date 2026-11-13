package io.github.wizard302.wise4chess.core.engine

import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.chess.parseSan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UciEngineTest {
    private val infoLines = listOf(
        "info string NNUE evaluation using nn-test.nnue",
        "info depth 1 seldepth 2 multipv 1 score cp 18 nodes 20 nps 20000 hashfull 0 tbhits 0 time 1 pv e2e4",
        "info depth 10 currmove d2d4 currmovenumber 2",
        "info depth 10 seldepth 14 multipv 1 score cp 31 lowerbound nodes 9000 nps 900000 time 10 pv e2e4 e7e5 g1f3",
        "info depth 10 seldepth 14 multipv 2 score mate -3 wdl 0 0 1000 nodes 9500 nps 900000 time 11 pv d2d4 d7d5 zzzz",
    )

    private fun <T> test(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

    @Test
    fun handshakeReadsIdentityAndOptions() = test {
        val fake = FakeEngine()
        val engine = UciEngine(fake)
        engine.start()
        assertEquals("Fake 1.0", engine.name)
        assertEquals("Test", engine.author)
        assertEquals(1320..3190, engine.eloRange)
        assertTrue(engine.supportsChess960)
        assertEquals(UciOption("Threads", "spin", "1", 1, 1024, emptyList()), engine.options["threads"])
        assertEquals(listOf("uci", "setoption name UCI_Chess960 value true", "isready"), fake.received.toList())
        engine.close()
    }

    @Test
    fun searchReportsLinesAndTheBestMove() = test {
        val fake = FakeEngine(searchOutput = infoLines)
        val engine = UciEngine(fake)
        engine.start()
        val updates = engine.search(EnginePosition(Position.START), SearchLimits(depth = 10), multiPv = 2).toList()

        val lines = updates.filterIsInstance<SearchUpdate.Info>().map { it.line }
        assertEquals(3, lines.size)
        assertEquals(Score.Centipawns(18), lines[0].score)
        assertEquals(Bound.LOWER, lines[1].bound)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), lines[1].pv.map { it.toUci() })
        assertEquals(900_000L, lines[1].nodesPerSecond)
        assertEquals(14, lines[1].selectiveDepth)
        // A variation is cut at the first move that makes no sense.
        assertEquals(2, lines[2].multiPv)
        assertEquals(Score.Mate(-3), lines[2].score)
        assertEquals(listOf("d2d4", "d7d5"), lines[2].pv.map { it.toUci() })
        assertEquals(9500L, lines[2].nodes)

        val best = updates.last() as SearchUpdate.BestMove
        assertEquals("e2e4", best.move!!.toUci())
        assertEquals("e7e5", best.ponder!!.toUci())
        assertEquals(
            listOf("setoption name MultiPV value 2", "position startpos", "go depth 10"),
            fake.received.takeLast(3),
        )
        engine.close()
    }

    @Test
    fun positionIsSentWithItsMovesAndChess960Castling() = test {
        val fake = FakeEngine(bestMove = "bestmove (none)")
        val engine = UciEngine(fake)
        engine.start()
        val start = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val castle = start.parseSan("O-O")!!
        val best = engine.search(EnginePosition(start, listOf(castle)), SearchLimits(moveTimeMillis = 50)).toList().last()
        assertNull((best as SearchUpdate.BestMove).move)
        // The engine understands Chess960, so castling goes out as king-takes-rook.
        assertEquals("position fen r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1 moves e1h1", fake.received[fake.received.size - 2])
        assertEquals("go movetime 50", fake.received.last())
        engine.close()
    }

    @Test
    fun clockLimitsAndUnchangedOptionsAreSentOnce() = test {
        val fake = FakeEngine(options = listOf("option name MultiPV type spin default 1 min 1 max 256"))
        val engine = UciEngine(fake)
        engine.start()
        assertFalse(engine.supportsChess960)
        assertNull(engine.eloRange)
        val limits = SearchLimits(whiteTimeMillis = 60_000, blackTimeMillis = 0, whiteIncrementMillis = 1000)
        repeat(2) { engine.search(EnginePosition(Position.START), limits, multiPv = 3).toList() }
        engine.setStrength(1500) // no such option: ignored
        engine.setOption("NoSuchOption", "1")
        assertEquals(
            listOf(
                "uci", "isready",
                "setoption name MultiPV value 3", "position startpos", "go wtime 60000 btime 1 winc 1000 binc 0",
                "position startpos", "go wtime 60000 btime 1 winc 1000 binc 0",
            ),
            fake.received.toList(),
        )
        engine.close()
    }

    @Test
    fun strengthIsClampedAndCanBeLifted() = test {
        val fake = FakeEngine()
        val engine = UciEngine(fake)
        engine.start()
        engine.setStrength(800)
        engine.setStrength(null)
        engine.newGame()
        assertEquals(
            listOf(
                "setoption name UCI_Elo value 1320", "setoption name UCI_LimitStrength value true",
                "setoption name UCI_LimitStrength value false", "ucinewgame", "isready",
            ),
            fake.received.takeLast(5),
        )
        engine.close()
    }

    @Test
    fun abandoningAnInfiniteSearchStopsTheEngineBeforeTheNextCommand() = test {
        val fake = FakeEngine(searchOutput = infoLines)
        val engine = UciEngine(fake)
        engine.start()
        val first = engine.search(EnginePosition(Position.START), SearchLimits.INFINITE).first()
        assertTrue(first is SearchUpdate.Info)
        // The next search must not see the leftovers of the first one.
        val second = engine.search(EnginePosition(Position.START), SearchLimits(depth = 1)).toList()
        assertEquals(3, second.count { it is SearchUpdate.Info })
        assertEquals(1, second.count { it is SearchUpdate.BestMove })
        val commands = fake.received.toList()
        assertTrue(commands.indexOf("stop") in commands.indexOf("go infinite")..commands.lastIndexOf("go depth 1"))
        engine.close()
    }

    @Test
    fun aDeadEngineIsReportedNotHungOn() = test {
        val fake = FakeEngine()
        val engine = UciEngine(fake)
        engine.start()
        fake.crash()
        try {
            engine.search(EnginePosition(Position.START), SearchLimits.INFINITE).toList()
            fail("expected EngineException")
        } catch (e: EngineException) {
            assertTrue(e.message!!.contains("stopped"))
        }
        engine.close()
    }

    @Test
    fun aSilentEngineFailsTheHandshake() = test {
        val engine = UciEngine(FakeEngine(answersHandshake = false))
        try {
            engine.start(timeoutMillis = 200)
            fail("expected EngineException")
        } catch (e: EngineException) {
            assertTrue(e.message!!.contains("handshake"))
        }
        engine.close()
    }

    @Test
    fun optionsWithSpacesAndChoicesAreParsed() {
        assertEquals(
            UciOption("Skill Level", "spin", "20", 0, 20, emptyList()),
            UciParser.parseOption("option name Skill Level type spin default 20 min 0 max 20"),
        )
        assertEquals(
            UciOption("Style", "combo", "Normal", null, null, listOf("Solid", "Normal", "Risky")),
            UciParser.parseOption("option name Style type combo default Normal var Solid var Normal var Risky"),
        )
        assertEquals(
            UciOption("SyzygyPath", "string", "", null, null, emptyList()),
            UciParser.parseOption("option name SyzygyPath type string default <empty>"),
        )
        assertEquals(
            UciOption("Clear Hash", "button", null, null, null, emptyList()),
            UciParser.parseOption("option name Clear Hash type button"),
        )
        assertNull(UciParser.parseOption("id name Something"))
    }

    @Test
    fun scoreCanBeTurnedToWhitesSide() {
        assertEquals(Score.Centipawns(-40), Score.Centipawns(40).forWhite(Side.BLACK))
        assertEquals(Score.Centipawns(40), Score.Centipawns(40).forWhite(Side.WHITE))
        assertEquals(Score.Mate(2), Score.Mate(-2).forWhite(Side.BLACK))
    }
}
