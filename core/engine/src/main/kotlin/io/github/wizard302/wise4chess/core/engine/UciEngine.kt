package io.github.wizard302.wise4chess.core.engine

import io.github.wizard302.wise4chess.core.chess.Position
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A chess engine driven over UCI.
 *
 * Call [start] once, then [search] as often as needed; [close] ends the engine. Commands are
 * serialised, so a new search simply waits until the previous one has been stopped.
 */
class UciEngine(
    private val transport: UciTransport,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lines = Channel<String>(Channel.UNLIMITED)
    private val conversation = Mutex()
    private val applied = HashMap<String, String>()

    /** Name the engine reports, e.g. "Stockfish 19". Empty until [start] has run. */
    var name: String = ""
        private set

    var author: String = ""
        private set

    /** The engine's settings by lower-case name. */
    var options: Map<String, UciOption> = emptyMap()
        private set

    /** Strengths the engine can be limited to, or null if it always plays at full strength. */
    val eloRange: IntRange?
        get() {
            val elo = options["uci_elo"] ?: return null
            if ("uci_limitstrength" !in options || elo.min == null || elo.max == null) return null
            return elo.min.toInt()..elo.max.toInt()
        }

    /** True if the engine understands Chess960; castling is then exchanged as king-takes-rook. */
    val supportsChess960: Boolean get() = "uci_chess960" in options

    init {
        scope.launch {
            try {
                while (true) lines.send(transport.readLine() ?: break)
            } catch (_: IOException) {
                // treated like the engine going away
            } finally {
                lines.close()
            }
        }
    }

    /** Performs the UCI handshake. Throws [EngineException] if the engine does not answer in time. */
    suspend fun start(timeoutMillis: Long = 10_000) = talk {
        val found = LinkedHashMap<String, UciOption>()
        try {
            withTimeout(timeoutMillis) {
                send("uci")
                while (true) {
                    val line = receive()
                    when {
                        line == "uciok" -> break
                        line.startsWith("id name ") -> name = line.removePrefix("id name ").trim()
                        line.startsWith("id author ") -> author = line.removePrefix("id author ").trim()
                        line.startsWith("option ") -> UciParser.parseOption(line)?.let { found[it.name.lowercase()] = it }
                    }
                }
                options = found
                if (supportsChess960) send("setoption name UCI_Chess960 value true")
                awaitReady()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw EngineException("The engine did not answer the UCI handshake", e)
        }
    }

    /** Sets an engine option. Unknown options are ignored; unchanged values are not sent again. */
    suspend fun setOption(name: String, value: String) = talk { setOptionLocked(name, value) }

    /**
     * Limits playing strength to [elo], clamped to what the engine supports, or lifts the limit
     * with null. Does nothing for engines without a strength setting.
     */
    suspend fun setStrength(elo: Int?) = talk {
        val range = eloRange ?: return@talk
        if (elo == null) {
            setOptionLocked("UCI_LimitStrength", "false")
        } else {
            setOptionLocked("UCI_Elo", elo.coerceIn(range).toString())
            setOptionLocked("UCI_LimitStrength", "true")
        }
    }

    /** Tells the engine that the next search belongs to a different game. */
    suspend fun newGame() = talk {
        send("ucinewgame")
        awaitReady()
    }

    /**
     * Searches [position] and reports progress, ending with [SearchUpdate.BestMove].
     *
     * The flow is cold: the search starts when it is collected. If the collector stops early,
     * which is how an open-ended analysis is ended, the engine is told to stop and brought
     * back to an idle state before anything else is sent to it.
     *
     * @param multiPv how many of the best lines to report.
     */
    fun search(position: EnginePosition, limits: SearchLimits, multiPv: Int = 1): Flow<SearchUpdate> = flow {
        talk {
            val current = position.current
            setOptionLocked("MultiPV", multiPv.coerceAtLeast(1).toString())
            send(positionCommand(position))
            send(limits.toGoCommand())
            var finished = false
            try {
                while (true) {
                    val line = receive()
                    if (line.startsWith("bestmove")) {
                        finished = true
                        emit(UciParser.parseBestMove(line, current))
                        break
                    }
                    UciParser.parseInfo(line, current)?.let { emit(SearchUpdate.Info(it)) }
                }
            } finally {
                if (!finished) withContext(NonCancellable) { stopSearch() }
            }
        }
    }.flowOn(dispatcher)

    /** Asks the engine to quit and releases the connection. */
    fun close() {
        try {
            transport.writeLine("quit")
        } catch (_: IOException) {
            // already gone
        }
        transport.close()
        scope.cancel()
    }

    private suspend inline fun <T> talk(block: () -> T): T = conversation.withLock { block() }

    private fun setOptionLocked(name: String, value: String) {
        val key = name.lowercase()
        if (key !in options || applied[key] == value) return
        send("setoption name ${options.getValue(key).name} value $value")
        applied[key] = value
    }

    private fun positionCommand(position: EnginePosition): String {
        val sb = StringBuilder("position ")
        if (position.start == Position.START) sb.append("startpos") else sb.append("fen ").append(position.start.toFen())
        if (position.moves.isNotEmpty()) {
            sb.append(" moves")
            for (move in position.moves) sb.append(' ').append(move.toUci(chess960 = supportsChess960))
        }
        return sb.toString()
    }

    private suspend fun awaitReady() {
        send("isready")
        while (receive() != "readyok") {
            // output of whatever came before
        }
    }

    /** Stops a running search and discards its remaining output. */
    private suspend fun stopSearch() {
        try {
            send("stop")
            withTimeoutOrNull(5_000) {
                while (!receive().startsWith("bestmove")) {
                    // info lines still in the pipe
                }
            }
        } catch (_: EngineException) {
            // the engine is gone; nothing left to tidy up
        }
    }

    private fun send(line: String) {
        try {
            transport.writeLine(line)
        } catch (e: IOException) {
            throw EngineException("The engine is not running", e)
        }
    }

    private suspend fun receive(): String =
        try {
            lines.receive().trim()
        } catch (e: ClosedReceiveChannelException) {
            throw EngineException("The engine stopped unexpectedly", e)
        }
}
