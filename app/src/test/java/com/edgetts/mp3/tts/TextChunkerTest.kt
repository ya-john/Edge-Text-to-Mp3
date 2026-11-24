package com.edgetts.mp3.tts

import com.edgetts.mp3.io.DocumentReader
import com.edgetts.mp3.io.MarkdownToSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {
    private fun words(s: String) = s.split(Regex("\\s+")).filter { it.isNotBlank() }

    @Test
    fun keepsEveryWordInOrderAndRespectsBudget() {
        val text = (1..400).joinToString("\n\n") { "Paragraph $it has a sentence. And another one & more <stuff>!" }
        val chunks = TextChunker.chunk(text, 500)
        assertTrue(chunks.size > 10)
        chunks.forEach { assertTrue("over budget: ${TextChunker.cost(it)}", TextChunker.cost(it) <= 500) }
        assertEquals(words(text), words(chunks.joinToString(" ")))
    }

    @Test
    fun splitsSentencesThatAreTooLong() {
        val sentence = (1..800).joinToString(", ") { "word$it" } + "."
        val chunks = TextChunker.chunk(sentence, 300)
        chunks.forEach { assertTrue(TextChunker.cost(it) <= 300) }
        assertEquals(words(sentence), words(chunks.joinToString(" ")))
    }

    @Test
    fun hardSplitsGiantTokens() {
        val blob = "x".repeat(5000)
        val chunks = TextChunker.chunk(blob, 1000)
        chunks.forEach { assertTrue(TextChunker.cost(it) <= 1000) }
        assertEquals(blob, chunks.joinToString(""))
    }

    @Test
    fun dropsUnspeakableChunksAndControlChars() {
        assertTrue(TextChunker.chunk("... --- !!!").isEmpty())
        assertFalse(TextChunker.chunk("Hi\u0007 there").single().contains('\u0007'))
    }

    @Test
    fun markdownBecomesSpeakableText() {
        val md = """
            # Getting started
            Read the [docs](https://example.com) for **details**.

            - First item
            - Second item

            ```kotlin
            val hidden = true
            ```
            <div>raw html</div>
        """.trimIndent()
        val out = MarkdownToSpeech.convert(md)
        assertTrue(out.startsWith("Getting started."))
        assertTrue(out.contains("Read the docs for details."))
        assertTrue(out.contains("First item."))
        assertFalse(out.contains("https://"))
        assertFalse(out.contains("hidden"))
        assertFalse(out.contains("raw html"))
        assertFalse(out.contains("*"))
    }

    @Test
    fun pdfLineBreaksAreReflowed() {
        val raw = "12\nThis is a sen-\ntence that wraps\nacross lines.\nPage 2 of 9\nNew paragraph here\nstill going.\n\n• bullet one\n• bullet two"
        val out = DocumentReader.cleanPdfText(raw)
        assertTrue(out, out.startsWith("This is a sentence that wraps across lines.\n\nNew paragraph here still going."))
        assertTrue(out, out.contains("\n\n• bullet one\n\n• bullet two"))
    }
}
