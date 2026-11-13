package io.github.wizard302.wise4chess.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.wizard302.wise4chess.core.chess.Square
import io.github.wizard302.wise4chess.core.chess.parseSan
import io.github.wizard302.wise4chess.core.ui.board.CHESS_BOARD_TAG
import io.github.wizard302.wise4chess.core.ui.theme.Wise4ChessTheme
import io.github.wizard302.wise4chess.feature.editor.EditorScreen
import io.github.wizard302.wise4chess.feature.play.GameScreen
import io.github.wizard302.wise4chess.data.SavedGame
import io.github.wizard302.wise4chess.feature.play.GameSetup
import io.github.wizard302.wise4chess.feature.play.PlayScreen
import io.github.wizard302.wise4chess.feature.play.GameViewModel
import io.github.wizard302.wise4chess.feature.play.TimeControl
import io.github.wizard302.wise4chess.feature.settings.SettingsScreen
import io.github.wizard302.wise4chess.settings.AppSettings
import io.github.wizard302.wise4chess.settings.LocalAppSettings
import io.github.wizard302.wise4chess.ui.Wise4ChessApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders whole screens on the JVM. Each test fails if the screen crashes while composing or
 * drawing, and leaves an image in build/outputs/roborazzi for a look at the result.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class ScreenRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(name: String, content: @Composable () -> Unit) {
        compose.setContent { Wise4ChessTheme(content = content) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun game(setup: GameSetup, vararg sans: String): GameViewModel {
        val viewModel = GameViewModel(setup)
        for (san in sans) viewModel.onMove(viewModel.state.value.position.parseSan(san)!!)
        return viewModel
    }

    @Test
    fun appStartsOnThePlaySection() = render("app") { Wise4ChessApp() }

    @Test
    @Config(qualifiers = "ru-w412dp-h915dp-420dpi")
    fun appInRussian() = render("app_ru") { Wise4ChessApp() }

    @Test
    fun gameAfterAFewMoves() {
        val viewModel = game(
            GameSetup(timeControl = TimeControl(5, 3)),
            "e4", "d5", "exd5", "Qxd5", "Nc3", "Qa5", "d4", "Nf6", "Bd2", "c6", "Nd5", "Qd8", "Nxf6+",
        )
        render("game") { GameScreen(viewModel, onClose = {}) }
    }

    @Test
    @Config(qualifiers = "ru-w412dp-h915dp-420dpi")
    fun gameFaceToFaceInRussian() {
        val viewModel = game(GameSetup(faceToFace = true), "e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Bxc6", "dxc6")
        render("game_face_to_face_ru") { GameScreen(viewModel, onClose = {}) }
    }

    @Test
    fun gameOverDialog() {
        val viewModel = game(GameSetup(), "f3", "e5", "g4", "Qh4#")
        render("game_over") { GameScreen(viewModel, onClose = {}) }
    }

    @Test
    fun editor() = render("editor") { EditorScreen(onClose = {}, onPlay = {}) }

    @Test
    @Config(qualifiers = "ru-w412dp-h915dp-420dpi")
    fun playSectionOffersToContinueASavedGame() {
        val saved = SavedGame(startFen = "start", moves = List(23) { "e2e4" }, botGame = true, botElo = 1500)
        var resumed = false
        compose.setContent {
            Wise4ChessTheme { PlayScreen(onStartGame = {}, onOpenEditor = {}, savedGame = saved, onResume = { resumed = true }) }
        }
        compose.onNodeWithText("Против компьютера · ход 12").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/play_continue_ru.png")
        compose.onNodeWithText("Продолжить партию").performClick()
        assertEquals(true, resumed)
    }

    @Test
    fun newGameSheetOpensFromThePlaySection() {
        compose.setContent { Wise4ChessTheme { Wise4ChessApp() } }
        compose.onNodeWithText("Two players").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Face to face").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/new_game_sheet.png")
    }

    /** Centre of [square] on a board of [width] pixels seen from White's side. */
    private fun at(square: String, width: Int): Offset {
        val index = Square.parse(square)!!.index
        val size = width / 8f
        return Offset(((index and 7) + 0.5f) * size, (7 - (index ushr 3) + 0.5f) * size)
    }

    private fun tap(square: String) {
        compose.onNodeWithTag(CHESS_BOARD_TAG).performTouchInput { click(at(square, width)) }
        compose.waitForIdle()
    }

    @Test
    fun movesCanBeMadeByTappingAndByDragging() {
        val viewModel = GameViewModel(GameSetup())
        compose.setContent { Wise4ChessTheme { GameScreen(viewModel, onClose = {}) } }

        tap("e2")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/game_piece_selected.png")
        tap("e4")
        assertEquals(listOf("e4"), viewModel.state.value.moves)

        // Tapping another piece of one's own changes the selection instead of moving.
        tap("b8")
        tap("g8")
        tap("f6")
        assertEquals(listOf("e4", "Nf6"), viewModel.state.value.moves)

        compose.onNodeWithTag(CHESS_BOARD_TAG).performTouchInput { swipe(at("g1", width), at("f3", width), 200) }
        compose.waitForIdle()
        assertEquals(listOf("e4", "Nf6", "Nf3"), viewModel.state.value.moves)

        // An illegal drop and a tap on an empty square change nothing.
        compose.onNodeWithTag(CHESS_BOARD_TAG).performTouchInput { swipe(at("a7", width), at("a4", width), 200) }
        tap("e5")
        assertEquals(3, viewModel.state.value.moves.size)

        // Take back with the button under the board.
        compose.onNodeWithText("Take back").performClick()
        compose.waitForIdle()
        assertEquals(listOf("e4", "Nf6"), viewModel.state.value.moves)
    }

    @Test
    fun draggedPieceIsDrawnWithoutDisturbingTheOthers() {
        val viewModel = GameViewModel(GameSetup())
        compose.setContent { Wise4ChessTheme { GameScreen(viewModel, onClose = {}) } }
        compose.onNodeWithTag(CHESS_BOARD_TAG).performTouchInput {
            down(at("d2", width))
            moveTo(at("d3", width))
            moveTo(at("d3", width) + Offset(10f, -20f))
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/game_dragging.png")
        compose.onNodeWithTag(CHESS_BOARD_TAG).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf("d3"), viewModel.state.value.moves)
    }

    @Test
    fun withDraggingSwitchedOffOnlyTapsMovePieces() {
        val viewModel = GameViewModel(GameSetup())
        compose.setContent {
            Wise4ChessTheme {
                CompositionLocalProvider(LocalAppSettings provides AppSettings(dragPieces = false)) {
                    GameScreen(viewModel, onClose = {})
                }
            }
        }
        compose.onNodeWithTag(CHESS_BOARD_TAG).performTouchInput { swipe(at("e2", width), at("e4", width), 200) }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), viewModel.state.value.moves)
        // The swipe still selected the pawn; a tap on the target finishes the move.
        tap("e4")
        assertEquals(listOf("e4"), viewModel.state.value.moves)
        tap("g8")
        tap("g8") // a second tap on the piece puts it down again
        tap("f6")
        assertEquals(listOf("e4"), viewModel.state.value.moves)
        tap("g8")
        tap("f6")
        assertEquals(listOf("e4", "Nf6"), viewModel.state.value.moves)
    }

    @Test
    @Config(qualifiers = "ru-w412dp-h915dp-420dpi")
    fun settingsScreenTogglesDragging() {
        var settings by mutableStateOf(AppSettings())
        compose.setContent {
            Wise4ChessTheme {
                SettingsScreen(settings, onChange = { settings = it }, onClose = {})
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings_ru.png")
        compose.onNodeWithText("Перетаскивание фигур").performClick()
        compose.waitForIdle()
        assertEquals(false, settings.dragPieces)
        compose.onNodeWithText("Полоска оценки").performClick()
        compose.waitForIdle()
        assertEquals(AppSettings(dragPieces = false, showEvaluation = true), settings)
    }

    @Test
    fun settingsAreReachedFromTheMoreSection() {
        compose.setContent { Wise4ChessTheme { Wise4ChessApp() } }
        compose.onNodeWithText("More").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/more.png")
        compose.onNodeWithText("Settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Drag pieces").assertExists()
    }

    @Test
    fun promotionAsksForThePiece() {
        val viewModel = GameViewModel(GameSetup(startFen = "8/P1k5/8/8/8/8/8/4K3 w - - 0 1"))
        compose.setContent { Wise4ChessTheme { GameScreen(viewModel, onClose = {}) } }
        tap("a7")
        tap("a8")
        assertEquals(emptyList<String>(), viewModel.state.value.moves)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/game_promotion.png")
        tap("a7") // the second choice below the promotion square is the knight
        assertEquals(listOf("a8=N+"), viewModel.state.value.moves)
    }

    @Test
    fun castlingByTappingTheKingsTargetAndCancelledPromotion() {
        val castling = GameViewModel(GameSetup(startFen = "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1"))
        compose.setContent { Wise4ChessTheme { GameScreen(castling, onClose = {}) } }
        tap("e1")
        tap("h1") // in standard chess a tap on the rook selects the rook, it does not castle
        assertEquals(emptyList<String>(), castling.state.value.moves)
        tap("e1")
        tap("g1")
        assertEquals(listOf("O-O"), castling.state.value.moves)
    }
}
