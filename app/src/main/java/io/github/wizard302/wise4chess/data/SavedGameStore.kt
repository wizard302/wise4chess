package io.github.wizard302.wise4chess.data

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Everything needed to pick an unfinished game up again. */
@Serializable
data class SavedGame(
    val startFen: String,
    val chess960: Boolean = false,
    /** Moves played, in UCI notation with castling as the king's two-square move. */
    val moves: List<String> = emptyList(),
    /** Time control; 0 minutes means the game has no clock. */
    val minutes: Int = 0,
    val incrementSeconds: Int = 0,
    val whiteMillis: Long = 0,
    val blackMillis: Long = 0,
    val faceToFace: Boolean = false,
    val whiteAtBottom: Boolean = true,
    /** Set for a game against the computer. */
    val botGame: Boolean = false,
    val humanIsWhite: Boolean = true,
    /** The computer's strength; 0 is its full strength. */
    val botElo: Int = 0,
    /** Which engine plays, by the name of its BotEngine entry. */
    val botEngine: String = "STOCKFISH",
)

/**
 * Keeps the game in progress on disk, so closing the app, or Android closing it, loses nothing.
 * There is one slot: the game most recently played.
 */
class SavedGameStore(private val file: File, scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val initial = read()
    private val state = MutableStateFlow(initial)

    /** The unfinished game, or null if there is none. */
    val current: StateFlow<SavedGame?> = state

    init {
        // Writes happen off the main thread; when moves come quickly only the latest counts.
        // What is on disk is tracked explicitly: a save may well arrive before this collector
        // has started, and must not be mistaken for the value read at start-up.
        scope.launch(Dispatchers.IO) {
            var onDisk = initial
            state.collect { game ->
                if (game != onDisk) {
                    write(game)
                    onDisk = game
                }
            }
        }
    }

    /** Stores [game] as the game in progress; null forgets it. */
    fun save(game: SavedGame?) {
        state.value = game
    }

    private fun read(): SavedGame? =
        try {
            if (file.exists()) json.decodeFromString<SavedGame>(file.readText()) else null
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null // written by a version that stored it differently: start clean
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun write(game: SavedGame?) {
        try {
            if (game == null) {
                file.delete()
            } else {
                // Written beside the file and moved over it, so a crash never leaves half a game.
                val temporary = File(file.parentFile, file.name + ".tmp")
                temporary.writeText(json.encodeToString(SavedGame.serializer(), game))
                if (!temporary.renameTo(file)) {
                    file.delete()
                    temporary.renameTo(file)
                }
            }
        } catch (_: IOException) {
            // Saving is best effort; the game itself goes on.
        }
    }
}
