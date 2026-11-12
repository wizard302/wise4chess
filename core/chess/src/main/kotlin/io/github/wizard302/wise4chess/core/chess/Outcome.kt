package io.github.wizard302.wise4chess.core.chess

/** Ways a game ends by the rules alone, without a player claiming or agreeing to anything. */
enum class Termination {
    CHECKMATE,
    STALEMATE,
    INSUFFICIENT_MATERIAL,

    /** 75 moves by each side without a capture or pawn move. */
    SEVENTY_FIVE_MOVES,

    /** The same position has occurred five times. */
    FIVEFOLD_REPETITION,
}

/** How a game ended; [winner] is null for a draw. */
data class Outcome(val termination: Termination, val winner: Side?)

/**
 * The outcome that follows from this position alone, or null if play continues. Repetition
 * needs the game's history, see [GameNode.outcome].
 */
val Position.outcome: Outcome?
    get() = when {
        isCheckmate -> Outcome(Termination.CHECKMATE, sideToMove.opposite)
        isStalemate -> Outcome(Termination.STALEMATE, null)
        isInsufficientMaterial -> Outcome(Termination.INSUFFICIENT_MATERIAL, null)
        halfmoveClock >= 150 -> Outcome(Termination.SEVENTY_FIVE_MOVES, null)
        else -> null
    }

/** Result as recorded in a PGN "Result" tag. */
enum class GameResult(val pgn: String) {
    WHITE_WINS("1-0"),
    BLACK_WINS("0-1"),
    DRAW("1/2-1/2"),
    UNKNOWN("*");

    companion object {
        fun fromPgn(text: String?): GameResult = when (text) {
            "1-0" -> WHITE_WINS
            "0-1" -> BLACK_WINS
            "1/2-1/2", "½-½" -> DRAW
            else -> UNKNOWN
        }

        fun of(outcome: Outcome): GameResult = when (outcome.winner) {
            Side.WHITE -> WHITE_WINS
            Side.BLACK -> BLACK_WINS
            null -> DRAW
        }
    }
}
