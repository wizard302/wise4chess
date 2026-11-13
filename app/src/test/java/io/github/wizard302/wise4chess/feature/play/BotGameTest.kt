package io.github.wizard302.wise4chess.feature.play

import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.wizard302.wise4chess.core.chess.GameResult
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.chess.parseSan
import androidx.compose.runtime.CompositionLocalProvider
import io.github.wizard302.wise4chess.core.engine.Score
import io.github.wizard302.wise4chess.core.engine.UciEngine
import io.github.wizard302.wise4chess.settings.AppSettings
import io.github.wizard302.wise4chess.settings.LocalAppSettings
import io.github.wizard302.wise4chess.core.ui.theme.Wise4ChessTheme
import io.github.wizard302.wise4chess.engines.cuckoo.CuckooChess
import io.github.wizard302.wise4chess.ui.Wise4ChessApp
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Games against the computer, with a scripted engine in place of Stockfish. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class BotGameTest {
    @get:Rule
    val compose = createComposeRule()

    private fun botGame(humanSide: Side, elo: Int? = 1500, transport: ScriptedEngine = ScriptedEngine()): GameViewModel {
        val viewModel = GameViewModel(GameSetup(bot = BotSetup(humanSide, elo)), launchEngine = { UciEngine(transport) })
        compose.setContent { Wise4ChessTheme { GameScreen(viewModel, onClose = {}) } }
        return viewModel
    }

    /**
     * Waits for [condition] while letting the main thread run. Robolectric's clock stands still
     * unless it is pushed, and the view model's pause before a reply waits on that clock.
     */
    private fun await(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            condition()
        }
    }

    private fun GameViewModel.awaitMoves(count: Int) = await { state.value.moves.size == count && !state.value.botThinking }

    private fun GameViewModel.play(san: String) = onMove(state.value.position.parseSan(san)!!)

    @Test
    fun theComputerAnswersEachMoveAtTheChosenStrength() {
        val transport = ScriptedEngine()
        val viewModel = botGame(Side.WHITE, elo = 1500, transport = transport)
        assertTrue(viewModel.state.value.canMove)
        assertFalse(viewModel.state.value.botThinking)

        viewModel.play("e4")
        assertFalse("the person cannot move while the computer thinks", viewModel.state.value.canMove)
        viewModel.awaitMoves(2)
        // The scripted engine plays the alphabetically first move: a7a5.
        assertEquals(listOf("e4", "a5"), viewModel.state.value.moves)
        assertTrue(viewModel.state.value.canMove)
        assertEquals("Scripted 1", viewModel.state.value.botName)
        assertTrue("setoption name UCI_Elo value 1500" in transport.received)
        assertTrue("setoption name UCI_LimitStrength value true" in transport.received)
        assertTrue("position startpos moves e2e4" in transport.received)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/bot_game.png")

        // A move made for the computer's side is ignored.
        viewModel.play("d4")
        viewModel.awaitMoves(4)
        assertEquals(listOf("e4", "a5", "d4", "a4"), viewModel.state.value.moves)

        // Taking back returns to the person's previous turn: two half-moves.
        viewModel.takeBack()
        assertEquals(listOf("e4", "a5"), viewModel.state.value.moves)
        assertTrue(viewModel.state.value.canMove)
    }

    @Test
    fun hintShowsTheEnginesTopMovesAtFullStrengthAndClearsOnTheNextMove() {
        val transport = ScriptedEngine()
        val viewModel = botGame(Side.WHITE, elo = 1400, transport = transport)
        viewModel.requestHint()
        await { viewModel.state.value.hints.size == 3 }
        // The scripted engine lists moves alphabetically: a2a3, a2a4, b1a3.
        assertEquals(listOf("a3", "a4", "Na3"), viewModel.state.value.hints.map { it.san })
        assertEquals(listOf("+0.3", "+0.2", "+0.1"), viewModel.state.value.hints.map { formatScore(it.score) })
        assertTrue("hints come from the engine at full strength", "setoption name UCI_LimitStrength value false" in transport.received)
        compose.waitForIdle()
        compose.onNodeWithText("a3 +0.3    a4 +0.2    Na3 +0.1").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/bot_game_hint.png")

        viewModel.play("e4")
        assertTrue(viewModel.state.value.hints.isEmpty())
        viewModel.awaitMoves(2)
        // Back on the person's move the engine plays at its limited strength again.
        assertEquals("setoption name UCI_LimitStrength value true", transport.received.last { it.startsWith("setoption name UCI_LimitStrength") })
        assertTrue(viewModel.state.value.hints.isEmpty())
    }

    @Test
    fun evaluationBarFollowsTheGameWhenSwitchedOn() {
        val viewModel = GameViewModel(GameSetup(bot = BotSetup(Side.BLACK, 1500)), launchEngine = { UciEngine(ScriptedEngine()) })
        compose.setContent {
            Wise4ChessTheme {
                CompositionLocalProvider(LocalAppSettings provides AppSettings(showEvaluation = true)) {
                    GameScreen(viewModel, onClose = {})
                }
            }
        }
        viewModel.awaitMoves(1)
        await { viewModel.state.value.evaluation != null }
        // Black is to move and the engine says +0.3 for the side to move: -0.3 for White.
        assertEquals(Score.Centipawns(-30), viewModel.state.value.evaluation)
        assertTrue(viewModel.state.value.evaluationShown)
        assertTrue(viewModel.state.value.hints.isEmpty())
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/bot_game_evaluation.png")
    }

    @Test
    fun scoresAreFormattedFromWhitesSide() {
        assertEquals("+0.3", formatScore(Score.Centipawns(34)))
        assertEquals("-1.5", formatScore(Score.Centipawns(-150)))
        assertEquals("+0.0", formatScore(Score.Centipawns(0)))
        assertEquals("#3", formatScore(Score.Mate(3)))
        assertEquals("#-2", formatScore(Score.Mate(-2)))
    }

    @Test
    fun theComputerOpensWhenThePersonPlaysBlack() {
        val viewModel = botGame(Side.BLACK, elo = null)
        assertEquals(Side.BLACK, viewModel.state.value.orientation)
        viewModel.awaitMoves(1)
        assertEquals(listOf("a3"), viewModel.state.value.moves) // a2a3 sorts first
        // The computer's opening move cannot be taken back.
        assertFalse(viewModel.state.value.canTakeBack)
        viewModel.play("e5")
        viewModel.awaitMoves(3)
        assertTrue(viewModel.state.value.canTakeBack)
        viewModel.takeBack()
        assertEquals(listOf("a3"), viewModel.state.value.moves)

        viewModel.newGame()
        viewModel.awaitMoves(1)
        assertEquals(listOf("a3"), viewModel.state.value.moves)
    }

    @Test
    fun resigningAgainstTheComputerIsAlwaysThePersonsLoss() {
        val viewModel = botGame(Side.BLACK)
        viewModel.awaitMoves(1)
        viewModel.play("e5") // now the computer is to move, yet it is the person who resigns
        viewModel.resign()
        assertEquals(GameEnd(GameResult.WHITE_WINS, EndReason.RESIGNATION), viewModel.state.value.end)
        compose.waitForIdle()
        compose.onNodeWithText("You lost").assertExists()
        compose.onNodeWithText("You resigned.").assertExists()
        // Offering a draw does nothing against the computer.
        viewModel.agreeDraw()
        assertEquals(EndReason.RESIGNATION, viewModel.state.value.end!!.reason)
    }

    @Test
    fun aDeadEngineIsReportedInsteadOfHangingTheGame() {
        val viewModel = botGame(Side.WHITE, transport = ScriptedEngine(failAfterMoves = 1))
        viewModel.play("e4")
        viewModel.awaitMoves(2)
        viewModel.play("d4")
        await { viewModel.state.value.engineFailed }
        assertNull(viewModel.state.value.end)
        compose.waitForIdle()
        compose.onNodeWithText("The engine stopped").assertExists()
    }

    @Test
    fun setupSheetOpensFromThePlaySection() {
        compose.setContent { Wise4ChessTheme { Wise4ChessApp() } }
        compose.onNodeWithText("Play the computer").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Elo 1500").assertExists()
        compose.onNodeWithText("Random").assertExists()
        // A strength that the other engine can also play at is kept when switching.
        compose.onNodeWithText("CuckooChess").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Elo 1500").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/bot_setup_sheet.png")
    }

    @Test
    fun cuckooChessPlaysWhileADifferentEngineGivesTheHints() {
        val adviser = ScriptedEngine()
        val viewModel = GameViewModel(
            GameSetup(bot = BotSetup(Side.WHITE, 400, BotEngine.CUCKOO)),
            launchEngine = CuckooChess::launch,
            launchAnalysisEngine = { UciEngine(adviser) },
        )
        compose.setContent { Wise4ChessTheme { GameScreen(viewModel, onClose = {}) } }
        viewModel.play("e4")
        viewModel.awaitMoves(2)
        assertTrue(viewModel.state.value.botName.startsWith("CuckooChess"))
        assertTrue(viewModel.state.value.canMove)
        assertTrue("the adviser is not asked to play", adviser.received.none { it.startsWith("go") })

        viewModel.requestHint()
        await { viewModel.state.value.hints.size == 3 }
        assertTrue(adviser.received.any { it.startsWith("go") })
        viewModel.play(viewModel.state.value.hints.first().san)
        viewModel.awaitMoves(4)
        assertNull(viewModel.state.value.end)
    }

    @Test
    fun losingTheAdviserCostsTheHintsButNotTheGame() {
        val viewModel = GameViewModel(
            GameSetup(bot = BotSetup(Side.WHITE, 1500)),
            launchEngine = { UciEngine(ScriptedEngine()) },
            launchAnalysisEngine = { UciEngine(ScriptedEngine(failAfterMoves = 0)) },
        )
        compose.setContent { Wise4ChessTheme { GameScreen(viewModel, onClose = {}) } }
        viewModel.requestHint()
        await { !viewModel.state.value.hintPending }
        assertTrue(viewModel.state.value.hints.isEmpty())
        assertFalse(viewModel.state.value.engineFailed)
        viewModel.play("e4")
        viewModel.awaitMoves(2)
        assertEquals(listOf("e4", "a5"), viewModel.state.value.moves)
    }
}
