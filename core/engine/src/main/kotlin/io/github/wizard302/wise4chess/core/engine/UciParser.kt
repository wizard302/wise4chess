package io.github.wizard302.wise4chess.core.engine

import io.github.wizard302.wise4chess.core.chess.Move
import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.parseUci

/** Reading the lines a UCI engine prints. */
internal object UciParser {
    private val WHITESPACE = Regex("\\s+")
    private val OPTION_KEYWORDS = setOf("type", "default", "min", "max", "var")

    /** Parses "option name <name> type <type> [default <x>] [min <x>] [max <x>] [var <x>]…". */
    fun parseOption(line: String): UciOption? {
        val tokens = line.trim().split(WHITESPACE)
        if (tokens.size < 3 || tokens[0] != "option" || tokens[1] != "name") return null
        // Names and values may contain spaces, so each field runs up to the next keyword.
        val fields = ArrayList<Pair<String, String>>()
        var key = "name"
        val value = StringBuilder()
        for (token in tokens.drop(2)) {
            // The name runs up to "type", whatever words it contains; after that every keyword
            // starts a new field.
            if (token in OPTION_KEYWORDS && !(key == "name" && token != "type")) {
                fields.add(key to value.toString().trim())
                key = token
                value.clear()
            } else {
                value.append(token).append(' ')
            }
        }
        fields.add(key to value.toString().trim())
        val name = fields.first { it.first == "name" }.second
        if (name.isEmpty()) return null
        fun field(which: String) = fields.firstOrNull { it.first == which }?.second
        return UciOption(
            name = name,
            type = field("type") ?: "string",
            default = field("default")?.let { if (it == "<empty>") "" else it },
            min = field("min")?.toLongOrNull(),
            max = field("max")?.toLongOrNull(),
            choices = fields.filter { it.first == "var" }.map { it.second },
        )
    }

    /**
     * Parses an "info" line that carries a score and a variation; other info lines (current
     * move, strings, hash usage) give null. [position] is the searched position, used to turn
     * the variation into moves; it is cut short at the first move that is not legal.
     */
    fun parseInfo(line: String, position: Position): EngineLine? {
        val tokens = line.trim().split(WHITESPACE)
        if (tokens.isEmpty() || tokens[0] != "info") return null
        var multiPv = 1
        var depth: Int? = null
        var selectiveDepth: Int? = null
        var score: Score? = null
        var bound = Bound.EXACT
        var nodes: Long? = null
        var nps: Long? = null
        var time: Long? = null
        var pv: List<Move> = emptyList()
        var i = 1
        while (i < tokens.size) {
            val argument = tokens.getOrNull(i + 1)
            when (tokens[i]) {
                "depth" -> depth = argument?.toIntOrNull()
                "seldepth" -> selectiveDepth = argument?.toIntOrNull()
                "multipv" -> multiPv = argument?.toIntOrNull() ?: 1
                "nodes" -> nodes = argument?.toLongOrNull()
                "nps" -> nps = argument?.toLongOrNull()
                "time" -> time = argument?.toLongOrNull()
                "score" -> {
                    val value = tokens.getOrNull(i + 2)?.toIntOrNull()
                    score = when {
                        value == null -> null
                        argument == "cp" -> Score.Centipawns(value)
                        argument == "mate" -> Score.Mate(value)
                        else -> null
                    }
                    i += 3
                    continue
                }
                "lowerbound", "upperbound" -> {
                    bound = if (tokens[i] == "lowerbound") Bound.LOWER else Bound.UPPER
                    i += 1
                    continue
                }
                "wdl" -> {
                    i += 4 // win, draw and loss per mille
                    continue
                }
                "string" -> return null // free text up to the end of the line
                "pv" -> {
                    pv = parseVariation(tokens.subList(i + 1, tokens.size), position)
                    break
                }
                "refutation", "currline" -> break // lists of moves that run to the end of the line
            }
            i += 2 // a keyword and its single argument
        }
        if (depth == null || score == null || pv.isEmpty()) return null
        return EngineLine(multiPv, depth, selectiveDepth, score, bound, nodes, nps, time, pv)
    }

    private fun parseVariation(moves: List<String>, start: Position): List<Move> {
        val result = ArrayList<Move>(moves.size)
        var position = start
        for (text in moves) {
            val move = position.parseUci(text) ?: break
            result.add(move)
            position = position.play(move)
        }
        return result
    }

    /** Parses "bestmove <move> [ponder <move>]"; "bestmove (none)" gives no move. */
    fun parseBestMove(line: String, position: Position): SearchUpdate.BestMove {
        val tokens = line.trim().split(WHITESPACE)
        val move = tokens.getOrNull(1)?.let { position.parseUci(it) }
        val ponder = if (move != null && tokens.getOrNull(2) == "ponder") {
            tokens.getOrNull(3)?.let { position.play(move).parseUci(it) }
        } else {
            null
        }
        return SearchUpdate.BestMove(move, ponder)
    }
}
