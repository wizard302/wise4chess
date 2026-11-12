package io.github.wizard302.wise4chess.core.chess

/**
 * Bitboard constants and attack tables. Bit n of a board is square index n (a1 = 0, h8 = 63).
 * Sliding attacks use hyperbola quintessence: no magic numbers, only per-square line masks.
 */
internal object Bitboards {
    const val RANK_1 = 0xFFL
    const val RANK_8 = RANK_1 shl 56
    const val LIGHT_SQUARES = 0x55AA55AA55AA55AAL

    val KNIGHT = LongArray(64)
    val KING = LongArray(64)

    /** PAWN_ATTACKS[side.ordinal][square]: squares attacked by a pawn of that side standing on square. */
    val PAWN_ATTACKS = arrayOf(LongArray(64), LongArray(64))

    /** Squares sharing a rank, file or diagonal with the square (the square itself excluded). */
    val QUEEN_LINES = LongArray(64)

    private val RANK_LINE = LongArray(64)
    private val FILE_LINE = LongArray(64)
    private val DIAGONAL_LINE = LongArray(64)
    private val ANTI_DIAGONAL_LINE = LongArray(64)

    /** BETWEEN[a * 64 + b]: squares strictly between a and b when they share a line, else 0. */
    private val BETWEEN = LongArray(64 * 64)

    init {
        val knightSteps = intArrayOf(1, 2, 2, 1, 2, -1, 1, -2, -1, -2, -2, -1, -2, 1, -1, 2)
        for (square in 0 until 64) {
            val file = square and 7
            val rank = square ushr 3
            for (i in knightSteps.indices step 2) {
                KNIGHT[square] = KNIGHT[square] or bitOrZero(file + knightSteps[i], rank + knightSteps[i + 1])
            }
            for (df in -1..1) {
                for (dr in -1..1) {
                    if (df != 0 || dr != 0) KING[square] = KING[square] or bitOrZero(file + df, rank + dr)
                }
            }
            PAWN_ATTACKS[0][square] = bitOrZero(file - 1, rank + 1) or bitOrZero(file + 1, rank + 1)
            PAWN_ATTACKS[1][square] = bitOrZero(file - 1, rank - 1) or bitOrZero(file + 1, rank - 1)
            for (other in 0 until 64) {
                if (other == square) continue
                val otherFile = other and 7
                val otherRank = other ushr 3
                val bit = 1L shl other
                if (otherRank == rank) RANK_LINE[square] = RANK_LINE[square] or bit
                if (otherFile == file) FILE_LINE[square] = FILE_LINE[square] or bit
                if (otherFile - otherRank == file - rank) DIAGONAL_LINE[square] = DIAGONAL_LINE[square] or bit
                if (otherFile + otherRank == file + rank) ANTI_DIAGONAL_LINE[square] = ANTI_DIAGONAL_LINE[square] or bit
            }
            QUEEN_LINES[square] =
                RANK_LINE[square] or FILE_LINE[square] or DIAGONAL_LINE[square] or ANTI_DIAGONAL_LINE[square]
        }
        for (a in 0 until 64) {
            for (b in 0 until 64) {
                if (a == b) continue
                val bitA = 1L shl a
                val bitB = 1L shl b
                BETWEEN[a * 64 + b] = when {
                    (RANK_LINE[a] or FILE_LINE[a]) and bitB != 0L -> rookAttacks(a, bitB) and rookAttacks(b, bitA)
                    (DIAGONAL_LINE[a] or ANTI_DIAGONAL_LINE[a]) and bitB != 0L ->
                        bishopAttacks(a, bitB) and bishopAttacks(b, bitA)
                    else -> 0L
                }
            }
        }
    }

    private fun bitOrZero(file: Int, rank: Int): Long =
        if (file in 0..7 && rank in 0..7) 1L shl (rank * 8 + file) else 0L

    private fun lineAttacks(square: Int, occupied: Long, line: Long): Long {
        val bit = 1L shl square
        var forward = occupied and line
        var reverse = java.lang.Long.reverse(forward)
        forward -= bit
        reverse -= java.lang.Long.reverse(bit)
        return (forward xor java.lang.Long.reverse(reverse)) and line
    }

    fun rookAttacks(square: Int, occupied: Long): Long =
        lineAttacks(square, occupied, RANK_LINE[square]) or lineAttacks(square, occupied, FILE_LINE[square])

    fun bishopAttacks(square: Int, occupied: Long): Long =
        lineAttacks(square, occupied, DIAGONAL_LINE[square]) or
            lineAttacks(square, occupied, ANTI_DIAGONAL_LINE[square])

    fun between(a: Int, b: Int): Long = BETWEEN[a * 64 + b]
}
