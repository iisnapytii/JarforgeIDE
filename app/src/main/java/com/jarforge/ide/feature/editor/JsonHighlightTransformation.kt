package com.jarforge.ide.feature.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.SpanStyle

@OptIn(ExperimentalFoundationApi::class)
object JsonHighlightTransformation : OutputTransformation {
    private val styles = mapOf(
        JsonTokenKind.OBJECT_KEY to SpanStyle(color = SyntaxColors.Key),
        JsonTokenKind.STRING to SpanStyle(color = SyntaxColors.StringLiteral),
        JsonTokenKind.NUMBER to SpanStyle(color = SyntaxColors.Number),
        JsonTokenKind.BOOLEAN to SpanStyle(color = SyntaxColors.Keyword),
        JsonTokenKind.NULL to SpanStyle(color = SyntaxColors.Annotation),
        JsonTokenKind.PUNCTUATION to SpanStyle(color = SyntaxColors.Operator)
    )

    override fun TextFieldBuffer.transformOutput() {
        if (length == 0 || length > JsonSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH) return
        JsonSyntaxHighlighter.tokenize(asCharSequence()) { start, end, kind ->
            addStyle(styles.getValue(kind), start, end)
        }
    }
}
