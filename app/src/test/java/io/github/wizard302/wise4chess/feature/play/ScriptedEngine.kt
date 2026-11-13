package io.github.wizard302.wise4chess.feature.play

import io.github.wizard302.wise4chess.core.chess.Position
import io.github.wizard302.wise4chess.core.chess.parseUci
import io.github.wizard302.wise4chess.core.engine.UciTransport
import java.util.concurrent.LinkedBlockingQueue

/**
 * A stand-in for an engine process that follows the game it is sent and always answers with
 * the alphabetically first legal move, so tests know what to expect. When several lines are
 * asked for it reports the first few moves in that order, scored 30, 20, 10… centipawns.
 *
 * @param failAfterMoves the "process" dies when asked for a move after this many answers.
 */
class ScriptedEngine(private val failAfterMoves: Int = Int.MAX_VALUE) : UciTransport {
    private val output = LinkedBlockingQueue<String>()
    private var position = Position.START
    private var answered = 0
    private var multiPv = 1

    val received = java.util.Collections.synchronizedList(ArrayList<String>())

    override fun writeLine(line: String) {
        received.add(line)
        when {
            line == "uci" -> {
                output.put("id name Scripted 1")
                output.put("option name MultiPV type spin default 1 min 1 max 256")
                output.put("option name UCI_LimitStrength type check default false")
                output.put("option name UCI_Elo type spin default 1320 min 1320 max 3190")
                output.put("uciok")
            }
            line == "isready" -> output.put("readyok")
            line.startsWith("setoption name MultiPV value ") -> multiPv = line.substringAfterLast(' ').toInt()
            line.startsWith("position ") -> position = parsePosition(line)
            line.startsWith("go") -> {
                if (answered++ >= failAfterMoves) {
                    output.put(END)
                    return
                }
                val moves = position.legalMoves.map { it.toUci() }.sorted()
                moves.take(multiPv).forEachIndexed { index, move ->
                    output.put("info depth 1 multipv ${index + 1} score cp ${(multiPv - index) * 10} pv $move")
                }
                output.put("bestmove ${moves.firstOrNull() ?: "(none)"}")
            }
            line == "quit" -> output.put(END)
        }
    }

    private fun parsePosition(command: String): Position {
        val tokens = command.split(' ')
        val movesAt = tokens.indexOf("moves")
        val setupEnd = if (movesAt >= 0) movesAt else tokens.size
        var result = if (tokens[1] == "startpos") Position.START else Position.fromFen(tokens.subList(2, setupEnd).joinToString(" "))
        if (movesAt >= 0) {
            for (move in tokens.drop(movesAt + 1)) result = result.play(result.parseUci(move)!!)
        }
        return result
    }

    override fun readLine(): String? {
        val line = output.take()
        return if (line === END) null else line
    }

    override fun close() {
        output.put(END)
    }

    private companion object {
        val END = String(charArrayOf('<', 'e', 'n', 'd', '>'))
    }
}
