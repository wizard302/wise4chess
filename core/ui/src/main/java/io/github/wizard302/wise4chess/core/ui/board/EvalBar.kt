package io.github.wizard302.wise4chess.core.ui.board

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import io.github.wizard302.wise4chess.core.chess.Side
import kotlin.math.exp

/**
 * Share of the bar that belongs to White for an evaluation seen from White's side: the
 * expected score, using the centipawn-to-winning-chances curve Lichess publishes.
 *
 * @param centipawns the evaluation, or null if [mateIn] is given.
 * @param mateIn moves to mate, positive when White mates.
 */
fun whiteShare(centipawns: Int?, mateIn: Int?): Float = when {
    mateIn != null -> if (mateIn > 0) 1f else 0f
    centipawns != null -> (1.0 / (1.0 + exp(-0.00368208 * centipawns))).toFloat()
    else -> 0.5f
}

/**
 * A vertical bar showing who is better, filled from the side of the player at the bottom.
 *
 * @param whiteShare 0 to 1, see [whiteShare]; null while there is no evaluation yet.
 */
@Composable
fun EvalBar(whiteShare: Float?, orientation: Side, modifier: Modifier = Modifier) {
    val target = whiteShare ?: 0.5f
    val share by animateFloatAsState(target, tween(300), label = "evaluation")
    Canvas(modifier) {
        val whiteHeight = size.height * share
        val white = Color(0xFFF2F2EF)
        val black = Color(0xFF3B3E3B)
        drawRect(black)
        if (orientation == Side.WHITE) {
            drawRect(white, Offset(0f, size.height - whiteHeight), Size(size.width, whiteHeight))
        } else {
            drawRect(white, Offset.Zero, Size(size.width, whiteHeight))
        }
        // A tick at the middle: equality.
        drawRect(Color(0xFF8D9389), Offset(0f, size.height / 2f - 1f), Size(size.width, 2f))
    }
}
