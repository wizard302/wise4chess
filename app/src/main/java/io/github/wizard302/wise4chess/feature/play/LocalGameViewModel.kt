package io.github.wizard302.wise4chess.feature.play

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.wizard302.wise4chess.audio.Sound
import io.github.wizard302.wise4chess.core.chess.Chess960
import io.github.wizard302.wise4chess.core.chess.Game
import io.github.wizard302.wise4chess.core.chess.GameNode
import io.github.wizard302.wise4chess.core.chess.GameResult
import io.github.wizard302.wise4chess.core.chess.Move
import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.chess.Square
import io.github.wizard302.wise4chess.core.chess.Termination
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** How a two-player game on one device is started. */
data class GameSetup(
    /** Starting position; null for the usual one (or a random one with [chess960]). */
    val startFen: String? = null,
    val chess960: Boolean = false,
    val timeControl: TimeControl? = null,
    val faceToFace: Boolean = false,
)

enum class EndReason {
    CHECKMATE,
    STALEMATE,
    INSUFFICIENT_MATERIAL,
    SEVENTY_FIVE_MOVES,
    FIVEFOLD_REPETITION,
    RESIGNATION,
    DRAW_AGREED,
    TIMEOUT,

    /** A flag fell, but the opponent has only a king left: a draw. */
    TIMEOUT_INSUFFICIENT_MATERIAL,
}

data class GameEnd(val result: GameResult, val reason: EndReason)

data class GameUiState(
    /** The position on the board: the latest one, or an earlier one while browsing. */
    val position: Position,
    /** The position the game started from. */
    val startPosition: Position,
    val lastMove: Move?,
    /** Moves of the game in algebraic notation. */
    val moves: List<String>,
    /** How many of [moves] have been played in [position]. */
    val viewPly: Int,
    val firstMoveNumber: Int,
    val blackMovesFirst: Boolean,
    val orientation: Side,
    val faceToFace: Boolean,
    val end: GameEnd?,
    /** Remaining time, or null when playing without a clock. */
    val whiteMillis: Long?,
    val blackMillis: Long?,
    /** The side whose clock is running. */
    val clockSide: Side?,
    val canTakeBack: Boolean,
) {
    /** False while looking at an earlier position; moves can only be made when live. */
    val isLive: Boolean get() = viewPly == moves.size
}

/** A game between two people sharing the device. */
class LocalGameViewModel(private val setup: GameSetup) : ViewModel() {
    private var game = createGame()
    private var live: GameNode = game.root
    private var view: GameNode = game.root
    private var orientation = Side.WHITE
    private var faceToFace = setup.faceToFace
    private var end: GameEnd? = null

    private var whiteMillis = setup.timeControl?.initialMillis ?: 0L
    private var blackMillis = setup.timeControl?.initialMillis ?: 0L
    private var clockSide: Side? = null
    private var clockStartedAt = 0L
    private var clockStartedWith = 0L
    private var pausedSide: Side? = null
    private var ticker: Job? = null

    private val mutableState = MutableStateFlow(snapshot())
    val state: StateFlow<GameUiState> = mutableState

    private val mutableSounds = MutableSharedFlow<Sound>(extraBufferCapacity = 8)
    val sounds: SharedFlow<Sound> = mutableSounds

    private fun createGame(): Game {
        val start = when {
            setup.startFen != null -> Position.fromFenOrNull(setup.startFen) ?: Position.START
            setup.chess960 -> Chess960.startPosition(Random.nextInt(Chess960.POSITION_COUNT))
            else -> Position.START
        }
        return Game(start, setup.chess960)
    }

    fun onMove(move: Move) {
        if (end != null || view !== live || !live.position.isLegal(move)) return
        val before = live.position
        val mover = before.sideToMove
        val capture = !move.isCastling && (move.isEnPassant || before.pieceAt(move.to) != null)

        if (clockSide == mover) {
            stopClock()
            setup.timeControl?.let { addTime(mover, it.incrementMillis) }
        }
        live = live.addMove(move)
        view = live

        live.outcome?.let { outcome ->
            val reason = when (outcome.termination) {
                Termination.CHECKMATE -> EndReason.CHECKMATE
                Termination.STALEMATE -> EndReason.STALEMATE
                Termination.INSUFFICIENT_MATERIAL -> EndReason.INSUFFICIENT_MATERIAL
                Termination.SEVENTY_FIVE_MOVES -> EndReason.SEVENTY_FIVE_MOVES
                Termination.FIVEFOLD_REPETITION -> EndReason.FIVEFOLD_REPETITION
            }
            finish(GameEnd(GameResult.of(outcome), reason))
        }
        // Like on Lichess, the clocks start once both sides have made their first move.
        if (end == null && live.ply >= 2) startClock(live.position.sideToMove)
        mutableSounds.tryEmit(if (capture) Sound.CAPTURE else Sound.MOVE)
        publish()
    }

