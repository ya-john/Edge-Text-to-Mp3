package com.edgetts.mp3.tts

/**
 * Rewrites text the way a person would read it aloud.
 *
 * Neural voices read what they are given: "**Note**" becomes "asterisk asterisk note", a URL is
 * spelled out character by character, "-----" or "!!!!" produce odd pauses, and lines without
 * punctuation run into each other. This pass strips the markup and visual noise while keeping
 * the words, and adds sentence breaks where a reader would naturally pause.
 *
 * Works on any input (typed text, .txt, Markdown, PDF output), so it does not depend on the
 * text being valid Markdown. Safe to run on already-clean text: prose passes through unchanged.
 */
object SpeechCleaner {

    fun clean(input: String): String {
        var text = input
            .replace("\r\n", "\n").replace('\r', '\n')
            .replace(INVISIBLE, "")
            .replace('\u00A0', ' ')
        text = text.replace(FENCED_CODE, "\n\n")
        text = text.replace(HTML_COMMENT, " ")
        text = text.replace(HTML_BREAK, "\n")
        text = text.replace(HTML_TAG, " ")
        text = decodeEntities(text)
        text = cleanLines(text)
        text = cleanInline(text)
        return punctuateBreaks(tidy(text))
    }

    /**
     * Runs after symbols are gone, so "Launch day 🚀" still ends in a full stop.
     *  - Last line of every paragraph gets terminal punctuation.
     *  - A short capitalised line followed by another capitalised line is a title ("Chapter Two").
     */
    private fun punctuateBreaks(text: String): String {
        val lines = text.split('\n').toMutableList()
        for (i in lines.indices) {
            val line = lines[i]
            if (line.isEmpty()) continue
            val next = lines.getOrNull(i + 1)
            val paragraphEnd = next == null || next.isEmpty()
            val looksLikeTitle = next != null && next.isNotEmpty() &&
                line.length <= 40 && line.split(' ').size <= 6 &&
                line.first().isUpperCase() && next.first().isUpperCase()
            if (paragraphEnd || looksLikeTitle) lines[i] = endSentence(line)
        }
        return lines.joinToString("\n")
    }

    // ---------------- Line level: structure ----------------

    private fun cleanLines(text: String): String {
        val out = ArrayList<String>()
        // A paragraph end is a full stop for a reader, even when the writer left it off (titles, captions).
        fun blank() {
            if (out.isNotEmpty() && out.last().isNotEmpty()) {
                out[out.lastIndex] = endSentence(out.last())
                out += ""
            }
        }
        fun block(line: String) { blank(); out += endSentence(line); out += "" }

        for (raw in text.split('\n')) {
            var line = raw.trim()
            if (line.isEmpty()) { blank(); continue }

            // ----- / ===== / *** / ~~~ / ••• : rules and setext heading underlines
            if (RULE_LINE.matches(line)) { blank(); continue }
            if (PAGE_NUMBER_LINE.matches(line)) continue
            if (LINK_DEFINITION.matches(line)) continue

            // Table rows: | a | b |  -> "a, b."
            if (line.count { it == '|' } >= 2) {
                if (TABLE_SEPARATOR.matches(line)) continue
                val cells = line.trim('|').split('|').map { it.trim() }.filter { it.isNotEmpty() }
                if (cells.isNotEmpty()) block(cells.joinToString(", "))
                continue
            }

            val heading = HEADING.matchEntire(line)
            if (heading != null) { block(heading.groupValues[1]); continue }

            line = line.replace(BLOCKQUOTE, "").trim()
            val bullet = BULLET.find(line)
            when {
                bullet != null -> {
                    line = line.substring(bullet.range.last + 1).replace(CHECKBOX, "").trim()
                    if (line.isNotEmpty()) block(line)
                }
                NUMBERED.containsMatchIn(line) -> block(line)
                line.isNotEmpty() -> out += line
            }
        }
        return out.joinToString("\n")
    }

    /** Adds a full stop so the voice pauses at the end of headings, list items and table rows. */
    private fun endSentence(line: String): String {
        val t = line.trimEnd()
        if (t.isEmpty()) return t
        val last = t.last()
        return if (last.isLetterOrDigit() || last in "\"'\u201D\u2019)*_`]") "$t." else t
    }

    // ---------------- Inline: markup and symbols ----------------

    private fun cleanInline(input: String): String {
        var t = input
        t = t.replace(IMAGE) { it.groupValues[1] }
        t = t.replace(INLINE_LINK) { it.groupValues[1] }
        t = t.replace(REF_LINK) { it.groupValues[1] }
        t = t.replace(AUTOLINK) { spokenUrl(it.groupValues[1]) }
        t = t.replace(URL) { m ->
            val url = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', '\'', '"')
            spokenUrl(url) + m.value.substring(url.length)
        }
        t = t.replace(FOOTNOTE_REF, "")
        t = t.replace(CITATION, "")
        t = t.replace(SUPERSCRIPT_REF, "")

