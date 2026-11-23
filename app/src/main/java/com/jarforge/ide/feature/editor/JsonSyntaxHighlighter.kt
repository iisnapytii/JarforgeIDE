package com.jarforge.ide.feature.editor


/** Token categories used by the JSON syntax highlighter. */
enum class JsonTokenKind {
    OBJECT_KEY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    PUNCTUATION
}

/**
 * A small, tolerant JSON tokenizer used only for visual syntax highlighting.
 *
 * It is deliberately not a validator. It scans once from left to right, keeps container
 * context in an explicit stack (never recursive), and emits ordered, non-overlapping ranges.
 * Object property names are recognized only in an object's key slot and only when a closing
 * quote is followed by optional whitespace and a colon. Strings that are not closed stop at
 * the first CR/LF or EOF, so one malformed string cannot color the rest of the document.
 */
object JsonSyntaxHighlighter {
    const val MAX_HIGHLIGHT_LENGTH = 100_000
    const val MAX_NESTING_DEPTH = 256

    private enum class Container { OBJECT, ARRAY }
    private enum class State {
        KEY_OR_END,
        EXPECT_COLON,
        EXPECT_VALUE,
        VALUE_OR_END,
        AFTER_VALUE
    }

    private data class Frame(
        val container: Container,
        var state: State
    )

    private data class StringScan(
        val end: Int,
        val closed: Boolean
    )

    private data class NumberScan(
        val end: Int,
        val valid: Boolean
    )

    /**
     * Emits (start, end, kind) ranges. Inputs above [MAX_HIGHLIGHT_LENGTH] are left uncolored.
     * Runtime is O(n), with at most [MAX_NESTING_DEPTH] retained container frames.
     */
    fun tokenize(
        text: CharSequence,
        emit: (start: Int, end: Int, kind: JsonTokenKind) -> Unit
    ) {
        val n = text.length
        if (n == 0 || n > MAX_HIGHLIGHT_LENGTH) return

        val stack = ArrayList<Frame>(16)
        // When the explicit stack is full, count nested delimiters without growing it.
        // The enclosing frame has already consumed the overflowing container as one value.
        var overflowDepth = 0
        var i = 0

        fun consumeValue() {
            if (overflowDepth != 0) return
            val frame = stack.lastOrNull() ?: return
            when {
                frame.container == Container.OBJECT && frame.state == State.EXPECT_VALUE ->
                    frame.state = State.AFTER_VALUE
                frame.container == Container.ARRAY && frame.state == State.VALUE_OR_END ->
                    frame.state = State.AFTER_VALUE
            }
        }

        while (i < n) {
            val c = text[i]

            if (c.isWhitespace()) {
                i++
                continue
            }

            if (c == '"') {
                val scan = scanString(text, i)
                val keySlot = overflowDepth == 0 &&
                    stack.lastOrNull()?.let {
                        it.container == Container.OBJECT && it.state == State.KEY_OR_END
                    } == true
                val isKey = scan.closed && keySlot && followedByColon(text, scan.end)
                emit(i, scan.end, if (isKey) JsonTokenKind.OBJECT_KEY else JsonTokenKind.STRING)
                if (isKey) {
                    stack.lastOrNull()?.state = State.EXPECT_COLON
                } else {
                    consumeValue()
                }
                i = scan.end
                continue
            }

            if (c == '{' || c == '[') {
                emit(i, i + 1, JsonTokenKind.PUNCTUATION)
                consumeValue()
                if (overflowDepth > 0) {
                    overflowDepth++
                } else if (stack.size < MAX_NESTING_DEPTH) {
                    stack.add(
                        Frame(
                            container = if (c == '{') Container.OBJECT else Container.ARRAY,
                            state = if (c == '{') State.KEY_OR_END else State.VALUE_OR_END
                        )
                    )
                } else {
                    overflowDepth = 1
                }
                i++
                continue
            }

            if (c == '}' || c == ']') {
                emit(i, i + 1, JsonTokenKind.PUNCTUATION)
                if (overflowDepth > 0) {
                    overflowDepth--
                } else {
                    val expected = if (c == '}') Container.OBJECT else Container.ARRAY
                    if (stack.lastOrNull()?.container == expected) {
                        stack.removeAt(stack.lastIndex)
                    }
                }
                i++
                continue
            }

            if (c == ':' || c == ',') {
                emit(i, i + 1, JsonTokenKind.PUNCTUATION)
                if (overflowDepth == 0) {
                    val frame = stack.lastOrNull()
                    if (frame != null) {
                        if (c == ':' && frame.container == Container.OBJECT &&
                            frame.state == State.EXPECT_COLON
                        ) {
                            frame.state = State.EXPECT_VALUE
                        } else if (c == ',' && frame.state == State.AFTER_VALUE) {
                            frame.state = if (frame.container == Container.OBJECT) {
                                State.KEY_OR_END
                            } else {
                                State.VALUE_OR_END
                            }
                        }
                    }
                }
                i++
                continue
            }

            if (c == 't' && matchesLiteral(text, i, "true")) {
                emit(i, i + 4, JsonTokenKind.BOOLEAN)
                consumeValue()
                i += 4
                continue
            }
            if (c == 'f' && matchesLiteral(text, i, "false")) {
                emit(i, i + 5, JsonTokenKind.BOOLEAN)
                consumeValue()
                i += 5
                continue
            }
            if (c == 'n' && matchesLiteral(text, i, "null")) {
                emit(i, i + 4, JsonTokenKind.NULL)
                consumeValue()
                i += 4
                continue
            }

            if (c == '-' || c == '+' || c == '.' || c in '0'..'9') {
                val number = scanNumber(text, i)
                if (number.valid) {
                    emit(i, number.end, JsonTokenKind.NUMBER)
                    consumeValue()
                }
                i = number.end
                continue
            }

            i++
        }
    }

