package io.github.wizard302.wise4chess.core.chess

/**
 * A game as a tree of moves: the main line plus any number of nested variations, with PGN
 * tags, comments and annotation glyphs. The tree is mutable and not thread-safe.
 */
class Game(
    val startPosition: Position = Position.START,
    /** Chess960 game: affects how castling is written for engines and the PGN "Variant" tag. */
    val chess960: Boolean = false,
) {
    /** PGN tags in insertion order. "FEN" and "SetUp" are derived from [startPosition] on export. */
    val tags: MutableMap<String, String> = LinkedHashMap()

    val root: GameNode = GameNode(null, null, startPosition)

    var result: GameResult
        get() = GameResult.fromPgn(tags["Result"])
        set(value) {
            tags["Result"] = value.pgn
        }

    /** The main line's moves in order, without the root. */
    fun mainLine(): List<GameNode> = root.mainLine()

    /** The last position of the main line (the root if no move was made). */
    val lastNode: GameNode get() = root.mainLine().lastOrNull() ?: root
}

/** A position in a [Game] together with the move that led to it. The root has no move. */
class GameNode internal constructor(
    val parent: GameNode?,
    val move: Move?,
    /** The position after [move]. */
    val position: Position,
) {
    private val childNodes = ArrayList<GameNode>(1)
    private val nagList = ArrayList<Int>(0)
    private var sanCache: String? = null

    /** Continuations from here; the first one is the main move, the rest are variations. */
    val children: List<GameNode> get() = childNodes

    /** Comment after this move (for the root: a comment on the whole game). */
    var comment: String = ""

    /** Comment before this move, as written at the start of a variation. */
    var startingComment: String = ""

    /** Numeric annotation glyphs: 1 "!", 2 "?", 3 "!!", 4 "??", 5 "!?", 6 "?!", others per the PGN standard. */
    val nags: List<Int> get() = nagList

    /** Number of half-moves played from the root to reach this node. */
    val ply: Int = if (parent == null) 0 else parent.ply + 1

    val isRoot: Boolean get() = parent == null

    val mainChild: GameNode? get() = childNodes.firstOrNull()

    /** [move] in algebraic notation; null for the root. */
    val san: String?
        get() {
            val m = move ?: return null
            return sanCache ?: parent!!.position.toSan(m).also { sanCache = it }
        }

    /** True if this node is reached from the root by main moves only. */
    val isMainLine: Boolean
        get() {
            var node = this
            while (true) {
                val up = node.parent ?: return true
                if (up.childNodes[0] !== node) return false
                node = up
            }
        }

    /**
     * Continues with [move]. If that move already exists here the existing node is returned;
     * otherwise it is appended, as the main move if there was none or as a new variation.
     */
    fun addMove(move: Move): GameNode {
        childNodes.firstOrNull { it.move == move }?.let { return it }
        val node = GameNode(this, move, position.play(move))
        childNodes.add(node)
        return node
    }

    /** Removes this move and everything after it. The root cannot be removed. */
    fun remove() {
        val up = requireNotNull(parent) { "The root cannot be removed" }
        up.childNodes.remove(this)
    }

    /** Makes this move the main continuation of its parent, ahead of its siblings. */
    fun promoteToMain() {
        val up = parent ?: return
        if (up.childNodes.remove(this)) up.childNodes.add(0, this)
    }

    fun addNag(nag: Int) {
        if (nag !in nagList) nagList.add(nag)
    }

    fun clearNags() = nagList.clear()

    /** The main continuation from here to its end, not including this node. */
    fun mainLine(): List<GameNode> {
        val line = ArrayList<GameNode>()
        var node = mainChild
        while (node != null) {
            line.add(node)
            node = node.mainChild
        }
        return line
    }

    /** Nodes from the first move down to this one; empty for the root. */
    fun pathFromRoot(): List<GameNode> {
        val path = ArrayList<GameNode>(ply)
        var node: GameNode? = this
        while (node != null && !node.isRoot) {
            path.add(node)
            node = node.parent
        }
        path.reverse()
        return path
    }

    /** How many times this position has occurred on the way here, counting this occurrence. */
    fun repetitionCount(): Int {
        var count = 1
        // Nothing before the last capture or pawn move can repeat.
        var remaining = position.halfmoveClock
        var node = parent
        while (node != null && remaining > 0) {
            if (node.position.isSamePosition(position)) count++
            remaining--
            node = node.parent
        }
        return count
    }

    /** The result forced by the rules here, including fivefold repetition; null if play continues. */
    val outcome: Outcome?
        get() = position.outcome
            ?: if (repetitionCount() >= 5) Outcome(Termination.FIVEFOLD_REPETITION, null) else null

    /** True if the player to move may claim a draw: threefold repetition or the fifty-move rule. */
    val canClaimDraw: Boolean
        get() = position.halfmoveClock >= 100 || repetitionCount() >= 3
}
