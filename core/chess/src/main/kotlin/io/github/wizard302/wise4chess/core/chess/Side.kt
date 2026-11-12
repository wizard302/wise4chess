package io.github.wizard302.wise4chess.core.chess

/** The two players. Named Side rather than Color to stay clear of UI color types. */
enum class Side {
    WHITE,
    BLACK;

    val opposite: Side get() = if (this == WHITE) BLACK else WHITE
}
