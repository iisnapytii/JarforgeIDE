package com.jarforge.ide.feature.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle

/** Kinds of highlighted tokens. Plain identifiers, punctuation and blanks have no kind. */
enum class JavaTokenKind {
    KEYWORD,
    TYPE,
    STRING,
    CHAR,
    COMMENT,
    NUMBER,
    OPERATOR,
    ANNOTATION
}

/**
 * Pure Java tokenizer used for syntax highlighting. No Compose or Android types.
 *
 * One forward pass over the text, O(length), no backtracking. It reports only the ranges
 * that get a color; everything between them is left plain. Ranges are reported in
 * ascending order, never overlap, are never empty and always lie inside the text.
 *
 * It is a lexer, not a parser, and it never fails: broken code (an unterminated string, an
 * unclosed comment) only affects the broken token. A string or character literal ends at the end
 * of its line, so one missing quote cannot color the rest of the file. Block comments and text
 * blocks (""") that are not closed run to the end of the text.
 *
 * Identifiers are classified without any context:
 * - reserved words and literals (true, false, null) are keywords; `var` too;
 * - primitive types and `void` are types;
 * - a name that starts with an uppercase letter and contains a lowercase letter
 *   (String, ItemStack, Map) is a type; ALL_CAPS constants are not;
 * - a few well-known all-caps types (UUID, URL, URI) are types;
 * - everything else (variables, methods, fields) stays plain.
 */
object JavaSyntaxHighlighter {

    /**
     * Longer texts are not highlighted at all, so typing in a very large file stays instant.
     * The editor itself still works with them normally.
     */
    const val MAX_HIGHLIGHT_LENGTH = 100_000

    private val KEYWORDS: Set<String> = hashSetOf(
        "abstract", "assert", "break", "case", "catch", "class", "const", "continue",
        "default", "do", "else", "enum", "extends", "final", "finally", "for", "goto",
        "if", "implements", "import", "instanceof", "interface", "native", "new",
        "package", "private", "protected", "public", "return", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient",
        "try", "volatile", "while", "var", "true", "false", "null"
    )

    private val PRIMITIVE_TYPES: Set<String> = hashSetOf(
        "boolean", "byte", "char", "short", "int", "long", "float", "double", "void"
    )

    private val UPPERCASE_TYPES: Set<String> = hashSetOf("UUID", "URL", "URI")

    private const val OPERATOR_CHARS = "+-*/%=<>!&|^~?:"