        // Emphasis and code markers: keep the words, drop the symbols.
        t = t.replace(INLINE_CODE) { it.groupValues[1] }
        repeat(2) { // handles nesting like ***bold italic***
            t = t.replace(BOLD_STARS) { it.groupValues[1] }
            t = t.replace(BOLD_UNDERSCORES) { it.groupValues[1] }
            t = t.replace(ITALIC_STAR) { it.groupValues[1] }
            t = t.replace(ITALIC_UNDERSCORE) { it.groupValues[1] }
            t = t.replace(STRIKE) { it.groupValues[1] }
            t = t.replace(HIGHLIGHT) { it.groupValues[1] }
        }

        // Backslash escapes (\*  \_  \#) keep the character; any other backslash is noise.
        t = t.replace(ESCAPED) { it.groupValues[1] }
        t = t.replace('\\', ' ')

        // Symbols a reader would say as words.
        t = t.replace(ARROW, " to ")
        t = t.replace(Regex("\\s&\\s|\\s&amp;\\s"), " and ")
        t = t.replace(Regex("(?i)\\bw/o\\b"), "without")
        t = t.replace(Regex("(?i)(?<=\\s|^)w/(?=\\s)"), "with")
        t = t.replace(Regex("#(?=\\d)"), "number ")
        t = t.replace(SLASH_BETWEEN_WORDS, " ")

        // Number ranges: "1990–2000" -> "1990 to 2000"
        t = t.replace(Regex("(?<=\\d)\\s*\u2013\\s*(?=\\d)"), " to ")
        // Dashes used as pauses: "word -- word", "word — word" -> "word, word"
        t = t.replace(DASH_PAUSE, ", ")

        // Runs of punctuation: "!!!" -> "!", "?!?" -> "?", "......" -> "...", ",," -> ","
        t = t.replace(Regex("[?!]*\\?[?!]*"), "?").replace(Regex("!{2,}"), "!")
        t = t.replace(Regex("\\.{4,}|\u2026+"), "...")
        t = t.replace(Regex(",{2,}"), ",").replace(Regex(";{2,}"), ";").replace(Regex(":{2,}"), ":")

        // Runs of decorative symbols anywhere in a line: "=====", "~~~", "***", "-----", "+++"
        t = t.replace(SYMBOL_RUN, " ")

        // Snake_case and stray underscores read as spaces.
        t = t.replace('_', ' ')

        // Emoji, pictographs, box drawing, bullets and other visual-only characters.
        t = t.replace(PICTOGRAPHS, " ")

