package io.github.wizard302.wise4chess.data

import io.github.wizard302.wise4chess.core.chess.GameResult
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.chess.parseSan
import io.github.wizard302.wise4chess.feature.play.GameSetup
import io.github.wizard302.wise4chess.feature.play.GameViewModel
import io.github.wizard302.wise4chess.feature.play.TimeControl
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedGameTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    private fun file() = File(folder.root, "current_game.json")

    private fun store() = SavedGameStore(file(), scope)

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.sleep(10)
        }
    }

    private fun GameViewModel.play(vararg sans: String) {
        for (san in sans) onMove(state.value.position.parseSan(san)!!)
    }

    @Test
    fun gameInProgressIsWrittenAndReadBackByANewStore() {
        val store = store()
        assertNull(store.current.value)
        val game = SavedGame(startFen = "start", moves = listOf("e2e4", "e7e5"), botGame = true, botElo = 1500)
        store.save(game)
        assertEquals(game, store.current.value)
        waitFor("the file") { file().exists() && store().current.value == game }

        store.save(null)
        waitFor("the file to go") { !file().exists() }
        assertNull(store().current.value)
    }

    @Test
    fun unreadableFileIsTreatedAsNoGame() {
        file().writeText("{ not json")
        assertNull(store().current.value)
        file().writeText("""{"startFen":"x","moves":["e2e4"],"somethingNew":1}""")
        assertEquals(listOf("e2e4"), store().current.value!!.moves) // unknown fields are ignored
    }

    @Test
    fun aGameIsSavedMoveByMoveAndClearedWhenItEnds() {
        val store = store()
        val viewModel = GameViewModel(GameSetup(faceToFace = true), store = store)
        assertNull("an untouched board is not worth saving", store.current.value)

        viewModel.play("e4", "e5", "Nf3")
        viewModel.flipBoard()
        val saved = store.current.value!!
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), saved.moves)
        assertTrue(saved.faceToFace)
        assertFalse(saved.whiteAtBottom)
        assertFalse(saved.botGame)

        viewModel.takeBack()
        assertEquals(2, store.current.value!!.moves.size)
        viewModel.takeBack()
        viewModel.takeBack()
        assertNull("taken back to the start", store.current.value)

        viewModel.play("f3", "e5", "g4")
        assertEquals(3, store.current.value!!.moves.size)
        viewModel.play("Qh4#")
        assertNull("a finished game is not a game in progress", store.current.value)
    }

    @Test
    fun resigningAndStartingOverClearTheSavedGame() {
        val store = store()
        val viewModel = GameViewModel(GameSetup(), store = store)
        viewModel.play("e4")
        assertEquals(1, store.current.value!!.moves.size)
        viewModel.resign()
        assertNull(store.current.value)

        viewModel.newGame()
        viewModel.play("d4", "d5")
        assertEquals(2, store.current.value!!.moves.size)
        viewModel.newGame()
        assertNull(store.current.value)
    }

    @Test
    fun aSavedGameIsRestoredWithItsMovesOrientationAndClocks() {
        val saved = SavedGame(
            startFen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
            moves = listOf("e1g1", "e8c8", "a1a7"),
            minutes = 5,
            incrementSeconds = 3,
            whiteMillis = 200_000,
            blackMillis = 150_000,
            whiteAtBottom = false,
        )
        // No clock is started here: the setup below has none, which keeps the test off Android's timer.
        val viewModel = GameViewModel(GameSetup(startFen = saved.startFen), restored = saved)
        val state = viewModel.state.value
        assertEquals(listOf("O-O", "O-O-O", "Ra7"), state.moves)
        assertEquals(Side.BLACK, state.orientation)
        assertEquals(Side.BLACK, state.position.sideToMove)
        assertTrue(state.isLive)
        assertNull(state.end)
        // And the game simply goes on.
        viewModel.play("Kb8")
        assertEquals(4, viewModel.state.value.moves.size)
    }

    @Test
    fun aSavedGameThatNoLongerReplaysStopsAtTheLastGoodMove() {
        val saved = SavedGame(startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", moves = listOf("e2e4", "e2e4", "g1f3"))
        val state = GameViewModel(GameSetup(), restored = saved).state.value
        assertEquals(listOf("e4"), state.moves)
    }

    @Test
    fun aRestoredGameThatIsAlreadyDecidedShowsItsResult() {
        val saved = SavedGame(startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", moves = listOf("f2f3", "e7e5", "g2g4", "d8h4"))
        val state = GameViewModel(GameSetup(), restored = saved).state.value
        assertEquals(GameResult.BLACK_WINS, state.end!!.result)
    }

    @Test
    fun timeControlIsKeptInTheSavedGame() {
        val store = store()
        // Fewer than two moves, so the clock has not started ticking yet.
        val viewModel = GameViewModel(GameSetup(timeControl = TimeControl(10, 5)), store = store)
        viewModel.play("e4")
        val saved = store.current.value!!
        assertEquals(10, saved.minutes)
        assertEquals(5, saved.incrementSeconds)
        assertEquals(600_000L, saved.whiteMillis)
        assertEquals(600_000L, saved.blackMillis)
    }
}
