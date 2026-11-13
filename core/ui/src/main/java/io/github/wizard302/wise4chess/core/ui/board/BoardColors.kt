package io.github.wizard302.wise4chess.core.ui.board

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** A mid-tone on which both white and black pieces stay readable outside the board. */
val PieceBackdrop = Color(0xFF8D9389)

/** Colours of a board theme. Highlights are translucent and drawn over the squares. */
@Immutable
data class BoardColors(
    val lightSquare: Color,
    val darkSquare: Color,
    val lastMove: Color,
    val selected: Color,
    val check: Color,
    val legalMove: Color,
) {
    companion object {
        val Green = BoardColors(
            lightSquare = Color(0xFFECEED4),
            darkSquare = Color(0xFF7A9A58),
            lastMove = Color(0x99F4E85A),
            selected = Color(0xB3F4E85A),
            check = Color(0xFFE5483C),
            legalMove = Color(0x38000000),
        )
    }
}
