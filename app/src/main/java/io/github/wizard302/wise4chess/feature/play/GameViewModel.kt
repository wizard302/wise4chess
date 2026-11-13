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
import io.github.wizard302.wise4chess.core.chess.parseUci
import io.github.wizard302.wise4chess.core.chess.toSan
import io.github.wizard302.wise4chess.core.engine.EngineException
import io.github.wizard302.wise4chess.core.engine.EngineLine
import io.github.wizard302.wise4chess.core.engine.EnginePosition
import io.github.wizard302.wise4chess.core.engine.Score
import io.github.wizard302.wise4chess.core.engine.SearchLimits
import io.github.wizard302.wise4chess.core.engine.SearchUpdate
import io.github.wizard302.wise4chess.core.engine.UciEngine
import io.github.wizard302.wise4chess.data.SavedGame
import io.github.wizard302.wise4chess.data.SavedGameStore
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The engines that can play as the computer opponent.
 *
 * @param minElo the weakest strength offered.
 * @param maxElo the top of the strength scale, which stands for the engine's full strength.
 */
enum class BotEngine(val displayName: String, val minElo: Int, val maxElo: Int, val defaultElo: Int) {
    STOCKFISH("Stockfish", 1320, 3200, 1500),

    /** Much weaker at the bottom of its scale than Stockfish can be made to play. */
    CUCKOO("CuckooChess", 200, 2400, 800),
}

/** The computer opponent of a game. */
data class BotSetup(
    /** The side the person plays. */
    val humanSide: Side,
    /** Playing strength, or null for the engine's full strength. */
    val elo: Int?,
    val engine: BotEngine = BotEngine.STOCKFISH,
)

/** How a game is started: two people on one device, or one against the computer if [bot] is set. */
data class GameSetup(
    /** Starting position; null for the usual one (or a random one with [chess960]). */
    val startFen: String? = null,
    val chess960: Boolean = false,
    val timeControl: TimeControl? = null,
    val faceToFace: Boolean = false,
    val bot: BotSetup? = null,
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

/** A move the engine suggests, with its evaluation from White's point of view. */
data class MoveHint(val move: Move, val san: String, val score: Score)

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
    /** The computer opponent, or null in a game between two people. */
    val bot: BotSetup? = null,
    val botName: String = "",
    val botThinking: Boolean = false,
    /** The engine could not be started or stopped answering. */
    val engineFailed: Boolean = false,
    /** The engine's suggestions for the position on the board, best first; empty unless asked for. */
    val hints: List<MoveHint> = emptyList(),
    /** True from asking for a hint until the first suggestion arrives. */
    val hintPending: Boolean = false,
    /** Whether the running evaluation is switched on. */
    val evaluationShown: Boolean = false,
    /** The engine's evaluation from White's point of view, once there is one. */
    val evaluation: Score? = null,
) {
    /** False while looking at an earlier position; moves can only be made when live. */
    val isLive: Boolean get() = viewPly == moves.size

    /** True when the person at the device may move a piece now. */
    val canMove: Boolean get() = end == null && isLive && (bot == null || position.sideToMove == bot.humanSide)
}

/**
 * A game played on the device: between two people, or against an engine.
 *
 * @param launchEngine starts the engine that plays as the computer; without a computer
 *   opponent it is the engine that analyses. Not called if the game needs no engine.
 * @param launchAnalysisEngine starts a separate engine for hints and the evaluation bar, for
 *   when the opponent is not an engine one would trust for advice. If null, [launchEngine]'s
 *   engine does both jobs.
 * @param store where the game in progress is kept, so that it survives the app being closed.
 * @param restored a game from [store] to continue instead of starting a new one.
 */
