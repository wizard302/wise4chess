package org.petero.droidfish.engine.cuckoochess

import chess.ChessParseError
import chess.ComputerPlayer
import chess.Move
import chess.Position
import chess.TextIO
import io.github.wizard302.wise4chess.core.engine.UciTransport
import java.io.IOException
import org.petero.droidfish.engine.LocalPipe

/**
 * Speaks UCI for CuckooChess without a process in between: commands are handled as they are
 * written and the engine's answers queue up to be read. The command handling follows
 * DroidFish's CuckooChessEngine.java (GPL-3.0-or-later, Peter Österlund).
 *
 * It lives in the vendored classes' package because what it needs of them is package-private,
 * and those files are kept exactly as they are upstream.
 */
class CuckooTransport : UciTransport {
    private val output = LocalPipe()
    private var control: DroidEngineControl? = null
    private var position: Position? = null
    private val moves = ArrayList<Move>()
    private var closed = false

    @Synchronized
    override fun writeLine(line: String) {
        if (closed) throw IOException("The engine has been closed")
        val tokens = line.trim().split(WHITESPACE)
        try {
            when (tokens[0]) {
                "uci" -> {
                    output.printLine("id name %s", ComputerPlayer.engineName)
                    output.printLine("id author Peter Osterlund")
                    DroidEngineControl.printOptions(output)
                    output.printLine("uciok")
                }
                "isready" -> {
                    control()
                    output.printLine("readyok")
                }
                "setoption" -> setOption(tokens)
                "ucinewgame" -> control?.newGame()
                "position" -> setPosition(tokens)
                "go" -> go(tokens)
                "stop" -> control?.stopSearch()
                "quit" -> close()
            }
        } catch (_: ChessParseError) {
            // A command the engine cannot make sense of is ignored, as UCI asks.
        } catch (_: IndexOutOfBoundsException) {
        } catch (_: NumberFormatException) {
        }
    }

    override fun readLine(): String? = output.readLine()

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        control?.stopSearch()
        output.close()
    }

    private fun control(): DroidEngineControl = control ?: DroidEngineControl(output).also { control = it }

    private fun setOption(tokens: List<String>) {
        if (tokens.getOrNull(1) != "name") return
        val valueAt = tokens.indexOf("value")
        val nameEnd = if (valueAt >= 0) valueAt else tokens.size
        val name = tokens.subList(2, nameEnd).joinToString(" ").lowercase()
        val value = if (valueAt >= 0) tokens.subList(valueAt + 1, tokens.size).joinToString(" ").lowercase() else ""
        control().setOption(name, value)
    }

    private fun setPosition(tokens: List<String>) {
        var index = 1
        val fen = when (tokens[index]) {
            "startpos" -> {
                index++
                TextIO.startPosFEN
            }
            "fen" -> {
                index++
                val start = index
                while (index < tokens.size && tokens[index] != "moves") index++
                tokens.subList(start, index).joinToString(" ")
            }
            else -> return
        }
        position = TextIO.readFEN(fen)
        moves.clear()
        if (tokens.getOrNull(index) == "moves") {
            for (text in tokens.drop(index + 1)) moves.add(TextIO.uciStringToMove(text) ?: break)
        }
    }

    private fun go(tokens: List<String>) {
        val parameters = SearchParams()
        var index = 1
        while (index < tokens.size) {
            when (tokens[index++]) {
                "wtime" -> parameters.wTime = tokens[index++].toInt()
                "btime" -> parameters.bTime = tokens[index++].toInt()
                "winc" -> parameters.wInc = tokens[index++].toInt()
                "binc" -> parameters.bInc = tokens[index++].toInt()
                "movestogo" -> parameters.movesToGo = tokens[index++].toInt()
                "depth" -> parameters.depth = tokens[index++].toInt()
                "nodes" -> parameters.nodes = tokens[index++].toInt()
                "mate" -> parameters.mate = tokens[index++].toInt()
                "movetime" -> parameters.moveTime = tokens[index++].toInt()
                "infinite" -> parameters.infinite = true
            }
        }
        val start = position ?: TextIO.readFEN(TextIO.startPosFEN).also { position = it }
        control().startSearch(start, moves, parameters)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
