package io.github.wizard302.wise4chess.core.chess

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PgnTest {
    private val annotated = """
        [Event "Casual \"blitz\" game"]
        [Site "Somewhere"]
        [Date "2026.11.12"]
        [Round "1"]
        [White "Alice"]
        [Black "Bob"]
        [Result "1-0"]
        [ECO "C50"]

        {An Italian} 1. e4 e5 2. Nf3 Nc6 3. Bc4 ${'$'}14 Bc5 {The Giuoco Piano
        proper.} ( 3... Nf6 {Two Knights} 4. Ng5 d5 ( 4... Bc5!? ${'$'}13 ) 5. exd5 ) ( {Hungarian} 3... Be7 )
        4. c3! Nf6?! ; a line comment
        5. d4 exd4 6. cxd4 Bb4+ 7. Nc3 1-0
    """.trimIndent()

    private fun parseOne(pgn: String): PgnGame {
        val games = Pgn.parse(pgn)
        assertEquals(1, games.size)
        return games[0]
    }

    @Test
    fun readsTagsMovesCommentsGlyphsAndVariations() {
        val parsed = parseOne(annotated)
        assertNull(parsed.error)
        val game = parsed.game
        assertEquals("Casual \"blitz\" game", game.tags["Event"])
        assertEquals("C50", game.tags["ECO"])
        assertEquals(GameResult.WHITE_WINS, game.result)
        assertEquals("An Italian", game.root.comment)

        val main = game.mainLine()
        assertEquals(
            listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", "c3", "Nf6", "d4", "exd4", "cxd4", "Bb4+", "Nc3"),
            main.map { it.san },
        )
        val bc4 = main[4]
        assertEquals(listOf(14), bc4.nags)
        assertEquals("The Giuoco Piano proper.", main[5].comment)
        assertEquals(listOf(1), main[6].nags)
        assertEquals(listOf(6), main[7].nags)
        assertEquals("a line comment", main[7].comment)

        assertEquals(listOf("Bc5", "Nf6", "Be7"), bc4.children.map { it.san })
        val twoKnights = bc4.children[1]
        assertEquals("Two Knights", twoKnights.comment)
        assertEquals(listOf("Ng5", "d5", "exd5"), twoKnights.mainLine().map { it.san })
        val ng5 = twoKnights.mainChild!!
        assertEquals(listOf("d5", "Bc5"), ng5.children.map { it.san })
        assertEquals(listOf(5, 13), ng5.children[1].nags)
        assertEquals("Hungarian", bc4.children[2].startingComment)
        assertEquals("", bc4.children[2].comment)
    }

    @Test
    fun writtenPgnReadsBackIdentically() {
        val first = parseOne(annotated).game
        val written = Pgn.write(first)
        val second = parseOne(written).game
        assertEquals(written, Pgn.write(second))
        assertEquals(first.tags, second.tags)
        assertTrue(written.lines().all { it.length <= 80 })
        assertTrue(written.startsWith("[Event \"Casual \\\"blitz\\\" game\"]\n[Site \"Somewhere\"]\n"))
        assertTrue("3. Bc4 \$14 Bc5 {The Giuoco Piano proper.} (3... Nf6 {Two Knights} 4. Ng5 d5" in written.replace("\n", " "))
        assertTrue("(4... Bc5!? \$13) 5. exd5) ({Hungarian} 3... Be7) 4. c3! Nf6?!" in written.replace("\n", " "))
        assertTrue(written.trimEnd().endsWith("7. Nc3 1-0"))
    }

    @Test
    fun exportOfANewGameHasTheSevenTagRoster() {
        val game = Game()
        var node = game.root
        for (san in listOf("d4", "d5", "c4")) node = node.addMove(node.position.parseSan(san)!!)
        assertEquals(
            """
            [Event "?"]
            [Site "?"]
            [Date "????.??.??"]
            [Round "?"]
            [White "?"]
            [Black "?"]
            [Result "*"]

            1. d4 d5 2. c4 *

            """.trimIndent(),
            Pgn.write(game),
        )
    }

    @Test
    fun customStartPositionAndBlackToMove() {
        val pgn = """
            [SetUp "1"]
            [FEN "4k3/8/8/8/8/8/p7/4K2R b K - 3 40"]

            40... Kd7 41. Kd2 (41. O-O?? a1=Q) 41... Kc6 *
        """.trimIndent()
        val parsed = parseOne(pgn)
        assertNull(parsed.error)
        val game = parsed.game
        assertEquals("4k3/8/8/8/8/8/p7/4K2R b K - 3 40", game.startPosition.toFen())
        assertEquals(listOf("Kd7", "Kd2", "Kc6"), game.mainLine().map { it.san })
        val written = Pgn.write(game)
        assertTrue("[SetUp \"1\"]\n[FEN \"4k3/8/8/8/8/8/p7/4K2R b K - 3 40\"]\n" in written)
        assertTrue("40... Kd7 41. Kd2 (41. O-O?? a1=Q) 41... Kc6 *" in written)
        assertEquals(written, Pgn.write(parseOne(written).game))
    }

    @Test
    fun chess960GameKeepsItsVariantAndCastling() {
        // Kings on the b-file: castling moves them several squares, or past their own rook.
        val start = Position.fromFen("rk2r3/pppppppp/8/8/8/8/PPPPPPPP/RK2R3 w KQkq - 0 1")
        val game = Game(start, chess960 = true)
        var node = game.root
        for (san in listOf("O-O", "O-O-O")) node = node.addMove(node.position.parseSan(san)!!)
        assertEquals("2krr3/pppppppp/8/8/8/8/PPPPPPPP/R4RK1 w - - 2 2", node.position.toFen())
        val written = Pgn.write(game)
        assertTrue("[Variant \"Chess960\"]" in written)
        val back = parseOne(written).game
        assertTrue(back.chess960)
        assertEquals(start, back.startPosition)
        assertEquals(listOf("O-O", "O-O-O"), back.mainLine().map { it.san })
        assertEquals("b8a8", back.lastNode.move!!.toUci(chess960 = true))
        assertEquals("b8c8", back.lastNode.move!!.toUci())
    }

    @Test
    fun readsSeveralGamesAndSloppyFormatting() {
        val pgn = "﻿" + """
            [White "A"]
            [Black "B"]
            [Result "0-1"]

            1.e4 e5 2.Qh5?? Nc6 3.Bc4 g6 4.Qf3 Nf6 5.g4 Nd4 0-1 {resigns}

            % an escape line the parser must skip
            [White "C"]
            [Black "D"]

            1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Be7 5. e3 0-0 6. Nf3 1/2-1/2

            1. e4 c5 *
        """.trimIndent()
        val games = Pgn.parse(pgn)
        assertEquals(3, games.size)
        assertTrue(games.all { it.error == null })
        assertEquals(10, games[0].game.mainLine().size)
        assertEquals(listOf(4), games[0].game.mainLine()[2].nags)
        assertEquals("C", games[1].game.tags["White"])
        assertEquals("O-O", games[1].game.mainLine()[9].san)
        assertEquals(GameResult.DRAW, games[1].game.result)
        assertEquals("1/2-1/2", games[1].game.tags["Result"])
        // A game without tags still records the result its movetext ended with.
        assertEquals(mapOf("Result" to "*"), games[2].game.tags)
        assertEquals(listOf("e4", "c5"), games[2].game.mainLine().map { it.san })
    }

    @Test
    fun illegalMoveStopsTheGameButKeepsWhatWasRead() {
        val pgn = """
            [White "A"]

            1. e4 e5 2. Nf3 Nc6 3. Bb5 Ke7 4. Qxe5 Nf6 (4... d6) 5. O-O 1-0

            [White "Next"]

            1. c4 *
        """.trimIndent()
        val games = Pgn.parse(pgn)
        assertEquals(2, games.size)
        assertEquals("Illegal or unreadable move: 4. Qxe5", games[0].error)
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "Ke7"), games[0].game.mainLine().map { it.san })
        assertEquals(GameResult.WHITE_WINS, games[0].game.result)
        assertNull(games[1].error)
        assertEquals("Next", games[1].game.tags["White"])
        assertEquals(listOf("c4"), games[1].game.mainLine().map { it.san })
    }

    @Test
    fun invalidFenTagIsReportedAndTheNextGameStillReads() {
        val games = Pgn.parse("[FEN \"garbage\"]\n\n1. e4 *\n\n[White \"Ok\"]\n\n1. d4 *\n")
        assertEquals(2, games.size)
        assertNotNull(games[0].error)
        assertTrue(games[0].game.mainLine().isEmpty())
        assertNull(games[1].error)
        assertEquals(1, games[1].game.mainLine().size)
    }

    @Test
    fun tagValuesWithRawQuotesAndBackslashesAreRead() {
        // Many programs do not escape tag values; the last quote before the bracket ends the value.
        val pgn = listOf(
            """[White "Player "Zero""]""",
            """[Black "say \"hi\""]""",
            """[Site "C:\games\"]""",
            """[Event "a\\b"]""",
            """[Round "3"] [Annotator "Two on a line"]""",
            """[Broken "no closing bracket""",
            """[Empty ""]""",
            "",
            "1. e4 *",
        ).joinToString("\n")
        val parsed = parseOne(pgn)
        assertNull(parsed.error)
        val tags = parsed.game.tags
        assertEquals("Player \"Zero\"", tags["White"])
        assertEquals("say \"hi\"", tags["Black"])
        assertEquals("C:\\games\\", tags["Site"])
        assertEquals("a\\b", tags["Event"])
        assertEquals("3", tags["Round"])
        assertEquals("Two on a line", tags["Annotator"])
        assertEquals("no closing bracket", tags["Broken"])
        assertEquals("", tags["Empty"])
        assertEquals(listOf("e4"), parsed.game.mainLine().map { it.san })
        // What we write is escaped properly and reads back to the same values.
        val back = parseOne(Pgn.write(parsed.game)).game.tags
        for ((name, value) in tags) assertEquals(name, value, back[name])
    }

    @Test
    fun gamesAreReadLazily() {
        val reader = StringReader("1. e4 *\n\n1. d4 *\n\n1. c4 *\n")
        val first = Pgn.games(reader).first()
        assertEquals("e4", first.game.mainLine().single().san)
        assertFalse(Pgn.parse("").iterator().hasNext())
        assertTrue(Pgn.parse("   \n\n ").isEmpty())
    }

    @Test
    fun longGameIsWrappedAndSurvivesARoundTrip() {
        val game = Game()
        var node = game.root
        repeat(60) {
            for (san in listOf("Nf3", "Nf6", "Ng1", "Ng8")) node = node.addMove(node.position.parseSan(san)!!)
        }
        node.comment = "a comment with a } brace and\nnew lines that is long enough to need wrapping across the eighty column limit"
        val written = Pgn.write(game)
        assertTrue(written.lines().all { it.length <= 80 })
        val back = parseOne(written).game
        assertEquals(240, back.mainLine().size)
        assertTrue(back.lastNode.comment.startsWith("a comment with a ) brace and new lines"))
    }
}
