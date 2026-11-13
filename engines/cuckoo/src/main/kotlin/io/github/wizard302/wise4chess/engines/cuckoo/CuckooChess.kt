package io.github.wizard302.wise4chess.engines.cuckoo

import io.github.wizard302.wise4chess.core.engine.UciEngine
import org.petero.droidfish.engine.cuckoochess.CuckooTransport

/** CuckooChess, an engine that runs inside the app and can play very weakly. */
object CuckooChess {
    const val NAME = "CuckooChess"

    /** Playing strengths the engine can be set to. */
    val ELO_RANGE = -625..2400

    /** Starts the engine. The caller runs [UciEngine.start] and eventually [UciEngine.close]. */
    fun launch(): UciEngine = UciEngine(CuckooTransport())
}
