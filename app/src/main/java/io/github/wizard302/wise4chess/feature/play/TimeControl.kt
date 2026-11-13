package io.github.wizard302.wise4chess.feature.play

/** Starting time and the increment added after each move, as in "5+3". */
data class TimeControl(val minutes: Int, val incrementSeconds: Int) {
    val initialMillis: Long get() = minutes * 60_000L
    val incrementMillis: Long get() = incrementSeconds * 1000L
    val label: String get() = "$minutes+$incrementSeconds"

    companion object {
        val PRESETS = listOf(
            TimeControl(1, 0), TimeControl(3, 0), TimeControl(3, 2), TimeControl(5, 0), TimeControl(5, 3),
            TimeControl(10, 0), TimeControl(10, 5), TimeControl(15, 10), TimeControl(30, 0),
        )
    }
}

/** "0:07.3" under ten seconds, "4:59" under an hour, "1:02:03" beyond. */
fun formatClock(millis: Long): String {
    val clamped = millis.coerceAtLeast(0)
    if (clamped < 10_000) {
        val tenths = clamped / 100
        return "0:%02d.%d".format(tenths / 10, tenths % 10)
    }
    // Round up, so the display reaches 0:00 only when the time is really gone.
    val seconds = (clamped + 999) / 1000
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}
