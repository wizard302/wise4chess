package io.github.wizard302.wise4chess.core.chess

import java.io.BufferedReader
import java.io.Reader
import java.io.StringReader

/**
 * One game read from PGN. If [error] is set the movetext could not be followed all the way;
 * [game] then holds everything up to the problem.
 */
class PgnGame(val game: Game, val error: String?)

/** PGN import and export: tags, comments, annotation glyphs and nested variations. */
object Pgn {
    private val SEVEN_TAG_ROSTER = listOf(
        "Event" to "?",
        "Site" to "?",
        "Date" to "????.??.??",
        "Round" to "?",
        "White" to "?",
        "Black" to "?",
        "Result" to "*",
    )
    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "½-½", "*")
    private const val LINE_WIDTH = 80

    /** Parses every game in [text]. */
    fun parse(text: String): List<PgnGame> = games(StringReader(text)).toList()

    /** Reads games one at a time, so files of any size can be imported without loading them whole. */
    fun games(reader: Reader): Sequence<PgnGame> = sequence {
        val parser = Parser(reader)
        while (true) {
            yield(parser.nextGame() ?: break)
        }
    }

    fun write(games: Iterable<Game>): String = games.joinToString("\n") { write(it) }

    fun write(game: Game): String {
        val sb = StringBuilder()
        val rosterNames = SEVEN_TAG_ROSTER.map { it.first }
        for ((name, default) in SEVEN_TAG_ROSTER) appendTag(sb, name, game.tags[name] ?: default)
        for ((name, value) in game.tags) {
            if (name !in rosterNames && name != "FEN" && name != "SetUp") appendTag(sb, name, value)
        }
        if (game.chess960 && "Variant" !in game.tags) appendTag(sb, "Variant", "Chess960")
        if (game.startPosition != Position.START) {
            appendTag(sb, "SetUp", "1")
            appendTag(sb, "FEN", game.startPosition.toFen())
        }
        sb.append('\n')

        val tokens = ArrayList<String>()
        if (game.root.comment.isNotEmpty()) addComment(tokens, game.root.comment)
        writeLine(tokens, game.root, forceNumber = true)
        tokens.add(game.tags["Result"] ?: "*")

        var column = 0
        for (token in tokens) {
            if (column > 0 && column + 1 + token.length > LINE_WIDTH) {
                sb.append('\n')
                column = 0
            } else if (column > 0) {
                sb.append(' ')
                column++
            }
            sb.append(token)
            column += token.length
        }
        sb.append('\n')
        return sb.toString()
    }

    private fun appendTag(sb: StringBuilder, name: String, value: String) {
        sb.append('[').append(name).append(" \"")
        for (char in value) {
            if (char == '\\' || char == '"') sb.append('\\')
            sb.append(if (char == '\n' || char == '\r') ' ' else char)
        }
        sb.append("\"]\n")
    }

    /** Writes the continuation of [start]: main moves in a loop, variations by recursion. */
    private fun writeLine(tokens: MutableList<String>, start: GameNode, forceNumber: Boolean) {
        var parent = start
        var force = forceNumber
        while (true) {
            val main = parent.mainChild ?: return
            writeMove(tokens, main, force)
            force = main.comment.isNotEmpty()
            for (i in 1 until parent.children.size) {
                val variation = parent.children[i]
                val open = tokens.size
                writeMove(tokens, variation, forceNumber = true)
                tokens[open] = "(" + tokens[open]
                writeLine(tokens, variation, forceNumber = variation.comment.isNotEmpty())
                tokens[tokens.size - 1] = tokens[tokens.size - 1] + ")"
                force = true
            }
            parent = main
        }
    }

    private fun writeMove(tokens: MutableList<String>, node: GameNode, forceNumber: Boolean) {
        var numbered = forceNumber
        if (node.startingComment.isNotEmpty()) {
            addComment(tokens, node.startingComment)
            numbered = true
        }
        val before = node.parent!!.position
        val text = StringBuilder()
        if (before.sideToMove == Side.WHITE) {
            text.append(before.fullmoveNumber).append(". ")
        } else if (numbered) {
            text.append(before.fullmoveNumber).append("... ")
        }
        // The number and the move stay on one line; split() below would undo that.
        val prefix = text.toString().trimEnd()
        if (prefix.isNotEmpty()) tokens.add(prefix)
        val san = StringBuilder(node.san!!)
        val otherNags = ArrayList<Int>()
        for (nag in node.nags) {
            when (nag) {
                1 -> san.append('!')
                2 -> san.append('?')
                3 -> san.append("!!")
                4 -> san.append("??")
                5 -> san.append("!?")
                6 -> san.append("?!")
                else -> otherNags.add(nag)
            }
        }
        tokens.add(san.toString())
        for (nag in otherNags) tokens.add("$$nag")
        if (node.comment.isNotEmpty()) addComment(tokens, node.comment)
    }

    private fun addComment(tokens: MutableList<String>, comment: String) {
        // '}' would end the comment early and PGN has no escape for it.
        val words = comment.replace('}', ')').split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        val first = tokens.size
        tokens.addAll(words)
        tokens[first] = "{" + tokens[first]
        tokens[tokens.size - 1] = tokens[tokens.size - 1] + "}"
    }

    private class Parser(reader: Reader) {
        private val input = reader as? BufferedReader ?: BufferedReader(reader)

        // The current line (always ending in '\n') and the read position in it. Working a line
        // at a time gives tag parsing the lookahead it needs.
        private var line: String? = null
        private var pos = 0

        /** The character at the read position, or -1 at the end of input. */
        private var next = -1

        private val atLineStart: Boolean get() = pos == 0

        init {
            line = input.readLine()?.plus('\n')
            load()
        }

        private fun load() {
            while (true) {
                val current = line
                if (current == null) {
                    next = -1
                    return
                }
                if (pos < current.length) {
                    next = current[pos].code
                    return
                }
                line = input.readLine()?.plus('\n')
                pos = 0
            }
        }

        private fun advance() {
            pos++
            load()
        }

        /** Skips whitespace, a byte-order mark and '%' escape lines. */
        private fun skipBlank() {
            while (next != -1) {
                val char = next.toChar()
                if (char.isWhitespace() || char == '﻿') {
                    advance()
                } else if (char == '%' && atLineStart) {
                    readRestOfLine()
                } else {
                    return
                }
            }
        }

        private fun readRestOfLine(): String {
            val sb = StringBuilder()
            while (next != -1 && next != '\n'.code) {
                sb.append(next.toChar())
                advance()
            }
            return sb.toString()
        }

        fun nextGame(): PgnGame? {
            while (true) {
                val parsed = readGame() ?: return null
                // Text left over after a result (a trailing comment, say) is not a game.
                val game = parsed.game
                if (parsed.error != null || game.tags.isNotEmpty() || game.root.children.isNotEmpty()) return parsed
            }
        }

        private fun readGame(): PgnGame? {
            skipBlank()
            if (next == -1) return null

            val tags = LinkedHashMap<String, String>()
            while (true) {
                skipBlank()
                if (next != '['.code) break
                readTag(tags)
            }

            var error: String? = null
            val start = tags["FEN"]?.let { fen ->
                try {
                    Position.fromFen(fen)
                } catch (e: InvalidFenException) {
                    error = "Invalid FEN tag: ${e.message}"
                    null
                }
            } ?: Position.START
            val variant = tags["Variant"]?.lowercase() ?: ""
            val game = Game(start, chess960 = "960" in variant || "fischer" in variant)
            game.tags.putAll(tags)

            var current = game.root
            val variationStack = ArrayDeque<GameNode>()
            // After an error the rest of the movetext is skipped up to the end of the game.
            var broken = error != null
            var variationJustOpened = false
            var startingComment: String? = null
            var result: String? = null

            while (result == null) {
                skipBlank()
                if (next == -1 || next == '['.code) break
                when (next.toChar()) {
                    '{' -> {
                        advance()
                        val sb = StringBuilder()
                        while (next != -1 && next != '}'.code) {
                            sb.append(next.toChar())
                            advance()
                        }
                        advance()
                        val comment = sb.toString().trim().replace(Regex("\\s+"), " ")
                        if (broken || comment.isEmpty()) continue
                        if (variationJustOpened) {
                            startingComment = join(startingComment ?: "", comment)
                        } else {
                            current.comment = join(current.comment, comment)
                        }
                    }
                    ';' -> {
                        advance()
                        val comment = readRestOfLine().trim()
                        if (!broken && comment.isNotEmpty()) current.comment = join(current.comment, comment)
                    }
                    '(' -> {
                        advance()
                        if (broken) continue
                        val parent = current.parent
                        if (parent == null) {
                            error = "Variation before the first move"
                            broken = true
                        } else {
                            variationStack.addLast(current)
                            current = parent
                            variationJustOpened = true
                            startingComment = null
                        }
                    }
                    ')' -> {
                        advance()
                        if (!broken && variationStack.isNotEmpty()) current = variationStack.removeLast()
                        variationJustOpened = false
                    }
                    '$' -> {
                        advance()
                        val digits = StringBuilder()
                        while (next != -1 && next.toChar().isDigit()) {
                            digits.append(next.toChar())
                            advance()
                        }
                        val nag = digits.toString().toIntOrNull()
                        if (!broken && nag != null && !current.isRoot) current.addNag(nag)
                    }
                    '}', ']' -> advance() // stray closer
                    else -> {
                        val word = stripMoveNumber(readWord())
                        if (word.isEmpty() || word == "e.p.") continue
                        if (word in RESULTS) {
                            result = if (word == "½-½") "1/2-1/2" else word
                            continue
                        }
                        if (broken) continue
                        val glyphStart = word.indexOfFirst { it == '!' || it == '?' }
                        val san = if (glyphStart >= 0) word.substring(0, glyphStart) else word
                        val nag = if (glyphStart >= 0) glyphToNag(word.substring(glyphStart)) else null
                        if (san.isEmpty()) {
                            if (nag != null && !current.isRoot) current.addNag(nag)
                            continue
                        }
                        val move = current.position.parseSan(san)
                        if (move == null) {
                            val before = current.position
                            val number = "${before.fullmoveNumber}${if (before.sideToMove == Side.WHITE) "." else "..."}"
                            error = "Illegal or unreadable move: $number $word"
                            broken = true
                            continue
                        }
                        current = current.addMove(move)
                        if (nag != null) current.addNag(nag)
                        startingComment?.let { current.startingComment = join(current.startingComment, it) }
                        startingComment = null
                        variationJustOpened = false
                    }
                }
            }

            val tagResult = tags["Result"]
            if (result != null && (tagResult == null || tagResult !in RESULTS)) game.tags["Result"] = result
            return PgnGame(game, error)
        }

        private fun join(existing: String, addition: String): String =
            if (existing.isEmpty()) addition else "$existing $addition"

        /**
         * Reads one "[Name "value"]" tag. Written to the standard, quotes and backslashes in
         * the value are escaped, but many programs write them raw, so the value is taken to
         * end at the quote that is followed by the closing bracket.
         */
        private fun readTag(tags: MutableMap<String, String>) {
            val text = line!!
            var i = pos + 1 // past '['
            val nameStart = i
            while (i < text.length && !text[i].isWhitespace() && text[i] != '"' && text[i] != ']') i++
            val name = text.substring(nameStart, i)
            while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++

            var value = ""
            if (i < text.length && text[i] == '"') {
                val valueStart = i + 1
                var valueEnd = -1
                var q = valueStart
                while (q < text.length && valueEnd < 0) {
                    if (text[q] == '"') {
                        var j = q + 1
                        while (j < text.length && (text[j] == ' ' || text[j] == '\t')) j++
                        if (j < text.length && text[j] == ']') {
                            var k = j + 1
                            while (k < text.length && text[k].isWhitespace()) k++
                            if (k == text.length || text[k] == '[') {
                                valueEnd = q
                                i = j + 1
                            }
                        }
                    }
                    q++
                }
                if (valueEnd < 0) {
                    // No proper ending: take everything up to the last quote, or the whole line.
                    val lastQuote = text.lastIndexOf('"')
                    valueEnd = if (lastQuote >= valueStart) lastQuote else text.length - 1
                    i = text.length - 1
                }
                value = unescape(text.substring(valueStart, valueEnd))
            } else {
                val close = text.indexOf(']', i)
                i = if (close >= 0) close + 1 else text.length - 1
            }
            pos = i
            load()
            if (name.isNotEmpty()) tags[name] = value.trim()
        }

        private fun unescape(raw: String): String {
            if ('\\' !in raw) return raw
            val sb = StringBuilder(raw.length)
            var i = 0
            while (i < raw.length) {
                val char = raw[i]
                if (char == '\\' && i + 1 < raw.length && (raw[i + 1] == '\\' || raw[i + 1] == '"')) i++
                sb.append(raw[i])
                i++
            }
            return sb.toString()
        }

        private fun readWord(): String {
            val sb = StringBuilder()
            while (next != -1) {
                val char = next.toChar()
                if (char.isWhitespace() || char in "{}()[];$") break
                sb.append(char)
                advance()
            }
            return sb.toString()
        }

        /** "12.", "12..." and a bare "12" are move numbers; "12.Nf3" keeps its move. */
        private fun stripMoveNumber(word: String): String {
            var i = 0
            while (i < word.length && word[i].isDigit()) i++
            if (i == word.length) return ""
            if (i > 0 && word[i] != '.') return word
            while (i < word.length && word[i] == '.') i++
            return word.substring(i)
        }

        private fun glyphToNag(glyph: String): Int? = when (glyph) {
            "!" -> 1
            "?" -> 2
            "!!" -> 3
            "??" -> 4
            "!?" -> 5
            "?!" -> 6
            else -> null
        }
    }
}
