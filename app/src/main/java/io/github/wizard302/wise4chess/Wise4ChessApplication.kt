package io.github.wizard302.wise4chess

import android.app.Application
import android.content.Context
import io.github.wizard302.wise4chess.core.engine.UciEngine
import io.github.wizard302.wise4chess.data.SavedGameStore
import io.github.wizard302.wise4chess.engines.cuckoo.CuckooChess
import io.github.wizard302.wise4chess.engines.stockfish.Stockfish
import io.github.wizard302.wise4chess.feature.play.BotEngine
import io.github.wizard302.wise4chess.settings.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide objects, created once. The project wires dependencies by hand rather than with a framework. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Lives as long as the app; for work that must outlast a screen. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context)

    val savedGame = SavedGameStore(File(appContext.filesDir, "current_game.json"), scope)

    /** Starts Stockfish, the engine used for analysis and as the stronger opponent. The caller owns and closes it. */
    val launchStockfish: () -> UciEngine = { Stockfish.launchLite(appContext) }

    /** Starts the engine behind a computer opponent. */
    fun launcherFor(engine: BotEngine): () -> UciEngine = when (engine) {
        BotEngine.STOCKFISH -> launchStockfish
        BotEngine.CUCKOO -> CuckooChess::launch
    }
}

class Wise4ChessApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
