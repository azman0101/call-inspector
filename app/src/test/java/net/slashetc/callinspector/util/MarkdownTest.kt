package net.slashetc.callinspector.util

import net.slashetc.callinspector.util.Markdown.Block
import net.slashetc.callinspector.util.Markdown.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MarkdownTest {

    @Test
    fun `headings, rule, paragraphs and lists`() {
        val blocks = Markdown.parse(
            """
            # Title

            First line
            continued here.

            ---

            ## 1. Section

            1. One
            2. Two
               continued
            - Bullet
            """.trimIndent()
        )
        assertEquals(
            listOf(
                Block.Heading(1, listOf(Span("Title"))),
                Block.Paragraph(listOf(Span("First line continued here."))),
                Block.Rule,
                Block.Heading(2, listOf(Span("1. Section"))),
                Block.ListItem(1, listOf(Span("One"))),
                Block.ListItem(2, listOf(Span("Two continued"))),
                Block.ListItem(null, listOf(Span("Bullet"))),
            ),
            blocks
        )
    }

    @Test
    fun `bold, italic and code spans`() {
        assertEquals(
            listOf(
                Span("L'app "),
                Span("Info", bold = true),
                Span(" lit "),
                Span("READ_CALL_LOG", code = true),
                Span(" dans "),
                Span("Observatoire", italic = true),
                Span("."),
            ),
            Markdown.parseInline("L'app **Info** lit `READ_CALL_LOG` dans *Observatoire*.")
        )
    }

    @Test
    fun `bold inside italic`() {
        assertEquals(
            listOf(Span("a ", italic = true), Span("b", bold = true, italic = true), Span(" c", italic = true)),
            Markdown.parseInline("*a **b** c*")
        )
    }

    @Test
    fun `unclosed markers stay literal`() {
        assertEquals(listOf(Span("2 * 3 = **6 and `x")), Markdown.parseInline("2 * 3 = **6 and `x"))
    }

    @Test
    fun `an italic line is a paragraph, not a bullet`() {
        assertEquals(
            listOf(Block.Paragraph(listOf(Span("Dernière mise à jour : 26 septembre 2026", italic = true)))),
            Markdown.parse("*Dernière mise à jour : 26 septembre 2026*")
        )
    }

    @Test
    fun `the bundled terms parse without leftover markup`() {
        // Unit tests run from the app module: this is the file Gradle bundles as the CGU asset.
        val blocks = Markdown.parse(File("../legal/CGU.md").readText())
        assertTrue(blocks.first() is Block.Heading)
        val text = blocks.joinToString("\n") { block ->
            when (block) {
                is Block.Heading -> Markdown.plainText(block.spans)
                is Block.Paragraph -> Markdown.plainText(block.spans)
                is Block.ListItem -> Markdown.plainText(block.spans)
                Block.Rule -> ""
            }
        }
        assertFalse(text, text.contains("**") || text.contains('`') || text.contains("# "))
    }
}
