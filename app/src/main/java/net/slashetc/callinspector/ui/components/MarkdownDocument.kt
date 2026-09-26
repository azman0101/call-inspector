package net.slashetc.callinspector.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import net.slashetc.callinspector.util.Markdown

/** Renders a Markdown document parsed by [Markdown] with the Material 3 typography. */
@Composable
fun MarkdownDocument(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { Markdown.parse(markdown) }
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Markdown.Block.Heading -> Text(
                    text = block.spans.toAnnotatedString(codeBackground),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (block.level == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = if (block.level == 1) 0.dp else 4.dp)
                )
                is Markdown.Block.Paragraph -> Text(
                    text = block.spans.toAnnotatedString(codeBackground),
                    style = MaterialTheme.typography.bodyMedium
                )
                is Markdown.Block.ListItem -> Row {
                    Text(
                        text = block.number?.let { "$it." } ?: "•",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.widthIn(min = 20.dp)
                    )
                    Text(
                        text = block.spans.toAnnotatedString(codeBackground),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Markdown.Block.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

private fun List<Markdown.Span>.toAnnotatedString(codeBackground: Color): AnnotatedString = buildAnnotatedString {
    forEach { span ->
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
            background = if (span.code) codeBackground else Color.Unspecified
        )
        withStyle(style) { append(span.text) }
    }
}
