package io.github.wizard302.wise4chess.core.chess

/** A board square, indexed 0..63 with a1 = 0, b1 = 1, …, h8 = 63. */
@JvmInline
value class Square(val index: Int) {
    init {
        require(index in 0..63) { "Square index out of range: $index" }
    }

    /** 0..7 for files a..h. */
    val file: Int get() = index and 7

    /** 0..7 for ranks 1..8. */
    val rank: Int get() = index ushr 3

    val isLight: Boolean get() = (file + rank) % 2 == 1

    override fun toString(): String = "${'a' + file}${'1' + rank}"

    companion object {
        fun of(file: Int, rank: Int): Square {
            require(file in 0..7 && rank in 0..7) { "Square out of range: file=$file rank=$rank" }
            return Square(rank * 8 + file)
        }

        /** Parses algebraic coordinates such as "e4"; null if [text] is not a square. */
        fun parse(text: String): Square? {
            if (text.length != 2) return null
            val file = text[0] - 'a'
            val rank = text[1] - '1'
            return if (file in 0..7 && rank in 0..7) of(file, rank) else null
        }
    }
}