    /** Takes back the last move. Not offered once the game ended by resignation, agreement or time. */
    fun takeBack() {
        if (!canTakeBack()) return
        stopClock()
        val parent = live.parent ?: return
        live.remove()
        live = parent
        view = live
        end = null
        game.result = GameResult.UNKNOWN
        if (live.ply >= 2) startClock(live.position.sideToMove)
        publish()
    }

    private fun canTakeBack(): Boolean {
        if (live.isRoot) return false
        return when (end?.reason) {
            EndReason.RESIGNATION, EndReason.DRAW_AGREED, EndReason.TIMEOUT, EndReason.TIMEOUT_INSUFFICIENT_MATERIAL -> false
            else -> true
        }
    }

    /** Shows the position after [ply] moves; `moves.size` returns to the live position. */
    fun goTo(ply: Int) {
        val path = live.pathFromRoot()
        view = if (ply <= 0) game.root else path[(ply - 1).coerceAtMost(path.size - 1)]
        publish()
    }

    fun stepBack() = goTo(view.ply - 1)

    fun stepForward() = goTo(view.ply + 1)

    fun flipBoard() {
        orientation = orientation.opposite
        publish()
    }

    fun setFaceToFace(enabled: Boolean) {
        faceToFace = enabled
        publish()
    }

    /** The side to move gives up. */
    fun resign() {
        if (end != null) return
        val loser = live.position.sideToMove
        finish(GameEnd(if (loser == Side.WHITE) GameResult.BLACK_WINS else GameResult.WHITE_WINS, EndReason.RESIGNATION))
        publish()
    }

    fun agreeDraw() {
        if (end != null) return
        finish(GameEnd(GameResult.DRAW, EndReason.DRAW_AGREED))
        publish()
    }

    /** Starts over with the same settings (and a new random position in Chess960). */
    fun newGame() {
        stopClock()
        game = createGame()
        live = game.root
        view = live
        end = null
        pausedSide = null
        whiteMillis = setup.timeControl?.initialMillis ?: 0L
        blackMillis = whiteMillis
        publish()
    }

    /** The app went to the background: nobody can see the clock, so it stops. */
    fun pauseClock() {
        if (clockSide == null) return
        pausedSide = clockSide
        stopClock()
        publish()
    }

    fun resumeClock() {
        val side = pausedSide ?: return
        pausedSide = null
        if (end == null) startClock(side)
        publish()
    }

    private fun finish(gameEnd: GameEnd) {
        stopClock()
        end = gameEnd
        game.result = gameEnd.result
        mutableSounds.tryEmit(Sound.GAME_END)
    }

    // --- Clock ---

    private fun remaining(side: Side): Long {
        val stored = if (side == Side.WHITE) whiteMillis else blackMillis
        if (clockSide != side) return stored
        return clockStartedWith - (SystemClock.elapsedRealtime() - clockStartedAt)
    }

    private fun addTime(side: Side, millis: Long) {
        if (side == Side.WHITE) whiteMillis += millis else blackMillis += millis
    }

    private fun startClock(side: Side) {
        if (setup.timeControl == null || end != null) return
        clockSide = null
        clockStartedWith = remaining(side)
        clockStartedAt = SystemClock.elapsedRealtime()
        clockSide = side
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                delay(100)
                if (remaining(side) <= 0) {
                    flagFell(side)
                    break
                }
                publish()
            }
        }
    }

    private fun stopClock() {
        val side = clockSide ?: return
        val left = remaining(side).coerceAtLeast(0)
        clockSide = null
        if (side == Side.WHITE) whiteMillis = left else blackMillis = left
        ticker?.cancel()
        ticker = null
    }

    private fun flagFell(side: Side) {
        stopClock()
        val opponent = side.opposite
        val opponentPieces = (0 until 64).count { live.position.pieceAt(Square(it))?.side == opponent }
        val gameEnd = if (opponentPieces == 1) {
            GameEnd(GameResult.DRAW, EndReason.TIMEOUT_INSUFFICIENT_MATERIAL)
        } else {
            GameEnd(if (opponent == Side.WHITE) GameResult.WHITE_WINS else GameResult.BLACK_WINS, EndReason.TIMEOUT)
        }
        finish(gameEnd)
        publish()
    }

    private fun publish() {
        mutableState.value = snapshot()
    }

    private fun snapshot(): GameUiState {
        val timed = setup.timeControl != null
        return GameUiState(
            position = view.position,
            startPosition = game.startPosition,
            lastMove = view.move,
            moves = live.pathFromRoot().map { it.san!! },
            viewPly = view.ply,
            firstMoveNumber = game.startPosition.fullmoveNumber,
            blackMovesFirst = game.startPosition.sideToMove == Side.BLACK,
            orientation = orientation,
            faceToFace = faceToFace,
            end = end,
            whiteMillis = if (timed) remaining(Side.WHITE).coerceAtLeast(0) else null,
            blackMillis = if (timed) remaining(Side.BLACK).coerceAtLeast(0) else null,
            clockSide = clockSide,
            canTakeBack = canTakeBack(),
        )
    }
}
