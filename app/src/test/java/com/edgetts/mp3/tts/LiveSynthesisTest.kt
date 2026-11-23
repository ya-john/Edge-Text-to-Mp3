package com.edgetts.mp3.tts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Hits the real Edge service. Run with EDGE_TTS_LIVE=1 ./gradlew testDebugUnitTest */
class LiveSynthesisTest {
    private val live = System.getenv("EDGE_TTS_LIVE") == "1"

    @Test
    fun synthesizesMultiChunkDocumentIntoOneMp3(): Unit = runBlocking {
        assumeTrue(live)
        val paragraph = "The quick brown fox jumps over the lazy dog. Testing chunked synthesis & reassembly <with> markup characters. "
        val text = (1..12).joinToString("\n\n") { "Paragraph $it. " + paragraph.repeat(3) }
        val chunks = TextChunker.chunk(text)
        assertTrue("expected several chunks, got ${chunks.size}", chunks.size >= 3)

        val dir = Files.createTempDirectory("tts").toFile()
        val out = File(dir, "out.mp3")
        var last = SynthesisProgress(0, 0)
        val voice = Voices.byId("en-GB-SoniaNeural")
        SynthesisEngine().synthesizeToFile(text, voice, Prosody(15, -5), out, dir, "Live test") { last = it }

        assertEquals(chunks.size, last.done)
        val bytes = out.readBytes()
        assertEquals("ID3", String(bytes, 0, 3))
        val id3Size = (bytes[6].toInt() shl 21) or (bytes[7].toInt() shl 14) or (bytes[8].toInt() shl 7) or bytes[9].toInt()
        val audioStart = 10 + id3Size
        assertEquals(0xFF, bytes[audioStart].toInt() and 0xFF) // MPEG frame sync right after the tag
        val seconds = SynthesisEngine.estimateSeconds(bytes.size.toLong())
        println("chunks=${chunks.size} size=${bytes.size} ~${seconds}s -> ${out.absolutePath}")
        assertTrue(seconds > 60)
        out.copyTo(File(System.getProperty("java.io.tmpdir"), "edge-live-test.mp3"), overwrite = true)
    }

    @Test
    fun everyVoiceInCatalogIsAccepted(): Unit = runBlocking {
        assumeTrue(live)
        val client = EdgeTtsClient()
        val failures = Voices.all.mapNotNull { v ->
            runCatching { client.synthesize("Hello.", v, Prosody()) }
                .fold({ if (it.isEmpty()) v.id else null }, { "${v.id}: ${it.message}" })
        }
        assertTrue("Voices failed: $failures", failures.isEmpty())
    }
}
