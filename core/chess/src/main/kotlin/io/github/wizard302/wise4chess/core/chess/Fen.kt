package io.github.wizard302.wise4chess.core.chess

class InvalidFenException(message: String) : IllegalArgumentException(message)

/**
 * FEN reading and writing. Castling rights accept and produce X-FEN: "KQkq" where the castling
 * rook is the outermost one on its side, a file letter otherwise (Chess960). Shredder-FEN file
 * letters are accepted everywhere.
 */
internal object Fen {
    private val WHITESPACE = Regex("\\s+")

    fun parse(fen: String): Position {
        val fields = fen.trim().split(WHITESPACE)
        if (fields[0].isEmpty()) throw InvalidFenException("FEN is empty")

        val boards = LongArray(6)
        var white = 0L
        var black = 0L
        val ranks = fields[0].split('/')
        if (ranks.size != 8) throw InvalidFenException("Expected 8 ranks, found ${ranks.size}")
        for ((i, rankText) in ranks.withIndex()) {
            val rank = 7 - i
            var file = 0
            for (char in rankText) {
                if (char in '1'..'8') {
                    file += char - '0'
                    continue
                }
                val piece = Piece.fromFenChar(char) ?: throw InvalidFenException("Unknown piece '$char'")
                if (file > 7) throw InvalidFenException("Rank ${rank + 1} is too long")
                val bit = 1L shl (rank * 8 + file)
                boards[piece.type.ordinal] = boards[piece.type.ordinal] or bit
                if (piece.side == Side.WHITE) white = white or bit else black = black or bit
                file++
            }
            if (file != 8) throw InvalidFenException("Rank ${rank + 1} does not have 8 squares")
        }
        val pawns = boards[0]
        val rooks = boards[3]
        val kings = boards[5]
        if ((kings and white).countOneBits() != 1 || (kings and black).countOneBits() != 1) {
            throw InvalidFenException("Each side needs exactly one king")
        }
        if (pawns and (Bitboards.RANK_1 or Bitboards.RANK_8) != 0L) {
            throw InvalidFenException("Pawns cannot stand on the first or last rank")
        }

        val sideToMove = when (fields.getOrNull(1) ?: "w") {
            "w" -> Side.WHITE
            "b" -> Side.BLACK
            else -> throw InvalidFenException("Side to move must be 'w' or 'b'")
        }

        var castlingRooks = 0L
        val castlingText = fields.getOrNull(2) ?: "-"
        if (castlingText != "-") {
            for (char in castlingText) {
                val side = if (char.isUpperCase()) Side.WHITE else Side.BLACK
                val own = if (side == Side.WHITE) white else black
                val backRank = if (side == Side.WHITE) 0 else 56
                val king = (kings and own).countTrailingZeroBits()
                val candidates = rooks and own and (0xFFL shl backRank)
                val rook = when (char.lowercaseChar()) {
                    'k' -> highestSquare(candidates and (-1L shl king))
                    'q' -> lowestSquare(candidates and (1L shl king) - 1)
                    in 'a'..'h' -> {
                        val square = backRank + (char.lowercaseChar() - 'a')
                        if ((candidates ushr square) and 1L != 0L) square else -1
                    }
                    else -> throw InvalidFenException("Unknown castling flag '$char'")
                }
                // Rights that the placement contradicts are dropped rather than rejected.
                if (rook >= 0 && king ushr 3 == backRank ushr 3) castlingRooks = castlingRooks or (1L shl rook)
            }
        }

        var epSquare = -1
        val epText = fields.getOrNull(3) ?: "-"
        if (epText != "-") {
            val square = Square.parse(epText) ?: throw InvalidFenException("Invalid en passant square '$epText'")
            // Kept only when a pawn of the side that just moved really stands in front of it.
            val expectedRank = if (sideToMove == Side.WHITE) 5 else 2
            val pusher = if (sideToMove == Side.WHITE) square.index - 8 else square.index + 8
            val origin = if (sideToMove == Side.WHITE) square.index + 8 else square.index - 8
            val theirPawns = pawns and if (sideToMove == Side.WHITE) black else white
            val occupied = white or black
            if (square.rank == expectedRank && (theirPawns ushr pusher) and 1L != 0L &&
                (occupied ushr square.index) and 1L == 0L && (occupied ushr origin) and 1L == 0L
            ) {
                epSquare = square.index
            }
        }

        val halfmoveClock = parseCounter(fields.getOrNull(4), default = 0, name = "Halfmove clock")
        val fullmoveNumber = parseCounter(fields.getOrNull(5), default = 1, name = "Fullmove number").coerceAtLeast(1)

        val position = Position(
            white = white,
            black = black,
            pawns = pawns,
            knights = boards[1],
            bishops = boards[2],
            rooks = rooks,
            queens = boards[4],
            kings = kings,
            sideToMove = sideToMove,
            castlingRooks = castlingRooks,
            epSquare = epSquare,
            halfmoveClock = halfmoveClock,
            fullmoveNumber = fullmoveNumber,
        )
        // The side that just moved must not have left its own king in check.
        val flipped = Position(
            white, black, pawns, boards[1], boards[2], rooks, boards[4], kings,
            sideToMove.opposite, 0L, -1, 0, 1,
        )
        if (flipped.isCheck) throw InvalidFenException("The side not to move is in check")
        return position
    }

    private fun parseCounter(text: String?, default: Int, name: String): Int {
        if (text == null) return default
        val value = text.toIntOrNull()
        if (value == null || value < 0) throw InvalidFenException("$name must be a non-negative number")
        return value
    }

    private fun highestSquare(board: Long): Int = if (board == 0L) -1 else 63 - board.countLeadingZeroBits()

    private fun lowestSquare(board: Long): Int = if (board == 0L) -1 else board.countTrailingZeroBits()

    fun write(position: Position): String {
        val sb = StringBuilder(90)
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val piece = position.pieceAt(Square(rank * 8 + file))
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) sb.append(empty)
                    empty = 0
                    sb.append(piece.fenChar)
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }
        sb.append(if (position.sideToMove == Side.WHITE) " w " else " b ")
        val length = sb.length
        appendCastling(sb, position, Side.WHITE)
        appendCastling(sb, position, Side.BLACK)
        if (sb.length == length) sb.append('-')
        sb.append(' ')
        sb.append(position.enPassantSquare?.toString() ?: "-")
        sb.append(' ').append(position.halfmoveClock).append(' ').append(position.fullmoveNumber)
        return sb.toString()
    }

    private fun appendCastling(sb: StringBuilder, position: Position, side: Side) {
        val own = position.sideBoard(side)
        val backRank = if (side == Side.WHITE) 0 else 56
        val king = (position.kings and own).countTrailingZeroBits()
        val rooksOnRank = position.rooks and own and (0xFFL shl backRank)
        // Kingside first, to give the usual "KQkq" order.
        for (file in 7 downTo 0) {
            val square = backRank + file
            if ((position.castlingRooks and own ushr square) and 1L == 0L) continue
            val kingside = square > king
            val outside = if (kingside) rooksOnRank and (-2L shl square) else rooksOnRank and (1L shl square) - 1
            val char = if (outside == 0L) (if (kingside) 'k' else 'q') else 'a' + file
            sb.append(if (side == Side.WHITE) char.uppercaseChar() else char)
        }
    }
}
