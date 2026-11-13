package io.github.wizard302.wise4chess.feature.play

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.wizard302.wise4chess.core.chess.Piece
import io.github.wizard302.wise4chess.core.chess.PieceType
import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.Side
import io.github.wizard302.wise4chess.core.chess.Square
import io.github.wizard302.wise4chess.core.ui.board.PieceBackdrop
import io.github.wizard302.wise4chess.core.ui.board.pieceDrawable

/** What each side has taken, and who is ahead on material. */
class MaterialSummary(
    /** Enemy pieces captured by White, most valuable first. */
    val takenByWhite: List<Piece>,
    val takenByBlack: List<Piece>,
    /** Points White is ahead by (pawn 1, minor 3, rook 5, queen 9); negative if Black leads. */
    val balance: Int,
) {
    companion object {
        private val ORDER = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)
        private val VALUE = mapOf(
            PieceType.QUEEN to 9, PieceType.ROOK to 5, PieceType.BISHOP to 3, PieceType.KNIGHT to 3, PieceType.PAWN to 1,
        )

        private fun count(position: Position): IntArray {
            val counts = IntArray(Piece.entries.size)
            for (index in 0 until 64) position.pieceAt(Square(index))?.let { counts[it.ordinal]++ }
            return counts
        }

        /**
         * @param start the position the game began from; what has gone missing since then counts
         *   as taken. Promotions make this approximate, as on every chess site.
         */
        fun of(position: Position, start: Position = Position.START): MaterialSummary {
            val now = count(position)
            val initially = count(start)
            fun missing(side: Side) = ORDER.flatMap { type ->
                val piece = Piece.of(side, type)
                List((initially[piece.ordinal] - now[piece.ordinal]).coerceAtLeast(0)) { piece }
            }
            fun points(side: Side) = ORDER.sumOf { now[Piece.of(side, it).ordinal] * VALUE.getValue(it) }
            return MaterialSummary(missing(Side.BLACK), missing(Side.WHITE), points(Side.WHITE) - points(Side.BLACK))
        }
    }
}

/** A player's strip above or below the board: name, captures, material lead and clock. */
@Composable
fun PlayerBar(
    name: String,
    side: Side,
    taken: List<Piece>,
    lead: Int,
    clockMillis: Long?,
    clockRunning: Boolean,
    toMove: Boolean,
    modifier: Modifier = Modifier,
    upsideDown: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (upsideDown) Modifier.rotate(180f) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (side == Side.WHITE) Color(0xFFF1F1EE) else PieceBackdrop),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(pieceDrawable(Piece.of(side, PieceType.PAWN))),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                if (toMove) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
            Row(Modifier.height(18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (taken.isNotEmpty()) {
                    // Captured pieces overlap a little, the way they pile up beside a real board.
                    Row(
                        Modifier.clip(RoundedCornerShape(5.dp)).background(PieceBackdrop).padding(horizontal = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy((-7).dp),
                    ) {
                        for (piece in taken) {
                            Image(painterResource(pieceDrawable(piece)), contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (lead > 0) {
                    Text(
                        "+$lead",
                        modifier = Modifier.padding(start = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (clockMillis != null) ClockFace(clockMillis, clockRunning)
    }
}

@Composable
private fun ClockFace(millis: Long, running: Boolean) {
    val low = millis < 20_000
    val background = when {
        running && low -> MaterialTheme.colorScheme.error
        running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        running && low -> MaterialTheme.colorScheme.onError
        running -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        formatClock(millis),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = content,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
    )
}

/** The game's moves on one scrolling line; tapping a move shows the position after it. */
@Composable
fun MoveStrip(
    moves: List<String>,
    viewPly: Int,
    firstMoveNumber: Int,
    blackMovesFirst: Boolean,
    onSelect: (ply: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(viewPly, moves.size) {
        if (moves.isNotEmpty()) listState.animateScrollToItem((viewPly - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        itemsIndexed(moves) { index, san ->
            // Half-moves counted from White's first move of the first numbered move.
            val halfMove = index + if (blackMovesFirst) 1 else 0
            val whiteMove = halfMove % 2 == 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (whiteMove || index == 0) {
                    Text(
                        "${firstMoveNumber + halfMove / 2}${if (whiteMove) "." else "…"}",
                        modifier = Modifier.padding(start = 6.dp, end = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val current = index + 1 == viewPly
                Text(
                    san,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSelect(index + 1) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** An icon with a caption underneath, for the row of actions below the board. */
@Composable
fun ControlButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}
