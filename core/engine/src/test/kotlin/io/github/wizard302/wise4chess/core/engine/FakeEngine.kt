package io.github.wizard302.wise4chess.core.engine

import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * A scripted stand-in for an engine process. It answers the handshake like a real engine and,
 * for "go", prints [searchOutput] and then either "bestmove" at once or, for an infinite
 * search, only after "stop".
 */
class FakeEngine(
    private val searchOutput: List<String> = emptyList(),
    private val bestMove: String = "bestmove e2e4 ponder e7e5",
    private val options: List<String> = listOf(
        "option name Threads type spin default 1 min 1 max 1024",
        "option name MultiPV type spin default 1 min 1 max 256",
        "option name UCI_Chess960 type check default false",
        "option name UCI_LimitStrength type check default false",
        "option name UCI_Elo type spin default 1320 min 1320 max 3190",
    ),
    private val answersHandshake: Boolean = true,
) : UciTransport {
    private val toEngine = LinkedBlockingQueue<String>()
    private val fromEngine = LinkedBlockingQueue<String>()

    /** Every command the engine received, in order. */
    val received = java.util.Collections.synchronizedList(ArrayList<String>())

    @Volatile
    private var closed = false

    private val worker = thread(isDaemon = true) {
        var searching = false
        while (true) {
            val command = toEngine.take()
            received.add(command)
            when {
                command == "uci" -> if (answersHandshake) {
                    fromEngine.put("id name Fake 1.0")
                    fromEngine.put("id author Test")
                    options.forEach(fromEngine::put)
                    fromEngine.put("uciok")
                }
                command == "isready" -> fromEngine.put("readyok")
                command.startsWith("go") -> {
                    searchOutput.forEach(fromEngine::put)
                    if (command == "go infinite") searching = true else fromEngine.put(bestMove)
                }
                command == "stop" -> if (searching) {
                    searching = false
                    fromEngine.put(bestMove)
                }
                command == "quit" -> {
                    fromEngine.put(END)
                    return@thread
                }
            }
        }
    }

    /** Simulates the process dying. */
    fun crash() {
        fromEngine.put(END)
    }

    override fun writeLine(line: String) {
        if (closed) throw java.io.IOException("closed")
        toEngine.put(line)
    }

    override fun readLine(): String? {
        val line = fromEngine.take()
        return if (line === END) null else line
    }

    override fun close() {
        closed = true
        fromEngine.put(END)
    }

    private companion object {
        val END = String(charArrayOf('<', 'e', 'n', 'd', '>'))
    }
}
