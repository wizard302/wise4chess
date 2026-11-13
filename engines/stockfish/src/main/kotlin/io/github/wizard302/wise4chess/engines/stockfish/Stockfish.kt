package io.github.wizard302.wise4chess.engines.stockfish

import android.content.Context
import io.github.wizard302.wise4chess.core.engine.ProcessTransport
import io.github.wizard302.wise4chess.core.engine.UciEngine
import java.io.File

/** The Stockfish builds that ship inside the app. */
object Stockfish {
    /** Display name of the bundled engine. */
    const val LITE_NAME = "Stockfish 19 Lite"

    /**
     * Starts Stockfish with the small built-in network as a separate process. The caller runs
     * [UciEngine.start] and eventually [UciEngine.close].
     */
    fun launchLite(context: Context): UciEngine {
        val executable = File(context.applicationInfo.nativeLibraryDir, "libstockfish_lite.so")
        check(executable.exists()) { "Engine binary is missing: $executable" }
        return UciEngine(ProcessTransport(listOf(executable.absolutePath), context.filesDir))
    }
}
