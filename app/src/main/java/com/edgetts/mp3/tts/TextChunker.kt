package com.edgetts.mp3.tts

/**
 * Splits long text into pieces small enough for one Edge synthesis request.
 *
 * The service rejects SSML bodies much past 4 KB, so the budget is measured on the
 * XML-escaped UTF-8 bytes, not raw characters. Splits fall on the most natural boundary
 * available: paragraph, then sentence, then clause, then word, and only as a last resort mid-word.
 */
object TextChunker {
    const val DEFAULT_MAX_BYTES = 2000

    private val paragraphSplit = Regex("\\n\\s*\\n+")
    private val sentenceSplit = Regex("(?<=[.!?…][\"'”’)\\]]?)\\s+")
    private val clauseSplit = Regex("(?<=[,;:—–])\\s+")
    private val spaces = Regex("[ \\t\\u00A0]+")
    private val controlChars = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")
    private val speakable = Regex("[\\p{L}\\p{N}]")

    fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun cost(s: String): Int = escapeXml(s).toByteArray(Charsets.UTF_8).size

    /** Normalises whitespace and strips characters the service chokes on. */
    fun clean(text: String): String = text
        .replace("\r\n", "\n").replace('\r', '\n')
        .replace(controlChars, " ")
        .lines().joinToString("\n") { it.replace(spaces, " ").trim() }
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    fun chunk(text: String, maxBytes: Int = DEFAULT_MAX_BYTES): List<String> {
        val units = ArrayList<String>()
        for (para in clean(text).split(paragraphSplit)) {
            val p = para.replace('\n', ' ').trim()
            if (p.isEmpty()) continue
            for (sentence in p.split(sentenceSplit)) splitToFit(sentence.trim(), maxBytes, units)
            // Paragraph marker so packing keeps a pause between paragraphs.
            units += "\n"
        }

        val chunks = ArrayList<String>()
        val current = StringBuilder()
        var currentCost = 0
        for (unit in units) {
            if (unit == "\n") {
                if (current.isNotEmpty()) { current.append('\n'); currentCost += 1 }
                continue
            }
            val sep = if (current.isEmpty() || current.last() == '\n') 0 else 1
            val c = cost(unit)
            if (currentCost + sep + c > maxBytes && current.isNotEmpty()) {
                chunks += current.toString().trim()
                current.clear(); currentCost = 0
            }
            if (current.isNotEmpty() && current.last() != '\n') { current.append(' '); currentCost += 1 }
            current.append(unit); currentCost += c
        }
        if (current.isNotBlank()) chunks += current.toString().trim()
        // Chunks of pure punctuation make the service return no audio; drop them.
        return chunks.filter { speakable.containsMatchIn(it) }
    }

    private fun splitToFit(piece: String, maxBytes: Int, out: MutableList<String>) {
        if (piece.isEmpty()) return
        if (cost(piece) <= maxBytes) { out += piece; return }
        val clauses = piece.split(clauseSplit)
        if (clauses.size > 1) {
            packWords(clauses, maxBytes, out); return
        }
        val words = piece.split(' ')
        if (words.size > 1) {
            packWords(words, maxBytes, out); return
        }
        // A single enormous token (e.g. a base64 blob). Hard split by characters.
        var start = 0
        while (start < piece.length) {
            var end = minOf(piece.length, start + maxBytes / 6)
            while (end < piece.length && cost(piece.substring(start, end + 1)) <= maxBytes) end++
            out += piece.substring(start, end)
            start = end
        }
    }

    private fun packWords(parts: List<String>, maxBytes: Int, out: MutableList<String>) {
        val sb = StringBuilder()
        for (part in parts) {
            if (cost(part) > maxBytes) {
                if (sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
                splitToFit(part, maxBytes, out)
                continue
            }
            val candidate = if (sb.isEmpty()) part else "$sb $part"
            if (cost(candidate) > maxBytes) {
                out += sb.toString(); sb.clear(); sb.append(part)
            } else {
                sb.clear(); sb.append(candidate)
            }
        }
        if (sb.isNotEmpty()) out += sb.toString()
    }
}