    /**
     * Finds all colored tokens of [text] and reports each one to [emit] as
     * (start, end, kind), where [start] is inclusive and [end] is exclusive.
     */
    fun tokenize(text: CharSequence, emit: (start: Int, end: Int, kind: JavaTokenKind) -> Unit) {
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            val next = if (i + 1 < n) text[i + 1] else '\u0000'
            when {
                // Line comment: up to, not including, the line break.
                c == '/' && next == '/' -> {
                    var j = i + 2
                    while (j < n && text[j] != '\n' && text[j] != '\r') j++
                    emit(i, j, JavaTokenKind.COMMENT)
                    i = j
                }

                // Block comment (also Javadoc). "/*/" does not close itself.
                c == '/' && next == '*' -> {
                    var end = n
                    var j = i + 2
                    while (j + 1 < n) {
                        if (text[j] == '*' && text[j + 1] == '/') {
                            end = j + 2
                            break
                        }
                        j++
                    }
                    emit(i, end, JavaTokenKind.COMMENT)
                    i = end
                }

                // Text block """ ... """ or a normal string literal.
                c == '"' -> {
                    val end = if (next == '"' && i + 2 < n && text[i + 2] == '"') {
                        scanTextBlock(text, i)
                    } else {
                        scanQuoted(text, i, '"')
                    }
                    emit(i, end, JavaTokenKind.STRING)
                    i = end
                }

                c == '\'' -> {
                    val end = scanQuoted(text, i, '\'')
                    emit(i, end, JavaTokenKind.CHAR)
                    i = end
                }

                isDigit(c) || (c == '.' && isDigit(next)) -> {
                    val end = scanNumber(text, i)
                    emit(i, end, JavaTokenKind.NUMBER)
                    i = end
                }

                c == '@' -> i = scanAnnotation(text, i, emit)

                Character.isJavaIdentifierStart(c) -> {
                    var j = i + 1
                    while (j < n && Character.isJavaIdentifierPart(text[j])) j++
                    val kind = classifyIdentifier(text, i, j)
                    if (kind != null) emit(i, j, kind)
                    i = j
                }

                OPERATOR_CHARS.indexOf(c) >= 0 -> {
                    var j = i + 1
                    while (j < n && OPERATOR_CHARS.indexOf(text[j]) >= 0) {
                        // A comment start ends the operator run.
                        if (text[j] == '/' && j + 1 < n && (text[j + 1] == '/' || text[j + 1] == '*')) break
                        j++
                    }
                    emit(i, j, JavaTokenKind.OPERATOR)
                    i = j
                }

                else -> i++
            }
        }
    }

    private fun isDigit(c: Char): Boolean = c in '0'..'9'

    private fun isHexDigit(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /**
     * End (exclusive) of a "..." or '...' literal starting at [start]. A backslash escapes the
     * next character, so \" and \\ work. The literal also ends at a line break.
     */
    private fun scanQuoted(text: CharSequence, start: Int, quote: Char): Int {
        val n = text.length
        var j = start + 1
        while (j < n) {
            val d = text[j]
            if (d == '\\') {
                // Never swallow a line break through an escape.
                j += if (j + 1 < n && text[j + 1] != '\n' && text[j + 1] != '\r') 2 else 1
                continue
            }
            if (d == quote) return j + 1
            if (d == '\n' || d == '\r') return j
            j++
        }
        return n
    }

    /** End (exclusive) of a text block starting with """ at [start]; the end of text if unclosed. */
    private fun scanTextBlock(text: CharSequence, start: Int): Int {
        val n = text.length
        var j = start + 3
        while (j < n) {
            val d = text[j]
            if (d == '\\') {
                j += 2
                continue
            }
            if (d == '"' && j + 2 < n && text[j + 1] == '"' && text[j + 2] == '"') return j + 3
            j++
        }
        return n
    }

    /** End (exclusive) of a Java numeric literal. Supports decimal and hexadecimal floats. */
    private fun scanNumber(text: CharSequence, start: Int): Int {
        val n = text.length
        var j = start

        // Hexadecimal literals. A hexadecimal floating-point literal requires a p/P exponent.
        if (text[j] == '0' && j + 1 < n && (text[j + 1] == 'x' || text[j + 1] == 'X')) {
            j += 2
            val beforeDot = j
            while (j < n && (isHexDigit(text[j]) || text[j] == '_')) j++
            val beforeDotEnd = j
            val hasBeforeDotDigits = beforeDotEnd > beforeDot
            var hasDot = false
            var hasAfterDotDigits = false
            if (j < n && text[j] == '.') {
                hasDot = true
                j++
                val afterDot = j
                while (j < n && (isHexDigit(text[j]) || text[j] == '_')) j++
                hasAfterDotDigits = j > afterDot
            }

            // p/P exponent: optional sign followed by decimal digits (underscores allowed).
            if (j < n && (text[j] == 'p' || text[j] == 'P') && (hasBeforeDotDigits || hasAfterDotDigits)) {
                var exponentEnd = j + 1
                if (exponentEnd < n && (text[exponentEnd] == '+' || text[exponentEnd] == '-')) exponentEnd++
                val exponentDigits = exponentEnd
                while (exponentEnd < n && (isDigit(text[exponentEnd]) || text[exponentEnd] == '_')) exponentEnd++
                if (exponentEnd > exponentDigits) {
                    j = exponentEnd
                    if (j < n && "fFdD".indexOf(text[j]) >= 0) j++
                    return j
                }
            }

            // Without p/P this is an integer only when there was no dot.
            if (!hasDot && hasBeforeDotDigits) {
                if (j < n && (text[j] == 'l' || text[j] == 'L')) j++
                return j
            }
            // Malformed hex-float: leave the dot/exponent for normal tokenization rather than
            // swallowing unrelated source. A valid hex prefix can still be highlighted.
            return if (hasBeforeDotDigits) beforeDotEnd else start + 2
        }

        // Binary integer literal.
        if (text[j] == '0' && j + 1 < n && (text[j + 1] == 'b' || text[j + 1] == 'B')) {
            j += 2
            while (j < n && (text[j] == '0' || text[j] == '1' || text[j] == '_')) j++
            if (j < n && (text[j] == 'l' || text[j] == 'L')) j++
            return j
        }

        val startsWithDot = text[j] == '.'
        if (startsWithDot) {
            j++
            while (j < n && (isDigit(text[j]) || text[j] == '_')) j++
        } else {
            while (j < n && (isDigit(text[j]) || text[j] == '_')) j++
            // A trailing dot is part of a floating-point literal too: 1. and 1.e3.
            if (j < n && text[j] == '.') {
                j++
                while (j < n && (isDigit(text[j]) || text[j] == '_')) j++
            }
        }

        // Decimal exponent; only consume it when at least one exponent digit exists.
        if (j < n && (text[j] == 'e' || text[j] == 'E')) {
            var exponentEnd = j + 1
            if (exponentEnd < n && (text[exponentEnd] == '+' || text[exponentEnd] == '-')) exponentEnd++
            val exponentDigits = exponentEnd
            while (exponentEnd < n && (isDigit(text[exponentEnd]) || text[exponentEnd] == '_')) exponentEnd++
            if (exponentEnd > exponentDigits) j = exponentEnd
        }

        if (j < n && "fFdD".indexOf(text[j]) >= 0) {
            j++
        } else if (!startsWithDot && j < n && (text[j] == 'l' || text[j] == 'L')) {
            j++
        }
        return j
    }

    /**
     * Handles '@' at [start]: an annotation (with a dotted name) or the `@interface` keyword.
     * A lone '@' is left plain. Returns the index to continue from.
     */
    private fun scanAnnotation(
        text: CharSequence,
        start: Int,
        emit: (Int, Int, JavaTokenKind) -> Unit
    ): Int {
        val n = text.length
        var j = start + 1
        if (j >= n || !Character.isJavaIdentifierStart(text[j])) return start + 1

        val nameStart = j
        while (j < n && Character.isJavaIdentifierPart(text[j])) j++
        if (j - nameStart == "interface".length && text.subSequence(nameStart, j).toString() == "interface") {
            emit(start, j, JavaTokenKind.KEYWORD)
            return j
        }
        // Qualified name: @net.example.Marker
        while (j + 1 < n && text[j] == '.' && Character.isJavaIdentifierStart(text[j + 1])) {
            j += 2
            while (j < n && Character.isJavaIdentifierPart(text[j])) j++
        }
        emit(start, j, JavaTokenKind.ANNOTATION)
        return j
    }

    /** Classifies the identifier [start, end). Returns null for a plain identifier. */
    private fun classifyIdentifier(text: CharSequence, start: Int, end: Int): JavaTokenKind? {
        val first = text[start]
        if (first in 'a'..'z') {
            // Reserved words are 2..12 lowercase letters; skip the lookup for everything else.
            val length = end - start
            if (length < 2 || length > 12) return null
            val word = text.subSequence(start, end).toString()
            return when {
                word in PRIMITIVE_TYPES -> JavaTokenKind.TYPE
                word in KEYWORDS -> JavaTokenKind.KEYWORD
                else -> null
            }
        }
        if (first in 'A'..'Z') {
            for (k in start + 1 until end) {
                if (text[k] in 'a'..'z') return JavaTokenKind.TYPE
            }
            if (end - start in 3..4 && text.subSequence(start, end).toString() in UPPERCASE_TYPES) {
                return JavaTokenKind.TYPE
            }
        }
        return null
    }
}