        // Remaining markup characters that have no spoken meaning.
        t = t.replace(STRAY_SYMBOLS, " ")
        t = t.replace(Regex("(?<=\\s|^)#+(?=\\s|$)"), " ")
        t = t.replace(Regex("\\(\\s*\\)|\\[\\s*]|\\{\\s*}"), " ")
        return t
    }

    /** "https://www.example.com/a/b?c=d" -> "example.com", which is what a person would say. */
    private fun spokenUrl(url: String): String {
        val host = url.replace(Regex("(?i)^[a-z][a-z0-9+.-]*://"), "")
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfter('@').substringBefore(':')
            .removePrefix("www.").removePrefix("WWW.")
        return if (host.contains('.')) host else "link"
    }

    // ---------------- Final whitespace and punctuation tidy ----------------

    private fun tidy(input: String): String = input
        .split('\n')
        .joinToString("\n") { line ->
            line.replace(Regex("[ \\t]+"), " ")
                .replace(Regex(" +([.,;:!?)])"), "$1")
                .replace(Regex("([(\\[]) +"), "$1")
                .replace(Regex("([,;:])(?:\\s*[,;:])+"), "$1")      // ", ," -> ","
                .replace(Regex("[,;:]\\s*([.!?])"), "$1")            // ", ." -> "."
                .replace(Regex("([.!?])\\s*[,;:]"), "$1")            // ". ," -> "."
                .replace(Regex("(?<!\\.)\\.\\s*\\.(?!\\.)"), ".")    // ". ." -> "."
                .trim()
                .trimStart(',', ';', ':', '.', ' ')
                .let { if (it.any { c -> c.isLetterOrDigit() }) it else "" }
        }
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun decodeEntities(s: String): String = s
        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&mdash;", " \u2014 ").replace("&ndash;", " \u2013 ")
        .replace("&hellip;", "...")
        .replace("&amp;", "&")

    // ---------------- Patterns ----------------

    private val INVISIBLE = Regex("[\\u00AD\\u200B-\\u200F\\u2060-\\u2064\\uFEFF\\uFE00-\\uFE0F]")
    private val FENCED_CODE = Regex("(?ms)^[ \\t]*(```|~~~).*?^[ \\t]*\\1[^\\n]*$")
    private val HTML_COMMENT = Regex("(?s)<!--.*?-->")
    private val HTML_BREAK = Regex("(?i)<br\\s*/?>|</p>|</div>|</li>|</h[1-6]>")
    private val HTML_TAG = Regex("</?[a-zA-Z][a-zA-Z0-9-]*(\\s[^<>]*)?/?>")

    private val RULE_LINE = Regex("^([-*_=~#+.\u2022\u00B7\u2014\u2013\u2500\u2550]\\s*){3,}$")
    private val PAGE_NUMBER_LINE = Regex("(?i)^(page\\s+)?\\d{1,4}(\\s*(of|/)\\s*\\d{1,4})?$")
    private val LINK_DEFINITION = Regex("^\\[[^\\]]+]:\\s*\\S+.*$")
    private val TABLE_SEPARATOR = Regex("^[|:\\-\\s+]+$")
    private val HEADING = Regex("^#{1,6}\\s+(.*?)\\s*#*\\s*$")
    private val BLOCKQUOTE = Regex("^(>\\s?)+")
    private val BULLET = Regex("^([-*+\u2022\u25CF\u25CB\u25A0\u25A1\u25AA\u25AB\u25E6\u2023\u2043\u25BA\u25B6\u27A4\u2192\u2713\u2714\u2717\u2718\u2610\u2611\u2612\u2605\u2606\u2666\u25C6\u25C7\u00B7]+)\\s+")
    private val NUMBERED = Regex("^(\\d{1,3}|[a-zA-Z]|[ivxIVX]{1,4})[.)]\\s+")
    private val CHECKBOX = Regex("^\\[[ xX]]\\s*")

    private val IMAGE = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
    private val INLINE_LINK = Regex("\\[([^\\]]+)]\\((?:[^()]|\\([^)]*\\))*\\)")
    private val REF_LINK = Regex("\\[([^\\]]+)]\\[[^\\]]*]")
    private val AUTOLINK = Regex("<((?:https?|ftp)://[^>\\s]+)>")
    private val URL = Regex("(?i)\\b(?:https?://|ftp://|www\\.)[^\\s<>\"]+")
    private val FOOTNOTE_REF = Regex("\\[\\^[^\\]]+]")
    private val CITATION = Regex("\\[\\s*\\d+(?:\\s*[,\u2013-]\\s*\\d+)*\\s*]")
    private val SUPERSCRIPT_REF = Regex("(?<=\\p{L}{3}[.,;:]?)[\u00B9\u00B2\u00B3\u2070\u2074-\u2079]+")

    private val INLINE_CODE = Regex("`+([^`]+?)`+")
    private val BOLD_STARS = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*")
    private val BOLD_UNDERSCORES = Regex("(?<![\\p{L}\\p{N}])__(?=\\S)(.+?)(?<=\\S)__(?![\\p{L}\\p{N}])")
    private val ITALIC_STAR = Regex("(?<![*\\p{L}\\p{N}])\\*(?=\\S)([^*\\n]+?)(?<=\\S)\\*(?!\\*)")
    private val ITALIC_UNDERSCORE = Regex("(?<![\\p{L}\\p{N}_])_(?=\\S)([^_\\n]+?)(?<=\\S)_(?![\\p{L}\\p{N}_])")
    private val STRIKE = Regex("~~(?=\\S)(.+?)(?<=\\S)~~")
    private val HIGHLIGHT = Regex("==(?=\\S)(.+?)(?<=\\S)==")
    private val ESCAPED = Regex("\\\\([\\\\`*_{}\\[\\]()#+\\-.!|~<>=])")

    private val ARROW = Regex("\\s*(?:->|=>|-->|==>|<-|<=(?=\\s)|[\u2190-\u21FF\u27F5-\u27FF\u2794\u279C\u27A1\u27A4])\\s*")
    private val SLASH_BETWEEN_WORDS = Regex("(?<=\\p{L}{2})/(?=\\p{L}{2})")
    private val DASH_PAUSE = Regex("\\s+(?:-{2,3}|\u2014|\u2013|\u2015)\\s+|(?<=\\p{L})\\s+-\\s+(?=\\p{L})|(?<=\\p{L})(?:--|\u2014)(?=\\p{L})")
    private val SYMBOL_RUN = Regex("([*=~^#_|<>`\\-])\\1{1,}")
    private val PICTOGRAPHS = Regex("[\\p{So}\\p{Cs}\\p{Co}&&[^\u00A9\u00AE\u2122\u00B0\u2116]]|[\u2500-\u259F\u25A0-\u25FF\u2022\u2023\u2043\u00B7]")
    private val STRAY_SYMBOLS = Regex("[*~^|`{}\\[\\]]|(?<=\\S)[<>]|[<>](?=\\S)")
}
