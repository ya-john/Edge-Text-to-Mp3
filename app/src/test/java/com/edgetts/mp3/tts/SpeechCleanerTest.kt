package com.edgetts.mp3.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCleanerTest {
    private fun c(s: String) = SpeechCleaner.clean(s)

    private val messy = """
        # Project **Phoenix** Notes ##
        ==============================

        Read the [full guide](https://docs.example.com/guide?x=1) or visit https://www.example.org/path/page.html.
        This is ***really*** important!!!!! Are you sure????  Wait...........
        The config_file_name lives in C:\Users\me\project\_drafts and uses snake_case_names.
        Escaped \*stars\* and \_underscores\_ should read cleanly.

        ---

        > A quote from someone smart.
        > Second quote line

        - [x] Ship the app
        - [ ] Write the docs
        * Item with `inline_code` here
        • Fancy bullet ★ with emoji 🚀🔥

        1. First step
        2) Second step

        | Name | Role |
        |------|:----:|
        | Ana  | Lead |

        ```kotlin
        val secret = "do not read"
        ```

        Chapter Two
        It was ~~not~~ a dark night <br> and the end — finally — came [12] with a footnote[^3].
        Prices rose 10–20% w/ inflation, see #4 and/or ask the client/server team -> answers.
        =====  ******  ~~~~~~  ++++  ______
        <div class="x">Hidden tag text stays</div> &amp; more&nbsp;text.
        12
        Page 4 of 20
    """.trimIndent()

    @Test
    fun cleansMessyDocument() {
        val out = c(messy)
        println("----- CLEANED -----\n$out\n-------------------")
        listOf("*", "#", "_", "\\", "|", "`", "~", "[", "]", "<", ">", "!!", "??", "....", "\uD83D\uDE80", "\u2605", "\u2022", "http", "www", "secret").forEach {
            assertFalse("should not contain '$it'", out.contains(it))
        }
        assertTrue(out.startsWith("Project Phoenix Notes."))
        assertTrue(out.contains("Read the full guide or visit example.org."))
        assertTrue(out.contains("This is really important! Are you sure? Wait..."))
        assertTrue(out.contains("config file name"))
        assertTrue(out.contains("snake case names"))
        assertTrue(out.contains("Escaped stars and underscores should read cleanly."))
        assertTrue(out.contains("Ship the app."))
        assertTrue(out.contains("Item with inline code here."))
        assertTrue(out.contains("1. First step."))
        assertTrue(out.contains("Name, Role.\n\nAna, Lead."))
        assertTrue(out.contains("Chapter Two.\nIt was not"))
        assertTrue(out.contains("Fancy bullet with emoji."))
        assertTrue(out.contains("It was not a dark night"))
        assertTrue(out.contains("the end, finally, came with a footnote."))
        assertTrue(out.contains("10 to 20% with inflation, see number 4 and or ask the client server team to answers."))
        assertTrue(out.contains("Hidden tag text stays and more text."))
        assertFalse(out.contains("Page 4"))
    }

    @Test
    fun leavesNormalProseAlone() {
        val prose = "It's 5:30 p.m. and the temperature is 25°C. Email me at jane.doe@example.com. " +
            "The ratio is 3/4, speed 60 km/h, x = 5 - 3, and C++ is fun (mostly). Phone 555-1234, dated 2024-01-05."
        assertEquals(prose, c(prose))
    }

    @Test
    fun keepsSquareMetersAndSubtractionButDropsFootnoteSuperscripts() {
        assertEquals("The flat is 50 m² in size.", c("The flat is 50 m² in size."))
        assertEquals("As shown earlier.", c("As shown earlier.²"))
    }

    @Test
    fun hardWrappedLinesStayOneSentence() {
        val wrapped = "This sentence was wrapped\nat seventy characters by\nan old text editor."
        assertEquals(wrapped, c(wrapped))
        assertEquals(listOf(wrapped.replace('\n', ' ')), TextChunker.chunk(c(wrapped)))
    }
}
