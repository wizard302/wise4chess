package io.github.wizard302.wise4chess.core.chess

/**
 * A move packed into an Int. [to] is always where the moving piece lands; for castling that is
 * the king's destination (g- or c-file) and the rook is described by [castlingRookFrom] and
 * [castlingRookTo], which also covers Chess960.
 *
 * Moves are only meaningful for the position they were generated from; obtain them from
 * [Position.legalMoves], [Position.findMove], [Position.parseSan] or [Position.parseUci].
 */
@JvmInline
value class Move internal constructor(internal val bits: Int) {
    val from: Square get() = Square(bits and 63)
    val to: Square get() = Square((bits ushr 6) and 63)

    val promotion: PieceType?
        get() {
            val code = (bits ushr PROMOTION_SHIFT) and 7
            return if (code == 0) null else PieceType.entries[code]
        }

    val isEnPassant: Boolean get() = bits and EN_PASSANT_FLAG != 0
    val isCastling: Boolean get() = bits and CASTLING_FLAG != 0
    val isKingsideCastling: Boolean get() = isCastling && to.file == 6
    val isQueensideCastling: Boolean get() = isCastling && to.file == 2

    val castlingRookFrom: Square?
        get() = if (isCastling) Square((bits ushr ROOK_FROM_SHIFT) and 63) else null

    val castlingRookTo: Square?
        get() = if (isCastling) Square(if (to.file == 6) to.index - 1 else to.index + 1) else null

    /**
     * UCI notation. Castling is written as the king's two-square move ("e1g1"), or, with
     * [chess960] set, as king-takes-rook ("e1h1") the way UCI_Chess960 engines expect.
     */
    fun toUci(chess960: Boolean = false): String {
        val target = if (chess960 && isCastling) castlingRookFrom!! else to
        val suffix = promotion?.letter?.toString() ?: ""
        return "$from$target$suffix"
    }

    override fun toString(): String = toUci()

    internal companion object {
        const val PROMOTION_SHIFT = 12
        const val EN_PASSANT_FLAG = 1 shl 15
        const val CASTLING_FLAG = 1 shl 16
        const val ROOK_FROM_SHIFT = 17

        fun encode(from: Int, to: Int): Int = from or (to shl 6)

        /** [promotion] is a [PieceType] ordinal (knight = 1 … queen = 4). */
        fun encodePromotion(from: Int, to: Int, promotion: Int): Int =
            from or (to shl 6) or (promotion shl PROMOTION_SHIFT)

        fun encodeCastling(kingFrom: Int, kingTo: Int, rookFrom: Int): Int =
            kingFrom or (kingTo shl 6) or CASTLING_FLAG or (rookFrom shl ROOK_FROM_SHIFT)
    }
}