class GameViewModel(
    private val setup: GameSetup,
    private val launchEngine: () -> UciEngine = { error("No engine available") },
    private val launchAnalysisEngine: (() -> UciEngine)? = null,
    private val store: SavedGameStore? = null,
    restored: SavedGame? = null,
) : ViewModel() {
    private var game = createGame()
    private var live: GameNode = game.root
    private var view: GameNode = game.root
    private var orientation = setup.bot?.humanSide ?: Side.WHITE

    private var engine: UciEngine? = null
    private var engineStarting: Deferred<UciEngine>? = null
    private var thinking: Job? = null
    private var engineFailed = false

    private var analysisEngine: UciEngine? = null
    private var analysisEngineStarting: Deferred<UciEngine>? = null
    private var analysis: Job? = null
    private var analysedNode: GameNode? = null
    private var lines: Map<Int, EngineLine> = emptyMap()
    private var evaluation: Score? = null
    private var hintShown = false
    private var evaluationEnabled = false
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

    init {
        if (restored != null) restore(restored)
        letBotMove()
    }

    /** Replays a stored game. Moves that no longer make sense end the replay where it stands. */
    private fun restore(saved: SavedGame) {
        for (text in saved.moves) {
            val move = live.position.parseUci(text) ?: break
            live = live.addMove(move)
        }
        view = live
        orientation = if (saved.whiteAtBottom) Side.WHITE else Side.BLACK
        if (setup.timeControl != null) {
            whiteMillis = saved.whiteMillis
            blackMillis = saved.blackMillis
        }
        // A game is only stored while it is in progress, but the rules have the last word.
        live.outcome?.let { finish(GameEnd(GameResult.of(it), reasonOf(it.termination))) }
        if (end == null && live.ply >= 2) startClock(live.position.sideToMove)
        publish()
    }

    /** Writes the game in progress to the store, or clears the store once it is over. */
    private fun persist() {
        val target = store ?: return
        if (end != null) {
            target.save(null)
            return
        }
        if (live.isRoot) return // nothing worth keeping yet
        target.save(
            SavedGame(
                startFen = game.startPosition.toFen(),
                chess960 = game.chess960,
                moves = live.pathFromRoot().map { it.move!!.toUci() },
                minutes = setup.timeControl?.minutes ?: 0,
                incrementSeconds = setup.timeControl?.incrementSeconds ?: 0,
                whiteMillis = remaining(Side.WHITE).coerceAtLeast(0),
                blackMillis = remaining(Side.BLACK).coerceAtLeast(0),
                faceToFace = faceToFace,
                whiteAtBottom = orientation == Side.WHITE,
                botGame = setup.bot != null,
                humanIsWhite = setup.bot?.humanSide != Side.BLACK,
                botElo = setup.bot?.elo ?: 0,
                botEngine = setup.bot?.engine?.name ?: BotEngine.STOCKFISH.name,
            ),
        )
    }

    private fun reasonOf(termination: Termination): EndReason = when (termination) {
        Termination.CHECKMATE -> EndReason.CHECKMATE
        Termination.STALEMATE -> EndReason.STALEMATE
        Termination.INSUFFICIENT_MATERIAL -> EndReason.INSUFFICIENT_MATERIAL
        Termination.SEVENTY_FIVE_MOVES -> EndReason.SEVENTY_FIVE_MOVES
        Termination.FIVEFOLD_REPETITION -> EndReason.FIVEFOLD_REPETITION
    }

    private fun createGame(): Game {
        val start = when {
            setup.startFen != null -> Position.fromFenOrNull(setup.startFen) ?: Position.START
            setup.chess960 -> Chess960.startPosition(Random.nextInt(Chess960.POSITION_COUNT))
            else -> Position.START
        }
        return Game(start, setup.chess960)
    }

    /** A move made on the board by the person at the device. */
    fun onMove(move: Move) {
        if (view !== live || botToMove()) return
        play(move)
    }

    private fun play(move: Move) {
        if (end != null || !live.position.isLegal(move)) return
        stopAnalysis()
        hintShown = false
        val before = live.position
        val mover = before.sideToMove
        val capture = !move.isCastling && (move.isEnPassant || before.pieceAt(move.to) != null)

        if (clockSide == mover) {
            stopClock()
            setup.timeControl?.let { addTime(mover, it.incrementMillis) }
        }
        live = live.addMove(move)
        view = live

        live.outcome?.let { finish(GameEnd(GameResult.of(it), reasonOf(it.termination))) }
        // Like on Lichess, the clocks start once both sides have made their first move.
        if (end == null && live.ply >= 2) startClock(live.position.sideToMove)
        mutableSounds.tryEmit(if (capture) Sound.CAPTURE else Sound.MOVE)
        persist()
        publish()
        letBotMove()
        if (evaluationEnabled) analyse()
    }

    // --- Computer opponent ---

    private fun botToMove(): Boolean {
        val bot = setup.bot ?: return false
        return end == null && live.position.sideToMove != bot.humanSide
    }

    /** If it is the computer's turn, asks the engine for a move and plays it. */
    private fun letBotMove() {
        if (!botToMove() || thinking?.isActive == true) return
        val node = live
        thinking = viewModelScope.launch {
            try {
                val started = System.nanoTime()
                val running = engine()
                running.setStrength(setup.bot?.elo)
                val control = setup.timeControl
                val limits = if (control != null) {
                    SearchLimits(
                        whiteTimeMillis = remaining(Side.WHITE),
                        blackTimeMillis = remaining(Side.BLACK),
                        whiteIncrementMillis = control.incrementMillis,
                        blackIncrementMillis = control.incrementMillis,
                    )
                } else {
                    SearchLimits(moveTimeMillis = UNTIMED_MOVE_MILLIS)
                }
                var best: Move? = null
                val position = EnginePosition(game.startPosition, node.pathFromRoot().map { it.move!! })
                running.search(position, limits).collect { update ->
                    if (update is SearchUpdate.BestMove) best = update.move
                }
                // An instant reply looks like a glitch; give the eye a moment.
                val elapsed = (System.nanoTime() - started) / 1_000_000
                if (elapsed < MIN_REPLY_MILLIS) delay(MIN_REPLY_MILLIS - elapsed)
                thinking = null
                val move = best
                if (node === live && move != null) play(move) else publish()
            } catch (e: CancellationException) {
                throw e
            } catch (_: EngineException) {
                onEngineFailure()
            } catch (_: java.io.IOException) {
                onEngineFailure()
            } catch (_: IllegalStateException) {
                onEngineFailure()
            }
        }
        publish()
    }

    /** The running engine, started on first use. */
    private suspend fun engine(): UciEngine {
        engine?.let { return it }
        // Starting a process is slow, blocking work: keep it off the main thread.
        val starting = engineStarting ?: viewModelScope.async(Dispatchers.IO) {
            launchEngine().also { it.start() }
        }.also { engineStarting = it }
        return starting.await().also { engine = it }
    }

    /** The engine that analyses: a separate one if the game has it, else the playing engine. */
    private suspend fun analysisEngine(): UciEngine {
        val launch = launchAnalysisEngine ?: return engine()
        analysisEngine?.let { return it }
        val starting = analysisEngineStarting ?: viewModelScope.async(Dispatchers.IO) {
            launch().also { it.start() }
        }.also { analysisEngineStarting = it }
        return starting.await().also { analysisEngine = it }
    }

    private fun onEngineFailure() {
        thinking = null
        engine?.close()
        engine = null
        engineStarting = null
        if (setup.bot == null || launchAnalysisEngine == null) stopAnalysis()
        if (setup.bot == null) {
            // Between two people the engine only feeds the evaluation bar; the game goes on.
            evaluationEnabled = false
            evaluation = null
        } else {
            engineFailed = true
            stopClock()
        }
        publish()
    }

    /** Losing the adviser costs the hints and the evaluation bar, not the game. */
    private fun onAnalysisFailure() {
        if (launchAnalysisEngine == null) {
            onEngineFailure()
            return
        }
        analysisEngine?.close()
        analysisEngine = null
        analysisEngineStarting = null
        stopAnalysis()
        hintShown = false
        evaluationEnabled = false
        evaluation = null
        publish()
    }

    // --- Hints and the evaluation bar ---

    /** Shows the engine's preferred moves for the position on the board. */
    fun requestHint() {
        if (setup.bot == null || !canAnalyse()) return
        hintShown = true
        analyse()
        publish()
    }

    /** Switches the running evaluation on or off, following the user's setting. */
    fun setEvaluationEnabled(enabled: Boolean) {
        if (evaluationEnabled == enabled) return
        evaluationEnabled = enabled
        if (enabled) {
            analyse()
        } else {
            if (!hintShown) stopAnalysis()
            evaluation = null
        }
        publish()
    }

    /** The engine analyses only while a person is thinking; on its own move it is busy playing. */
    private fun canAnalyse(): Boolean = end == null && !botToMove()

    private fun analyse() {
        if (!canAnalyse() || (analysedNode === live && analysis != null)) return
        stopAnalysis()
        val node = live
        analysedNode = node
        analysis = viewModelScope.launch {
            try {
                val running = analysisEngine()
                running.setStrength(null)
                val position = EnginePosition(game.startPosition, node.pathFromRoot().map { it.move!! })
                running.search(position, SearchLimits(moveTimeMillis = ANALYSIS_MILLIS), multiPv = HINT_LINES).collect { update ->
                    if (update is SearchUpdate.Info && node === live) {
                        lines = lines + (update.line.multiPv to update.line)
                        if (update.line.multiPv == 1) evaluation = update.line.score.forWhite(node.position.sideToMove)
                        publish()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: EngineException) {
                onAnalysisFailure()
            } catch (_: java.io.IOException) {
                onAnalysisFailure()
            } catch (_: IllegalStateException) {
                onAnalysisFailure()
            }
        }
    }

    private fun stopAnalysis() {
        analysis?.cancel()
        analysis = null
        analysedNode = null
        lines = emptyMap()
    }

    private fun stopThinking() {
        thinking?.cancel()
        thinking = null
    }

    override fun onCleared() {
        stopThinking()
        stopAnalysis()
        engine?.close()
        engine = null
        analysisEngine?.close()
        analysisEngine = null
    }

    /**
     * Takes back the last move; against the computer, as many moves as it takes for the person
     * to be on move again. Not offered once the game ended by resignation, agreement or time.
     */
    fun takeBack() {
        if (!canTakeBack()) return
        stopClock()
        stopThinking()
        stopAnalysis()
        hintShown = false
        removeLastMove()
        val bot = setup.bot
        if (bot != null && !live.isRoot && live.position.sideToMove != bot.humanSide) removeLastMove()
        view = live
        end = null
        game.result = GameResult.UNKNOWN
        if (live.ply >= 2) startClock(live.position.sideToMove)
        if (live.isRoot) store?.save(null) else persist()
        publish()
        letBotMove()
        if (evaluationEnabled) analyse()
    }

    private fun removeLastMove() {
        val parent = live.parent ?: return
        live.remove()
        live = parent
    }

    private fun canTakeBack(): Boolean {
        if (live.isRoot) return false
        // Against the computer the first move of a game it opened cannot be taken back.
        val bot = setup.bot
        if (bot != null && live.ply == 1 && game.startPosition.sideToMove != bot.humanSide) return false
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
        persist()
        publish()
    }

    fun setFaceToFace(enabled: Boolean) {
        faceToFace = enabled
        publish()
    }

    /** The side to move gives up; against the computer it is always the person who resigns. */
    fun resign() {
        if (end != null) return
        stopThinking()
        val loser = setup.bot?.humanSide ?: live.position.sideToMove
        finish(GameEnd(if (loser == Side.WHITE) GameResult.BLACK_WINS else GameResult.WHITE_WINS, EndReason.RESIGNATION))
        persist()
        publish()
    }

    fun agreeDraw() {
        if (end != null || setup.bot != null) return
        finish(GameEnd(GameResult.DRAW, EndReason.DRAW_AGREED))
        persist()
        publish()
    }

    /** Starts over with the same settings (and a new random position in Chess960). */
    fun newGame() {
        stopClock()
        stopThinking()
        stopAnalysis()
        hintShown = false
        evaluation = null
        game = createGame()
        live = game.root
        view = live
        end = null
        pausedSide = null
        whiteMillis = setup.timeControl?.initialMillis ?: 0L
        blackMillis = whiteMillis
        store?.save(null)
        publish()
        viewModelScope.launch {
            try {
                engine?.newGame()
            } catch (_: EngineException) {
                // the next move request reports it
            }
            letBotMove()
            if (evaluationEnabled) analyse()
        }
    }

    /** The app went to the background: nobody can see the clock, so it stops. */
    fun pauseClock() {
        if (clockSide == null) return
        pausedSide = clockSide
        stopClock()
        persist() // the clocks as they stand, in case the app does not come back
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
        stopAnalysis()
        hintShown = false
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
        persist()
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
            bot = setup.bot,
            botName = engine?.name ?: "",
            botThinking = thinking?.isActive == true,
            engineFailed = engineFailed,
            hints = if (hintShown && analysedNode === live) hints() else emptyList(),
            hintPending = hintShown && lines.isEmpty(),
            evaluationShown = evaluationEnabled,
            evaluation = if (evaluationEnabled) evaluation else null,
        )
    }

    private fun hints(): List<MoveHint> {
        val position = live.position
        return lines.toSortedMap().values.mapNotNull { line ->
            val move = line.pv.firstOrNull() ?: return@mapNotNull null
            MoveHint(move, position.toSan(move), line.score.forWhite(position.sideToMove))
        }.distinctBy { it.move }
    }

    private companion object {
        /** Thinking time per move when the game has no clock. */
        const val UNTIMED_MOVE_MILLIS = 700L
        const val MIN_REPLY_MILLIS = 450L

        /** How long the engine looks at a position for a hint or the evaluation bar. */
        const val ANALYSIS_MILLIS = 1500L
        const val HINT_LINES = 3
    }
}
