package com.edgetts.mp3.io

import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * Converts Markdown into text meant to be heard: formatting marks, link URLs, raw HTML and
 * code blocks are dropped; headings and list items become sentences so the voice pauses.
 */
object MarkdownToSpeech {
    private val parser = Parser.builder().build()

    fun convert(markdown: String): String {
        val out = StringBuilder()
        parser.parse(markdown).accept(Visitor(out))
        return out.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private class Visitor(private val out: StringBuilder) : AbstractVisitor() {
        private fun endBlock(addStop: Boolean) {
            if (addStop) {
                val trimmed = out.trimEnd()
                if (trimmed.isNotEmpty() && trimmed.last() !in ".!?:;,") {
                    out.setLength(trimmed.length); out.append('.')
                }
            }
            out.append("\n\n")
        }

        override fun visit(heading: Heading) { visitChildren(heading); endBlock(true) }
        override fun visit(paragraph: Paragraph) {
            visitChildren(paragraph)
            // List items wrap their text in paragraphs; give each item its own pause.
            endBlock(paragraph.parent is ListItem)
        }
        override fun visit(listItem: ListItem) { visitChildren(listItem) }
        override fun visit(bulletList: BulletList) { visitChildren(bulletList) }
        override fun visit(orderedList: OrderedList) { visitChildren(orderedList) }
        override fun visit(blockQuote: BlockQuote) { visitChildren(blockQuote) }
        override fun visit(text: Text) { out.append(text.literal) }
        override fun visit(code: Code) { out.append(code.literal) }
        override fun visit(softLineBreak: SoftLineBreak) { out.append(' ') }
        override fun visit(hardLineBreak: HardLineBreak) { out.append(' ') }
        override fun visit(image: Image) { visitChildren(image) } // alt text only
        override fun visit(fencedCodeBlock: FencedCodeBlock) {}
        override fun visit(indentedCodeBlock: IndentedCodeBlock) {}
        override fun visit(htmlBlock: HtmlBlock) {}
        override fun visit(htmlInline: HtmlInline) {}
        override fun visit(thematicBreak: ThematicBreak) { out.append("\n\n") }
        override fun visitChildren(parent: Node) { super.visitChildren(parent) }
    }
}
