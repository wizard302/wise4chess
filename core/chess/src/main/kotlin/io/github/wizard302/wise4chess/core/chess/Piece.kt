package io.github.wizard302.wise4chess.core.chess

/** A piece of a given side. [fenChar] is uppercase for White and lowercase for Black. */
enum class Piece(val side: Side, val type: PieceType) {
    WHITE_PAWN(Side.WHITE, PieceType.PAWN),
    WHITE_KNIGHT(Side.WHITE, PieceType.KNIGHT),
    WHITE_BISHOP(Side.WHITE, PieceType.BISHOP),
    WHITE_ROOK(Side.WHITE, PieceType.ROOK),
    WHITE_QUEEN(Side.WHITE, PieceType.QUEEN),
    WHITE_KING(Side.WHITE, PieceType.KING),
    BLACK_PAWN(Side.BLACK, PieceType.PAWN),
    BLACK_KNIGHT(Side.BLACK, PieceType.KNIGHT),
    BLACK_BISHOP(Side.BLACK, PieceType.BISHOP),
    BLACK_ROOK(Side.BLACK, PieceType.ROOK),
    BLACK_QUEEN(Side.BLACK, PieceType.QUEEN),
    BLACK_KING(Side.BLACK, PieceType.KING);

    val fenChar: Char
        get() = if (side == Side.WHITE) type.letter.uppercaseChar() else type.letter

    companion object {
        fun of(side: Side, type: PieceType): Piece = entries[side.ordinal * PieceType.entries.size + type.ordinal]

        fun fromFenChar(char: Char): Piece? {
            val type = PieceType.fromLetter(char) ?: return null
            return of(if (char.isUpperCase()) Side.WHITE else Side.BLACK, type)
        }
    }
}
