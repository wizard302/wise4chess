package io.github.wizard302.wise4chess.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import io.github.wizard302.wise4chess.R

enum class Sound { MOVE, CAPTURE, GAME_END }

/** Plays the short game sound effects. Create one per screen and [release] it when leaving. */
class SoundPlayer(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = mapOf(
        Sound.MOVE to pool.load(context, R.raw.sound_move, 1),
        Sound.CAPTURE to pool.load(context, R.raw.sound_capture, 1),
        Sound.GAME_END to pool.load(context, R.raw.sound_game_end, 1),
    )

    fun play(sound: Sound) {
        // Returns 0 without playing if the sample has not finished loading yet; that is fine.
        pool.play(ids.getValue(sound), 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
