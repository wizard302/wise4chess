package io.github.wizard302.wise4chess.core.chess

import io.github.wizard302.wise4chess.core.chess.Bitboards.PAWN_ATTACKS

/**
 * An immutable chess position: piece placement, side to move, castling rights, en passant
 * square and move counters. [play] returns a new position.
 *
 * Castling rights are kept as the set of rooks that may still castle, which handles standard
 * chess and Chess960 the same way.
 */
class Position internal constructor(
    internal val white: Long,
    internal val black: Long,
    internal val pawns: Long,
    internal val knights: Long,
    internal val bishops: Long,
    internal val rooks: Long,
    internal val queens: Long,
    internal val kings: Long,
    val sideToMove: Side,
    internal val castlingRooks: Long,
    /** Square behind a pawn that has just advanced two squares, or -1. */
    internal val epSquare: Int,
    /** Half-moves since the last capture or pawn move (fifty-move rule counter). */
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    private var legalBitsCache: IntArray? = null
    private var legalMovesCache: List<Move>? = null
    private var hashCache = 0L

    internal val occupied: Long get() = white or black

    /** Square a pawn may capture onto en passant, as recorded in FEN; null if none. */
    val enPassantSquare: Square? get() = if (epSquare >= 0) Square(epSquare) else null

    fun pieceAt(square: Square): Piece? {
        val type = typeAt(square.index)
        if (type < 0) return null
        val side = if ((white ushr square.index) and 1L != 0L) Side.WHITE else Side.BLACK
        return Piece.of(side, PieceType.entries[type])
    }

    fun kingSquare(side: Side): Square = Square((kings and sideBoard(side)).countTrailingZeroBits())

    /** Squares of the rooks [side] may still castle with. */
    fun castlingRookSquares(side: Side): List<Square> {
        val result = ArrayList<Square>(2)
        var rooksLeft = castlingRooks and sideBoard(side)
        while (rooksLeft != 0L) {
            result.add(Square(rooksLeft.countTrailingZeroBits()))
            rooksLeft = rooksLeft and (rooksLeft - 1)
        }
        return result
    }

    internal fun sideBoard(side: Side): Long = if (side == Side.WHITE) white else black

    /** [PieceType] ordinal of the piece on [square], or -1 if it is empty. */
    internal fun typeAt(square: Int): Int {
        val bit = 1L shl square
        return when {
            occupied and bit == 0L -> -1
            pawns and bit != 0L -> 0
            knights and bit != 0L -> 1
            bishops and bit != 0L -> 2
            rooks and bit != 0L -> 3
            queens and bit != 0L -> 4
            else -> 5
        }
    }

    /** Pieces of both sides attacking [square], given the blockers in [occupancy]. */
    private fun attackersTo(square: Int, occupancy: Long): Long =
        (PAWN_ATTACKS[1][square] and pawns and white) or
            (PAWN_ATTACKS[0][square] and pawns and black) or
            (Bitboards.KNIGHT[square] and knights) or
            (Bitboards.KING[square] and kings) or
            (Bitboards.bishopAttacks(square, occupancy) and (bishops or queens)) or
            (Bitboards.rookAttacks(square, occupancy) and (rooks or queens))

    /** True if the side to move is in check. */
    val isCheck: Boolean
        get() {
            val us = sideBoard(sideToMove)
            val kingSquare = (kings and us).countTrailingZeroBits()
            return attackersTo(kingSquare, occupied) and us.inv() and occupied != 0L
        }

    val isCheckmate: Boolean get() = legalBits().isEmpty() && isCheck
    val isStalemate: Boolean get() = legalBits().isEmpty() && !isCheck

    /** True when neither side can possibly deliver mate (bare kings, a lone minor, same-coloured bishops). */
    val isInsufficientMaterial: Boolean
        get() {
            if (pawns or rooks or queens != 0L) return false
            val minors = knights or bishops
            if (minors.countOneBits() <= 1) return true
            if (knights != 0L) return false
            return bishops and Bitboards.LIGHT_SQUARES == 0L || bishops and Bitboards.LIGHT_SQUARES.inv() == 0L
        }

    // ---------------------------------------------------------------------------------------
    // Move generation
    // ---------------------------------------------------------------------------------------

    /** All legal moves, in no particular order. */
    val legalMoves: List<Move>
        get() {
            legalMovesCache?.let { return it }
            val result = legalBits().map { Move(it) }
            legalMovesCache = result
            return result
        }

    fun legalMovesFrom(square: Square): List<Move> = legalMoves.filter { it.from == square }

    fun isLegal(move: Move): Boolean = legalBits().contains(move.bits)

    /**
     * Finds the legal move from [from] to [to]. A castling move is matched by the king's
     * destination or, as in Chess960 interfaces, by the castling rook's square. [promotion]
     * must be given exactly when the move promotes.
     */
    fun findMove(from: Square, to: Square, promotion: PieceType? = null): Move? {
        val moves = legalMoves
        moves.firstOrNull { !it.isCastling && it.from == from && it.to == to && it.promotion == promotion }
            ?.let { return it }
        if (promotion != null) return null
        return moves.firstOrNull { it.isCastling && it.from == from && it.castlingRookFrom == to }
            ?: moves.firstOrNull { it.isCastling && it.from == from && it.to == to }
    }

    internal fun legalBits(): IntArray {
        legalBitsCache?.let { return it }
        val buffer = IntArray(MAX_MOVES)
        val count = generateLegal(buffer, 0)
        val result = buffer.copyOf(count)
        legalBitsCache = result
        return result
    }

    /** Writes the legal moves into [out] starting at [offset]; returns the number of moves. */
    internal fun generateLegal(out: IntArray, offset: Int): Int {
        val whiteToMove = sideToMove == Side.WHITE
        val us = if (whiteToMove) white else black
        val them = if (whiteToMove) black else white
        val occ = white or black
        val kingSquare = (kings and us).countTrailingZeroBits()
        val inCheck = attackersTo(kingSquare, occ) and them != 0L
        // A piece off the king's lines cannot be pinned, so its moves need no legality test
        // unless the king is in check.
        val kingLines = Bitboards.QUEEN_LINES[kingSquare]
        var n = offset

        // Pawns
        val forward = if (whiteToMove) 8 else -8
        val startRank = if (whiteToMove) 1 else 6
        val promotionRank = if (whiteToMove) 7 else 0
        val pawnAttacks = PAWN_ATTACKS[sideToMove.ordinal]
        var pieces = pawns and us
        while (pieces != 0L) {
            val from = pieces.countTrailingZeroBits()
            pieces = pieces and (pieces - 1)
            val test = inCheck || (kingLines ushr from) and 1L != 0L
            val one = from + forward
            if ((occ ushr one) and 1L == 0L) {
                if (!test || kingSafeAfter(from, one, kingSquare, occ, them)) {
                    n = addPawnMove(out, n, from, one, promotionRank)
                }
                if (from ushr 3 == startRank) {
                    val two = one + forward
                    if ((occ ushr two) and 1L == 0L && (!test || kingSafeAfter(from, two, kingSquare, occ, them))) {
                        out[n++] = Move.encode(from, two)
                    }
                }
            }
            var captures = pawnAttacks[from] and them
            while (captures != 0L) {
                val to = captures.countTrailingZeroBits()
                captures = captures and (captures - 1)
                if (!test || kingSafeAfter(from, to, kingSquare, occ, them)) {
                    n = addPawnMove(out, n, from, to, promotionRank)
                }
            }
            if (epSquare >= 0 && (pawnAttacks[from] ushr epSquare) and 1L != 0L) {
                // Both pawns leave the rank at once, so this is always tested in full.
                val capturedBit = 1L shl (epSquare - forward)
                val after = (occ xor (1L shl from) xor capturedBit) or (1L shl epSquare)
                if (attackersTo(kingSquare, after) and them and capturedBit.inv() == 0L) {
                    out[n++] = Move.encode(from, epSquare) or Move.EN_PASSANT_FLAG
                }
            }
        }

        // Knights, bishops, rooks, queens
        val notUs = us.inv()
        pieces = (knights or bishops or rooks or queens) and us
        while (pieces != 0L) {
            val from = pieces.countTrailingZeroBits()
            pieces = pieces and (pieces - 1)
            val fromBit = 1L shl from
            var targets = when {
                knights and fromBit != 0L -> Bitboards.KNIGHT[from]
                bishops and fromBit != 0L -> Bitboards.bishopAttacks(from, occ)
                rooks and fromBit != 0L -> Bitboards.rookAttacks(from, occ)
                else -> Bitboards.bishopAttacks(from, occ) or Bitboards.rookAttacks(from, occ)
            } and notUs
            val test = inCheck || kingLines and fromBit != 0L
            while (targets != 0L) {
                val to = targets.countTrailingZeroBits()
                targets = targets and (targets - 1)
                if (!test || kingSafeAfter(from, to, kingSquare, occ, them)) out[n++] = Move.encode(from, to)
            }
        }

        // King
        val kingBit = 1L shl kingSquare
        val occWithoutKing = occ xor kingBit
        var targets = Bitboards.KING[kingSquare] and notUs
        while (targets != 0L) {
            val to = targets.countTrailingZeroBits()
            targets = targets and (targets - 1)
            if (attackersTo(to, occWithoutKing) and them == 0L) out[n++] = Move.encode(kingSquare, to)
        }

        // Castling. Written for Chess960; standard chess is the special case.
        if (!inCheck) {
            val backRank = if (whiteToMove) 0 else 56
            var castlers = castlingRooks and us
            while (castlers != 0L) {
                val rook = castlers.countTrailingZeroBits()
                castlers = castlers and (castlers - 1)
                val kingside = rook > kingSquare
                val kingTo = backRank + if (kingside) 6 else 2
                val rookTo = backRank + if (kingside) 5 else 3
                val rookBit = 1L shl rook
                val kingPath = Bitboards.between(kingSquare, kingTo) or (1L shl kingTo)
                val rookPath = Bitboards.between(rook, rookTo) or (1L shl rookTo)
                // Everything the king and rook travel over must be empty apart from themselves.
                if ((kingPath or rookPath) and (occ xor kingBit xor rookBit) != 0L) continue
                var safe = true
                var path = kingPath
                while (path != 0L) {
                    val square = path.countTrailingZeroBits()
                    path = path and (path - 1)
                    if (attackersTo(square, occWithoutKing) and them != 0L) {
                        safe = false
                        break
                    }
                }
                if (!safe) continue
                // In Chess960 the rook may have been shielding the king's destination.
                val after = (occ xor kingBit xor rookBit) or (1L shl rookTo)
                if (attackersTo(kingTo, after) and them != 0L) continue
                out[n++] = Move.encodeCastling(kingSquare, kingTo, rook)
            }
        }
        return n - offset
    }

    private fun addPawnMove(out: IntArray, index: Int, from: Int, to: Int, promotionRank: Int): Int {
        var n = index
        if (to ushr 3 == promotionRank) {
            for (promotion in 4 downTo 1) out[n++] = Move.encodePromotion(from, to, promotion)
        } else {
            out[n++] = Move.encode(from, to)
        }
        return n
    }

    /** True if moving a non-king piece from [from] to [to] leaves our king unattacked. */
    private fun kingSafeAfter(from: Int, to: Int, kingSquare: Int, occ: Long, them: Long): Boolean {
        val toBit = 1L shl to
        val after = (occ xor (1L shl from)) or toBit
        return attackersTo(kingSquare, after) and them and toBit.inv() == 0L
    }

    // ---------------------------------------------------------------------------------------
    // Making moves
    // ---------------------------------------------------------------------------------------

    /** Plays a legal move. Throws [IllegalArgumentException] if [move] is not legal here. */
    fun play(move: Move): Position {
        require(isLegal(move)) { "Illegal move $move in ${toFen()}" }
        return playUnchecked(move.bits)
    }

    internal fun playUnchecked(move: Int): Position {
        val from = move and 63
        val to = (move ushr 6) and 63
        val fromBit = 1L shl from
        val toBit = 1L shl to
        val whiteToMove = sideToMove == Side.WHITE
        var us = if (whiteToMove) white else black
        var them = if (whiteToMove) black else white
        var p = pawns
        var n = knights
        var b = bishops
        var r = rooks
        var q = queens
        var k = kings
        var castling = castlingRooks
        var ep = -1
        var halfmoves = halfmoveClock + 1
        val ownBackRank = if (whiteToMove) Bitboards.RANK_1 else Bitboards.RANK_8

        if (move and Move.CASTLING_FLAG != 0) {
            val rookFromBit = 1L shl ((move ushr Move.ROOK_FROM_SHIFT) and 63)
            val rookToBit = 1L shl (if (to and 7 == 6) to - 1 else to + 1)
            // Remove both pieces before placing them: in Chess960 the squares may overlap.
            us = (us and (fromBit or rookFromBit).inv()) or toBit or rookToBit
            k = (k and fromBit.inv()) or toBit
            r = (r and rookFromBit.inv()) or rookToBit
            castling = castling and ownBackRank.inv()
        } else {
            if (them and toBit != 0L) {
                val keep = toBit.inv()
                them = them and keep
                p = p and keep
                n = n and keep
                b = b and keep
                r = r and keep
                q = q and keep
                castling = castling and keep
                halfmoves = 0
            }
            when {
                p and fromBit != 0L -> {
                    halfmoves = 0
                    p = p xor fromBit
                    when ((move ushr Move.PROMOTION_SHIFT) and 7) {
                        0 -> p = p or toBit
                        1 -> n = n or toBit
                        2 -> b = b or toBit
                        3 -> r = r or toBit
                        else -> q = q or toBit
                    }
                    if (move and Move.EN_PASSANT_FLAG != 0) {
                        val keep = (1L shl (if (whiteToMove) to - 8 else to + 8)).inv()
                        p = p and keep
                        them = them and keep
                    } else if (to - from == 16 || from - to == 16) {
                        ep = (from + to) ushr 1
                    }
                }
                n and fromBit != 0L -> n = (n xor fromBit) or toBit
                b and fromBit != 0L -> b = (b xor fromBit) or toBit
                r and fromBit != 0L -> {
                    r = (r xor fromBit) or toBit
                    castling = castling and fromBit.inv()
                }
                q and fromBit != 0L -> q = (q xor fromBit) or toBit
                else -> {
                    k = (k xor fromBit) or toBit
                    castling = castling and ownBackRank.inv()
                }
            }
            us = (us xor fromBit) or toBit
        }
        return Position(
            white = if (whiteToMove) us else them,
            black = if (whiteToMove) them else us,
            pawns = p,
            knights = n,
            bishops = b,
            rooks = r,
            queens = q,
            kings = k,
            sideToMove = sideToMove.opposite,
            castlingRooks = castling,
            epSquare = ep,
            halfmoveClock = halfmoves,
            fullmoveNumber = if (whiteToMove) fullmoveNumber else fullmoveNumber + 1,
        )
    }

    /** Number of leaf positions reachable in exactly [depth] plies; the standard move generator test. */
    fun perft(depth: Int): Long {
        require(depth >= 0) { "depth must not be negative" }
        if (depth == 0) return 1
        return perft(depth, IntArray(depth * MAX_MOVES), 0)
    }

    private fun perft(depth: Int, buffer: IntArray, offset: Int): Long {
        val count = generateLegal(buffer, offset)
        if (depth == 1) return count.toLong()
        var total = 0L
        for (i in offset until offset + count) {
            total += playUnchecked(buffer[i]).perft(depth - 1, buffer, offset + MAX_MOVES)
        }
        return total
    }

    // ---------------------------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------------------------

    /**
     * Zobrist hash of what matters for repetition: placement, side to move, castling rights and
     * the en passant square when a capture there is actually legal. Move counters are excluded.
     */
    val hash: Long
        get() {
            var h = hashCache
            if (h != 0L) return h
            for (side in 0..1) {
                val sideBits = if (side == 0) white else black
                for (type in 0..5) {
                    var pieces = sideBits and when (type) {
                        0 -> pawns
                        1 -> knights
                        2 -> bishops
                        3 -> rooks
                        4 -> queens
                        else -> kings
                    }
                    while (pieces != 0L) {
                        h = h xor Zobrist.PIECE[(side * 6 + type) * 64 + pieces.countTrailingZeroBits()]
                        pieces = pieces and (pieces - 1)
                    }
                }
            }
            var castlers = castlingRooks
            while (castlers != 0L) {
                h = h xor Zobrist.CASTLING_ROOK[castlers.countTrailingZeroBits()]
                castlers = castlers and (castlers - 1)
            }
            if (sideToMove == Side.BLACK) h = h xor Zobrist.BLACK_TO_MOVE
            if (epSquare >= 0 && legalBits().any { it and Move.EN_PASSANT_FLAG != 0 }) {
                h = h xor Zobrist.EN_PASSANT_FILE[epSquare and 7]
            }
            hashCache = h
            return h
        }

    /** True if [other] is the same position for the purposes of the repetition rules. */
    fun isSamePosition(other: Position): Boolean =
        hash == other.hash && white == other.white && black == other.black && pawns == other.pawns &&
            knights == other.knights && bishops == other.bishops && rooks == other.rooks &&
            queens == other.queens && sideToMove == other.sideToMove && castlingRooks == other.castlingRooks

    /** Full equality, including the en passant square as recorded and both move counters. */
    override fun equals(other: Any?): Boolean =
        other is Position && white == other.white && black == other.black && pawns == other.pawns &&
            knights == other.knights && bishops == other.bishops && rooks == other.rooks &&
            queens == other.queens && kings == other.kings && sideToMove == other.sideToMove &&
            castlingRooks == other.castlingRooks && epSquare == other.epSquare &&
            halfmoveClock == other.halfmoveClock && fullmoveNumber == other.fullmoveNumber

    override fun hashCode(): Int = (hash xor (hash ushr 32)).toInt() * 31 + halfmoveClock * 7 + fullmoveNumber

    fun toFen(): String = Fen.write(this)

    override fun toString(): String = toFen()

    companion object {
        /** No position has more than 218 legal moves. */
        internal const val MAX_MOVES = 256

        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        /** The standard starting position. */
        val START: Position = Fen.parse(START_FEN)

        /** Parses [fen]; throws [InvalidFenException] if it does not describe a playable position. */
        fun fromFen(fen: String): Position = Fen.parse(fen)

        fun fromFenOrNull(fen: String): Position? =
            try {
                Fen.parse(fen)
            } catch (_: InvalidFenException) {
                null
            }
    }
}
