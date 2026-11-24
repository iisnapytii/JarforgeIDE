package com.jarforge.ide.feature.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.SpanStyle

@OptIn(ExperimentalFoundationApi::class)
object GradleScriptHighlightTransformation : OutputTransformation {
    private val styles = mapOf(
        GradleTokenKind.COMMENT to SpanStyle(color = SyntaxColors.Comment),
        GradleTokenKind.STRING to SpanStyle(color = SyntaxColors.StringLiteral),
        GradleTokenKind.NUMBER to SpanStyle(color = SyntaxColors.Number),
        GradleTokenKind.KEYWORD to SpanStyle(color = SyntaxColors.Keyword),
        GradleTokenKind.BOOLEAN to SpanStyle(color = SyntaxColors.Keyword),
        GradleTokenKind.NULL to SpanStyle(color = SyntaxColors.Annotation),
        GradleTokenKind.PUNCTUATION to SpanStyle(color = SyntaxColors.Operator),
        GradleTokenKind.OPERATOR to SpanStyle(color = SyntaxColors.Operator),
        GradleTokenKind.KEY to SpanStyle(color = SyntaxColors.Key)
    )

    override fun TextFieldBuffer.transformOutput() {
        if (length == 0 || length > GradleSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH) return
        GradleSyntaxHighlighter.tokenize(asCharSequence(), GradleFileMode.SCRIPT) { start, end, kind ->
            addStyle(styles.getValue(kind), start, end)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
object GradlePropertiesHighlightTransformation : OutputTransformation {
    private val styles = mapOf(
        GradleTokenKind.COMMENT to SpanStyle(color = SyntaxColors.Comment),
        GradleTokenKind.STRING to SpanStyle(color = SyntaxColors.StringLiteral),
        GradleTokenKind.NUMBER to SpanStyle(color = SyntaxColors.Number),
        GradleTokenKind.BOOLEAN to SpanStyle(color = SyntaxColors.Keyword),
        GradleTokenKind.NULL to SpanStyle(color = SyntaxColors.Annotation),
        GradleTokenKind.KEY to SpanStyle(color = SyntaxColors.Key),
        GradleTokenKind.OPERATOR to SpanStyle(color = SyntaxColors.Operator)
    )

    override fun TextFieldBuffer.transformOutput() {
        if (length == 0 || length > GradleSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH) return
        GradleSyntaxHighlighter.tokenize(asCharSequence(), GradleFileMode.PROPERTIES) { start, end, kind ->
            addStyle(styles.getValue(kind), start, end)
        }
    }
}
