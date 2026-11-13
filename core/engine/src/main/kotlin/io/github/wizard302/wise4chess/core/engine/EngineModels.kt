package io.github.wizard302.wise4chess.core.engine

import io.github.wizard302.wise4chess.core.chess.Move
import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.Side

/** An evaluation from the point of view of the side to move in the searched position. */
sealed interface Score {
    /** Advantage in hundredths of a pawn. */
    data class Centipawns(val value: Int) : Score

    /** Forced mate in [moves] moves; negative when the side to move is the one being mated. */
    data class Mate(val moves: Int) : Score

    /** The same evaluation seen from White's side, given who is to move. */
    fun forWhite(sideToMove: Side): Score = if (sideToMove == Side.WHITE) this else when (this) {
        is Centipawns -> Centipawns(-value)
        is Mate -> Mate(-moves)
    }
}

/** Whether a reported score is exact or only a bound from an unfinished search. */
enum class Bound { EXACT, LOWER, UPPER }

/** One line of analysis: the engine's "info" output for one principal variation. */
data class EngineLine(
    /** 1 for the best line, 2 for the second best, … when several are requested. */
    val multiPv: Int,
    val depth: Int,
    val selectiveDepth: Int?,
    val score: Score,
    val bound: Bound,
    val nodes: Long?,
    val nodesPerSecond: Long?,
    val timeMillis: Long?,
    /** The expected continuation from the searched position. */
    val pv: List<Move>,
)

/** What a search reports while and when it finishes. */
sealed interface SearchUpdate {
    data class Info(val line: EngineLine) : SearchUpdate

    /** The search is over. [move] is null only if the position has no legal move. */
    data class BestMove(val move: Move?, val ponder: Move?) : SearchUpdate
}

/** When a search should stop. With nothing set the engine searches until stopped. */
data class SearchLimits(
    val depth: Int? = null,
    val moveTimeMillis: Long? = null,
    val nodes: Long? = null,
    /** Clock times; when given, the engine budgets its own thinking time. */
    val whiteTimeMillis: Long? = null,
    val blackTimeMillis: Long? = null,
    val whiteIncrementMillis: Long = 0,
    val blackIncrementMillis: Long = 0,
) {
    internal fun toGoCommand(): String {
        val sb = StringBuilder("go")
        depth?.let { sb.append(" depth ").append(it) }
        moveTimeMillis?.let { sb.append(" movetime ").append(it) }
        nodes?.let { sb.append(" nodes ").append(it) }
        if (whiteTimeMillis != null && blackTimeMillis != null) {
            sb.append(" wtime ").append(whiteTimeMillis.coerceAtLeast(1))
            sb.append(" btime ").append(blackTimeMillis.coerceAtLeast(1))
            sb.append(" winc ").append(whiteIncrementMillis).append(" binc ").append(blackIncrementMillis)
        }
        if (sb.length == 2) sb.append(" infinite")
        return sb.toString()
    }

    companion object {
        /** Search until the collector of the search stops listening. */
        val INFINITE = SearchLimits()
    }
}

/**
 * The position to search, given as a game: where it started and the moves played. Engines
 * need the moves, not just the final position, to see repetitions.
 */
class EnginePosition(val start: Position, val moves: List<Move> = emptyList()) {
    /** The position after [moves]. */
    val current: Position by lazy { moves.fold(start) { position, move -> position.play(move) } }
}

/** A setting an engine declares in response to "uci". */
data class UciOption(
    val name: String,
    /** "check", "spin", "combo", "button" or "string". */
    val type: String,
    val default: String?,
    val min: Long?,
    val max: Long?,
    /** The choices of a "combo" option. */
    val choices: List<String>,
)

class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)
