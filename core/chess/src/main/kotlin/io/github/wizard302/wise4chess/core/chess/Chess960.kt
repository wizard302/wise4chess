package io.github.wizard302.wise4chess.core.chess

/** Chess960 (Fischer Random) starting positions, numbered 0..959 by the standard Scharnagl scheme. */
object Chess960 {
    const val POSITION_COUNT = 960

    /** Index of the classical starting position (RNBQKBNR). */
    const val STANDARD_INDEX = 518

    private val KNIGHT_PLACEMENTS = arrayOf(
        intArrayOf(0, 1), intArrayOf(0, 2), intArrayOf(0, 3), intArrayOf(0, 4), intArrayOf(1, 2),
        intArrayOf(1, 3), intArrayOf(1, 4), intArrayOf(2, 3), intArrayOf(2, 4), intArrayOf(3, 4),
    )

    /** White's back rank from the a-file to the h-file, e.g. "RNBQKBNR" for [STANDARD_INDEX]. */
    fun backRank(index: Int): String {
        require(index in 0 until POSITION_COUNT) { "Chess960 index out of range: $index" }
        val rank = CharArray(8) { ' ' }
        var n = index
        rank[n % 4 * 2 + 1] = 'B'
        n /= 4
        rank[n % 4 * 2] = 'B'
        n /= 4
        rank[nthEmpty(rank, n % 6)] = 'Q'
        n /= 6
        val first = nthEmpty(rank, KNIGHT_PLACEMENTS[n][0])
        val second = nthEmpty(rank, KNIGHT_PLACEMENTS[n][1])
        rank[first] = 'N'
        rank[second] = 'N'
        for (piece in "RKR") rank[nthEmpty(rank, 0)] = piece
        return String(rank)
    }

    fun startFen(index: Int): String {
        val white = backRank(index)
        return "${white.lowercase()}/pppppppp/8/8/8/8/PPPPPPPP/$white w KQkq - 0 1"
    }

    fun startPosition(index: Int): Position = Position.fromFen(startFen(index))

    private fun nthEmpty(rank: CharArray, n: Int): Int {
        var seen = 0
        for (i in rank.indices) {
            if (rank[i] == ' ' && seen++ == n) return i
        }
        throw IllegalStateException("No empty square $n")
    }
}