    private fun scanString(text: CharSequence, start: Int): StringScan {
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '"' -> return StringScan(i + 1, true)
                '\\' -> {
                    // An escape must not carry a broken string across a physical line.
                    if (i + 1 >= text.length) return StringScan(text.length, false)
                    if (text[i + 1] == '\n' || text[i + 1] == '\r') {
                        return StringScan(i + 1, false)
                    }
                    i += 2
                }
                '\n', '\r' -> return StringScan(i, false)
                else -> i++
            }
        }
        return StringScan(text.length, false)
    }

    private fun followedByColon(text: CharSequence, from: Int): Boolean {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i < text.length && text[i] == ':'
    }

    private fun matchesLiteral(text: CharSequence, start: Int, literal: String): Boolean {
        val end = start + literal.length
        if (end > text.length) return false
        for (offset in literal.indices) {
            if (text[start + offset] != literal[offset]) return false
        }
        return isValueBoundary(text, end)
    }

    private fun isValueBoundary(text: CharSequence, index: Int): Boolean =
        index >= text.length || text[index].isWhitespace() || text[index] == ',' ||
            text[index] == ']' || text[index] == '}'

    /**
     * Scans one contiguous number-like run, then validates strict JSON number grammar.
     * Invalid runs are consumed too, preventing quadratic rescans of malformed long numbers.
     */
    private fun scanNumber(text: CharSequence, start: Int): NumberScan {
        val n = text.length
        var end = start
        while (end < n && (text[end] in '0'..'9' || text[end] == 'e' || text[end] == 'E' ||
                text[end] == '+' || text[end] == '-' || text[end] == '.')) {
            end++
        }

        var i = start
        if (i < end && text[i] == '-') i++
        if (i >= end) return NumberScan(end, false)

        if (text[i] == '0') {
            i++
            if (i < end && text[i] in '0'..'9') return NumberScan(end, false)
        } else if (text[i] in '1'..'9') {
            i++
            while (i < end && text[i] in '0'..'9') i++
        } else {
            return NumberScan(end, false)
        }

        if (i < end && text[i] == '.') {
            i++
            val fractionStart = i
            while (i < end && text[i] in '0'..'9') i++
            if (i == fractionStart) return NumberScan(end, false)
        }

        if (i < end && (text[i] == 'e' || text[i] == 'E')) {
            i++
            if (i < end && (text[i] == '+' || text[i] == '-')) i++
            val exponentStart = i
            while (i < end && text[i] in '0'..'9') i++
            if (i == exponentStart) return NumberScan(end, false)
        }

        return NumberScan(end, i == end && isValueBoundary(text, end))
    }
}
