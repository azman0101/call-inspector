package net.slashetc.callinspector.util

/**
 * Minimal Markdown parser for the documents bundled with the app (legal/CGU.md): headings, paragraphs,
 * numbered and bulleted lists, horizontal rules, and inline **bold**, *italic* and `code`.
 * Anything else is kept as plain text. Pure Kotlin, so it is unit-tested on the JVM.
 */
object Markdown {

    data class Span(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false)

    sealed interface Block {
        data class Heading(val level: Int, val spans: List<Span>) : Block
        data class Paragraph(val spans: List<Span>) : Block
        /** [number] is null for a bulleted item. */
        data class ListItem(val number: Int?, val spans: List<Span>) : Block
        data object Rule : Block
    }

    private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")
    private val orderedItemRegex = Regex("^\\s*(\\d+)\\.\\s+(.*)$")
    private val bulletItemRegex = Regex("^\\s*[-*+]\\s+(.*)$")
    private val ruleRegex = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")

    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = StringBuilder()
        // Text of the list item being read, which may continue on indented lines.
        var item: Pair<Int?, StringBuilder>? = null

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) blocks += Block.Paragraph(parseInline(paragraph.toString()))
            paragraph.clear()
        }

        fun flushItem() {
            item?.let { (number, text) -> blocks += Block.ListItem(number, parseInline(text.toString())) }
            item = null
        }

        for (line in markdown.lines()) {
            val trimmed = line.trim()
            val heading = headingRegex.find(trimmed)
            val ordered = orderedItemRegex.find(line)
            val bullet = bulletItemRegex.find(line)
            when {
                trimmed.isEmpty() -> { flushParagraph(); flushItem() }
                ruleRegex.matches(line) -> { flushParagraph(); flushItem(); blocks += Block.Rule }
                heading != null -> {
                    flushParagraph(); flushItem()
                    blocks += Block.Heading(heading.groupValues[1].length, parseInline(heading.groupValues[2].trim()))
                }
                ordered != null -> {
                    flushParagraph(); flushItem()
                    item = ordered.groupValues[1].toInt() to StringBuilder(ordered.groupValues[2].trim())
                }
                bullet != null -> {
                    flushParagraph(); flushItem()
                    item = null to StringBuilder(bullet.groupValues[1].trim())
                }
                item != null && line.startsWith(" ") -> item!!.second.append(' ').append(trimmed)
                else -> {
                    flushItem()
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(trimmed)
                }
            }
        }
        flushParagraph()
        flushItem()
        return blocks
    }

    /** Splits [text] on `code`, **bold** and *italic* markers; an unclosed marker stays literal. */
    fun parseInline(text: String, bold: Boolean = false, italic: Boolean = false): List<Span> {
        val spans = mutableListOf<Span>()
        val plain = StringBuilder()

        fun flushPlain() {
            if (plain.isNotEmpty()) spans += Span(plain.toString(), bold = bold, italic = italic)
            plain.clear()
        }

        var i = 0
        while (i < text.length) {
            when {
                text[i] == '`' && text.indexOf('`', i + 1) > i -> {
                    val end = text.indexOf('`', i + 1)
                    flushPlain()
                    spans += Span(text.substring(i + 1, end), bold = bold, italic = italic, code = true)
                    i = end + 1
                }
                text.startsWith("**", i) && text.indexOf("**", i + 2) > i + 2 -> {
                    val end = text.indexOf("**", i + 2)
                    flushPlain()
                    spans += parseInline(text.substring(i + 2, end), bold = true, italic = italic)
                    i = end + 2
                }
                text[i] == '*' && !text.startsWith("**", i) && closingItalic(text, i + 1) > i + 1 -> {
                    val end = closingItalic(text, i + 1)
                    flushPlain()
                    spans += parseInline(text.substring(i + 1, end), bold = bold, italic = true)
                    i = end + 1
                }
                else -> {
                    plain.append(text[i])
                    i++
                }
            }
        }
        flushPlain()
        return spans
    }

    // A single '*' closing an italic run, skipping '**' pairs (bold inside italic).
    private fun closingItalic(text: String, from: Int): Int {
        var j = from
        while (j < text.length) {
            if (text.startsWith("**", j)) {
                j += 2
            } else if (text[j] == '*') {
                return j
            } else {
                j++
            }
        }
        return -1
    }

    /** Plain text of [spans], without markup. */
    fun plainText(spans: List<Span>): String = spans.joinToString("") { it.text }
}
