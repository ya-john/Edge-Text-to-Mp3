package com.edgetts.mp3.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DocKind(val label: String) { Text("Text"), Markdown("Markdown"), Pdf("PDF") }

data class LoadedDocument(val name: String, val kind: DocKind, val text: String, val pages: Int? = null)

class DocumentReader(private val context: Context) {

    suspend fun load(uri: Uri): LoadedDocument = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: "document"
        val mime = context.contentResolver.getType(uri).orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase()
        val kind = when {
            mime == "application/pdf" || ext == "pdf" -> DocKind.Pdf
            ext in setOf("md", "markdown", "mdown", "mkd") || mime == "text/markdown" || mime == "text/x-markdown" -> DocKind.Markdown
            else -> DocKind.Text
        }
        when (kind) {
            DocKind.Pdf -> readPdf(uri, name)
            DocKind.Markdown -> LoadedDocument(name, kind, MarkdownToSpeech.convert(readString(uri)))
            DocKind.Text -> LoadedDocument(name, kind, readString(uri))
        }
    }

    private fun readString(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not open file")

    private fun readPdf(uri: Uri, name: String): LoadedDocument {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri) ?: error("Could not open PDF")
        input.use { stream ->
            PDDocument.load(stream).use { doc ->
                if (doc.isEncrypted && !doc.currentAccessPermission.canExtractContent()) {
                    error("This PDF is protected and does not allow text extraction.")
                }
                val raw = PDFTextStripper().apply { sortByPosition = true }.getText(doc)
                val text = cleanPdfText(raw)
                if (text.isBlank()) error("No selectable text found. Scanned PDFs need OCR first.")
                return LoadedDocument(name, DocKind.Pdf, text, doc.numberOfPages)
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        /** Re-flows PDF line breaks into sentences so the voice does not pause at every line end. */
        fun cleanPdfText(raw: String): String = raw
            .replace("\r\n", "\n")
            .replace(Regex("(\\p{L})-\\n(\\p{Ll})"), "$1$2")          // de-hyphenate "exam-\nple"
            .replace(Regex("[ \\t]*\\n[ \\t]*\\n\\s*"), "\u0000")     // protect paragraph breaks
            .replace(Regex("(?<=[.!?:\"\u201D])\\n"), "\u0000")       // a line ending a sentence ends a paragraph
            .replace(Regex("\\n(?=\\s*([\u2022\u25CF\\-*]|\\d+[.)])\\s)"), "\u0000") // bullet/numbered lines
            .replace('\n', ' ')
            .replace('\u0000', '\n')
            .replace(Regex("\\n{2,}"), "\n")
            .split('\n').joinToString("\n\n") { it.trim() }
            .trim()
    }
}