/** Colors of the syntax highlighting. Color only: no weight or slant, so text metrics never change. */
internal object SyntaxColors {
    val Keyword = Color(0xFFFF7B72)
    val Type = Color(0xFFFFA657)
    val StringLiteral = Color(0xFFA5D6FF)
    val Comment = Color(0xFF8B949E)
    val Number = Color(0xFF79C0FF)
    val Operator = Color(0xFF7EE787)
    val Annotation = Color(0xFFD2A8FF)
    val Key = Color(0xFF56D4DD)
    val Bold = Color(0xFFF2CC60)
    val Italic = Color(0xFFFFB3D9)
    val Link = Color(0xFF58A6FF)
    val Quote = Color(0xFF7D8590)
}

/**
 * Colors Java code in the editor. Purely visual: the styles are added to the displayed text
 * only, never to the text held by the TextFieldState, so what is saved, copied, undone or
 * indented is not affected. The text length and offsets stay the same.
 *
 * Runs whenever the displayed text is produced, so it has to stay cheap: one linear pass,
 * and nothing at all above [JavaSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH] characters.
 */
@OptIn(ExperimentalFoundationApi::class)
object JavaHighlightTransformation : OutputTransformation {

    private val keywordStyle = SpanStyle(color = SyntaxColors.Keyword)
    private val typeStyle = SpanStyle(color = SyntaxColors.Type)
    private val stringStyle = SpanStyle(color = SyntaxColors.StringLiteral)
    private val commentStyle = SpanStyle(color = SyntaxColors.Comment)
    private val numberStyle = SpanStyle(color = SyntaxColors.Number)
    private val operatorStyle = SpanStyle(color = SyntaxColors.Operator)
    private val annotationStyle = SpanStyle(color = SyntaxColors.Annotation)

    private fun styleOf(kind: JavaTokenKind): SpanStyle = when (kind) {
        JavaTokenKind.KEYWORD -> keywordStyle
        JavaTokenKind.TYPE -> typeStyle
        JavaTokenKind.STRING, JavaTokenKind.CHAR -> stringStyle
        JavaTokenKind.COMMENT -> commentStyle
        JavaTokenKind.NUMBER -> numberStyle
        JavaTokenKind.OPERATOR -> operatorStyle
        JavaTokenKind.ANNOTATION -> annotationStyle
    }

    override fun TextFieldBuffer.transformOutput() {
        if (length == 0 || length > JavaSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH) return
        JavaSyntaxHighlighter.tokenize(asCharSequence()) { start, end, kind ->
            addStyle(styleOf(kind), start, end)
        }
    }
}
