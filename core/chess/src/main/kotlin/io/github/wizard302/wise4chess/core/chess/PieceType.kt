package io.github.wizard302.wise4chess.core.chess

/** Piece kinds with their lowercase FEN/SAN letter. */
enum class PieceType(val letter: Char) {
    PAWN('p'),
    KNIGHT('n'),
    BISHOP('b'),
    ROOK('r'),
    QUEEN('q'),
    KING('k');

    companion object {
        fun fromLetter(letter: Char): PieceType? {
            val lower = letter.lowercaseChar()
            return entries.firstOrNull { it.letter == lower }
        }
    }
}
