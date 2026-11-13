package io.github.wizard302.wise4chess.core.ui.board

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import io.github.wizard302.wise4chess.core.chess.Piece
import io.github.wizard302.wise4chess.core.ui.R

/** Drawable of [piece] in the bundled piece set. */
@DrawableRes
fun pieceDrawable(piece: Piece): Int = when (piece) {
    Piece.WHITE_PAWN -> R.drawable.piece_wp
    Piece.WHITE_KNIGHT -> R.drawable.piece_wn
    Piece.WHITE_BISHOP -> R.drawable.piece_wb
    Piece.WHITE_ROOK -> R.drawable.piece_wr
    Piece.WHITE_QUEEN -> R.drawable.piece_wq
    Piece.WHITE_KING -> R.drawable.piece_wk
    Piece.BLACK_PAWN -> R.drawable.piece_bp
    Piece.BLACK_KNIGHT -> R.drawable.piece_bn
    Piece.BLACK_BISHOP -> R.drawable.piece_bb
    Piece.BLACK_ROOK -> R.drawable.piece_br
    Piece.BLACK_QUEEN -> R.drawable.piece_bq
    Piece.BLACK_KING -> R.drawable.piece_bk
}

/** Painters for all twelve pieces, indexed by [Piece.ordinal]. */
@Composable
internal fun rememberPiecePainters(): List<Painter> = Piece.entries.map { painterResource(pieceDrawable(it)) }
