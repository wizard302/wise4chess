package io.github.wizard302.wise4chess.core.ui.board

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.wizard302.wise4chess.core.chess.Square

/**
 * An arrow drawn over the board, such as a suggested move.
 *
 * @param width thickness of the shaft as a fraction of a square.
 */
@Immutable
data class BoardArrow(val from: Square, val to: Square, val color: Color, val width: Float = 0.17f)

internal fun DrawScope.drawArrow(arrow: BoardArrow, geometry: BoardGeometry) {
    val sq = geometry.square
    val start = geometry.center(arrow.from.index)
    val end = geometry.center(arrow.to.index)
    val delta = end - start
    val length = delta.getDistance()
    if (length == 0f) return
    val direction = delta / length
    val normal = Offset(-direction.y, direction.x)
    val shaft = arrow.width * sq
    val headLength = shaft * 2.3f
    val headHalfWidth = shaft * 1.35f
    // The shaft leaves the origin square off-centre and the tip stops short of the target's
    // centre, so the pieces on both squares stay recognisable.
    val tail = start + direction * (sq * 0.28f)
    val tip = end - direction * (sq * 0.08f)
    val neck = tip - direction * headLength
    val path = Path().apply {
        moveTo((tail + normal * (shaft / 2f)).x, (tail + normal * (shaft / 2f)).y)
        lineTo((neck + normal * (shaft / 2f)).x, (neck + normal * (shaft / 2f)).y)
        lineTo((neck + normal * headHalfWidth).x, (neck + normal * headHalfWidth).y)
        lineTo(tip.x, tip.y)
        lineTo((neck - normal * headHalfWidth).x, (neck - normal * headHalfWidth).y)
        lineTo((neck - normal * (shaft / 2f)).x, (neck - normal * (shaft / 2f)).y)
        lineTo((tail - normal * (shaft / 2f)).x, (tail - normal * (shaft / 2f)).y)
        close()
    }
    drawPath(path, arrow.color)
}
