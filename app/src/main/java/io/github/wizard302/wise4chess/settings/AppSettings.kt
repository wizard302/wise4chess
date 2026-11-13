package io.github.wizard302.wise4chess.settings

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** The user's preferences. Defaults apply until something has been changed. */
data class AppSettings(
    /** Pieces can be moved by dragging; tapping the piece and then the square always works. */
    val dragPieces: Boolean = true,
)

/** The current settings for any composable that needs them. */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Reads and stores [AppSettings] on the device. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settings: Flow<AppSettings> = dataStore.data
        // An unreadable file falls back to the defaults instead of breaking the app.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> AppSettings(dragPieces = preferences[DRAG_PIECES] ?: true) }

    suspend fun setDragPieces(enabled: Boolean) {
        dataStore.edit { it[DRAG_PIECES] = enabled }
    }

    private companion object {
        val DRAG_PIECES = booleanPreferencesKey("drag_pieces")
    }
}
