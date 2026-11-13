package io.github.wizard302.wise4chess.core.engine

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException

/** A line-based connection to something that speaks UCI, normally an engine process. */
interface UciTransport {
    /** Sends one command. */
    @Throws(IOException::class)
    fun writeLine(line: String)

    /** Blocks until the engine prints a line; null once it has gone away. */
    @Throws(IOException::class)
    fun readLine(): String?

    fun close()
}

/** Runs an engine executable and talks to it through its standard input and output. */
class ProcessTransport(command: List<String>, workingDirectory: File? = null) : UciTransport {
    private val process: Process = ProcessBuilder(command)
        .directory(workingDirectory)
        .redirectErrorStream(true)
        .start()
    private val writer: BufferedWriter = process.outputStream.bufferedWriter()
    private val reader: BufferedReader = process.inputStream.bufferedReader()

    override fun writeLine(line: String) {
        writer.write(line)
        writer.write("\n")
        writer.flush()
    }

    override fun readLine(): String? =
        try {
            reader.readLine()
        } catch (_: IOException) {
            null // the stream is closed when the process is destroyed
        }

    override fun close() {
        process.destroy()
    }
}
