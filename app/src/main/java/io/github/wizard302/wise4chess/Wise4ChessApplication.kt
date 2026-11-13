package io.github.wizard302.wise4chess

import android.app.Application
import android.content.Context
import io.github.wizard302.wise4chess.settings.SettingsRepository

/** App-wide objects, created once. The project wires dependencies by hand rather than with a framework. */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
}

class Wise4ChessApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
