package io.github.wizard302.wise4chess.core.chess

/** Standard algebraic notation for a legal [move], with check and mate marks ("Nbd7", "exd8=Q+", "O-O"). */
fun Position.toSan(move: Move): String {
    val sb = StringBuilder(8)
    if (move.isCastling) {
        sb.append(if (move.isKingsideCastling) "O-O" else "O-O-O")
    } else {
        val from = move.from.index
        val to = move.to.index
        val type = typeAt(from)
        val capture = move.isEnPassant || (occupied ushr to) and 1L != 0L
        if (type == PieceType.PAWN.ordinal) {
            if (capture) sb.append('a' + (from and 7))
        } else {
            sb.append(PieceType.entries[type].letter.uppercaseChar())
            // Other pieces of the same kind that could also reach the square decide how much
            // of the origin has to be spelled out.
            var rivals = false
            var sameFile = false
            var sameRank = false
            for (other in legalBits()) {
                val otherFrom = other and 63
                if (other and Move.CASTLING_FLAG != 0 || otherFrom == from) continue
                if ((other ushr 6) and 63 != to || typeAt(otherFrom) != type) continue
                rivals = true
                if (otherFrom and 7 == from and 7) sameFile = true
                if (otherFrom ushr 3 == from ushr 3) sameRank = true
            }
            if (rivals) {
                if (!sameFile) {
                    sb.append('a' + (from and 7))
                } else if (!sameRank) {
                    sb.append('1' + (from ushr 3))
                } else {
                    sb.append(move.from)
                }
            }
        }
        if (capture) sb.append('x')
        sb.append(move.to)
        move.promotion?.let { sb.append('=').append(it.letter.uppercaseChar()) }
    }
    val after = playUnchecked(move.bits)
    if (after.isCheck) sb.append(if (after.legalBits().isEmpty()) '#' else '+')
    return sb.toString()
}

/**
 * Reads a move in algebraic notation. Lenient about what real PGN files contain: check marks
 * and "!?" glyphs are ignored, "0-0" is castling, "e8Q" is accepted for "e8=Q", and long
 * algebraic forms such as "Ng1f3" or "e2-e4" work. Returns null if no legal move matches or
 * the text is ambiguous.
 */
fun Position.parseSan(text: String): Move? {
    val san = text.trim().trimEnd('+', '#', '!', '?')
    if (san.isEmpty()) return null
    when (san.replace('0', 'O').replace('o', 'O')) {
        "O-O" -> return legalMoves.firstOrNull { it.isKingsideCastling }
        "O-O-O" -> return legalMoves.firstOrNull { it.isQueensideCastling }
    }

    var end = san.length
    var promotion: PieceType? = null
    if (end >= 3 && san[end - 1].isLetter() && (san[end - 2] == '=' || san[end - 2].isDigit())) {
        promotion = PieceType.fromLetter(san[end - 1])
        if (promotion == null || promotion == PieceType.PAWN || promotion == PieceType.KING) return null
        end -= if (san[end - 2] == '=') 2 else 1
    }
    if (end < 2) return null
    val to = Square.parse(san.substring(end - 2, end)) ?: return null

    var index = 0
    var type = PieceType.PAWN
    if (end > 2 && san[0] in "KQRBN") {
        type = PieceType.fromLetter(san[0])!!
        index = 1
    }
    var fromFile = -1
    var fromRank = -1
    while (index < end - 2) {
        when (val char = san[index]) {
            in 'a'..'h' -> fromFile = char - 'a'
            in '1'..'8' -> fromRank = char - '1'
            'x', ':', '-' -> {}
            else -> return null
        }
        index++
    }

    var found: Move? = null
    for (move in legalMoves) {
        if (move.isCastling || move.to != to || move.promotion != promotion) continue
        val from = move.from
        if (typeAt(from.index) != type.ordinal) continue
        if (fromFile >= 0 && from.file != fromFile) continue
        if (fromRank >= 0 && from.rank != fromRank) continue
        if (found != null) return null
        found = move
    }
    return found
}

/**
 * Reads a move in UCI notation ("e2e4", "e7e8q"). Castling is accepted both as the king's
 * two-square move and as king-takes-rook, so standard and Chess960 engines are both understood.
 */
fun Position.parseUci(text: String): Move? {
    if (text.length != 4 && text.length != 5) return null
    val from = Square.parse(text.substring(0, 2)) ?: return null
    val to = Square.parse(text.substring(2, 4)) ?: return null
    var promotion: PieceType? = null
    if (text.length == 5) {
        promotion = PieceType.fromLetter(text[4])
        if (promotion == null || promotion == PieceType.PAWN || promotion == PieceType.KING) return null
    }
    return findMove(from, to, promotion)
}
