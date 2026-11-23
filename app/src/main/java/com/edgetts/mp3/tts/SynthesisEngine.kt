package com.edgetts.mp3.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class SynthesisProgress(val done: Int, val total: Int)

/**
 * Turns a full document into one MP3:
 *  1. chunk the text ([TextChunker]),
 *  2. synthesise chunks in parallel (bounded) with retries, each to its own part file on disk,
 *  3. stitch the parts in order into a single file with an ID3 tag.
 *
 * Edge returns headerless CBR MPEG frames, so byte-level concatenation yields a valid,
 * seekable MP3 with correct duration. Parts live on disk so a 500-page PDF never sits in RAM.
 */
class SynthesisEngine(
    private val client: EdgeTtsClient = EdgeTtsClient(),
    private val parallelism: Int = 4,
    private val maxAttempts: Int = 4,
) {
    suspend fun synthesizeToFile(
        text: String,
        voice: Voice,
        prosody: Prosody,
        output: File,
        workDir: File,
        title: String,
        onProgress: (SynthesisProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val chunks = TextChunker.chunk(text)
        if (chunks.isEmpty()) throw TtsException("Nothing to read: the text has no words in it.", retryable = false)

        val partsDir = File(workDir, "parts-${System.nanoTime()}").apply { mkdirs() }
        try {
            val completed = AtomicInteger(0)
            onProgress(SynthesisProgress(0, chunks.size))
            val gate = Semaphore(parallelism)

            val parts = coroutineScope {
                chunks.mapIndexed { index, chunk ->
                    async {
                        gate.withPermit {
                            val bytes = withRetry(index) { client.synthesize(chunk, voice, prosody) }
                            val part = File(partsDir, "part_%05d.mp3".format(index))
                            part.writeBytes(bytes)
                            onProgress(SynthesisProgress(completed.incrementAndGet(), chunks.size))
                            part
                        }
                    }
                }.awaitAll()
            }

            output.parentFile?.mkdirs()
            val tmp = File(output.parentFile, output.name + ".partial")
            tmp.outputStream().buffered().use { out ->
                out.write(Id3.v24(title = title, artist = "${voice.name} (Edge TTS)", album = "Edge TTS Studio"))
                for (part in parts) part.inputStream().use { it.copyTo(out) }
            }
            if (output.exists()) output.delete()
            if (!tmp.renameTo(output)) throw TtsException("Could not write ${output.name}", retryable = false)
            output
        } finally {
            partsDir.deleteRecursively()
        }
    }

    /** Synthesise a short sample straight to a file (voice preview). */
    suspend fun preview(text: String, voice: Voice, prosody: Prosody, output: File): File =
        withContext(Dispatchers.IO) {
            output.parentFile?.mkdirs()
            output.writeBytes(withRetry(0) { client.synthesize(text, voice, prosody) })
            output
        }

    private suspend fun <T> withRetry(index: Int, block: suspend () -> T): T {
        var last: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: TtsException) {
                if (!e.retryable) throw e
                last = e
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                last = e
            }
            delay(800L * (1 shl attempt))
        }
        throw TtsException("Chunk ${index + 1} failed after $maxAttempts attempts: ${last?.message}", false, last)
    }

    companion object {
        fun estimateSeconds(bytes: Long): Long = bytes * 8 / EdgeTtsClient.BITRATE_BPS
    }
}

/** Minimal ID3v2.4 writer (UTF-8 text frames) so players show a title instead of a file name. */
object Id3 {
    fun v24(title: String, artist: String, album: String): ByteArray {
        val frames = listOf("TIT2" to title, "TPE1" to artist, "TALB" to album, "TSSE" to "Microsoft Edge neural TTS")
            .map { (id, value) -> frame(id, value) }
        val body = frames.fold(ByteArray(0)) { acc, f -> acc + f }
        return byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0) + synchsafe(body.size) + body
    }

    private fun frame(id: String, value: String): ByteArray {
        val payload = byteArrayOf(3) + value.toByteArray(Charsets.UTF_8) // 3 = UTF-8
        return id.toByteArray(Charsets.US_ASCII) + synchsafe(payload.size) + byteArrayOf(0, 0) + payload
    }

    private fun synchsafe(n: Int) = byteArrayOf(
        ((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(),
        ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte(),
    )
}
