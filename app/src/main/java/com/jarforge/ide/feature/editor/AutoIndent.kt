package com.jarforge.ide.feature.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.TextRange

/**
 * Replace [start, end) of the text with [text], then put the cursor at [cursor].
 * Offsets are in the text that already contains the typed '\n'.
 */
data class AutoIndentEdit(
    val start: Int,
    val end: Int,
    val text: String,
    val cursor: Int
)

/**
 * Pure logic of auto-indent. No Compose or Android types.
 *
 * Called with the text AFTER the user typed a single '\n' at [computeEnter]'s `newlinePos`.
 * It never looks at more than [MAX_SCAN] characters in one direction and gives up
 * (returns null, so the plain newline stays) when a limit is hit.
 *
 * '\r' is never treated as blank space and never as '}', so CRLF files cannot hide or fake
 * a closing brace. In a CRLF file the inserted line breaks are "\r\n".
 */
object AutoIndent {

    private const val MAX_SCAN = 10_000
    private const val MAX_INDENT = 200
    private const val TAB = "\t"

    private fun isBlank(c: Char): Boolean = c == ' ' || c == '\t'

    /**
     * Returns the edit that turns the typed '\n' into a line break followed by the right
     * indentation, or null when the text should stay as typed.
     */
    fun computeEnter(text: CharSequence, newlinePos: Int): AutoIndentEdit? {
        val length = text.length
        if (newlinePos < 0 || newlinePos >= length || text[newlinePos] != '\n') return null
        // The typed '\n' completed an existing "\r\n" (cursor was between them): leave it.
        if (newlinePos > 0 && text[newlinePos - 1] == '\r') return null

        // Start of the line the cursor was on. Only '\n' ends a line.
        var lineStart = newlinePos
        var scanned = 0
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
            if (++scanned > MAX_SCAN) return null
        }

        // Leading blanks before the cursor: the indentation to inherit.
        var baseEnd = lineStart
        while (baseEnd < newlinePos && isBlank(text[baseEnd])) baseEnd++
        val base = text.subSequence(lineStart, baseEnd).toString()
        val beforeIsBlank = baseEnd == newlinePos

        // Last visible character before the cursor (trailing blanks ignored).
        var last = newlinePos - 1
        while (last >= lineStart && isBlank(text[last])) last--
        val inLineComment = isInsideComment(text, lineStart, newlinePos)
        val opensBlock = !inLineComment && last >= lineStart && text[last] == '{'

        // First visible character after the cursor on the same line. Only blanks are
        // skipped, so '\r' or '\n' stop the look-ahead and are never mistaken for '}'.
        val afterStart = newlinePos + 1
        var closeIdx = afterStart
        scanned = 0
        while (closeIdx < length && isBlank(text[closeIdx])) {
            closeIdx++
            if (++scanned > MAX_SCAN) return null
        }
        val closesBlock = !inLineComment && closeIdx < length && text[closeIdx] == '}'
        val blankAfterCursor = afterStart < length && isBlank(text[afterStart])

        val eol = if (usesCrLf(text, newlinePos, lineStart)) "\r\n" else "\n"
        val unit = if (base.contains(TAB)) TAB else EditorTextOps.INDENT
        if (base.length + unit.length > MAX_INDENT) return null

        val edit: AutoIndentEdit = when {
            // Cursor inside the leading blanks: the blanks after it move down with the text,
            // so adding more would double the indentation.
            beforeIsBlank && blankAfterCursor ->
                AutoIndentEdit(newlinePos, newlinePos + 1, eol, newlinePos + eol.length)

            // "{|}" or "{|   }": clean three-line block, cursor on the middle line.
            opensBlock && closesBlock -> {
                val inner = eol + base + unit
                AutoIndentEdit(
                    start = newlinePos,
                    end = closeIdx,
                    text = inner + eol + base,
                    cursor = newlinePos + inner.length
                )
            }

            // "{|": one level deeper.
            opensBlock -> {
                val replacement = eol + base + unit
                AutoIndentEdit(
                    newlinePos,
                    newlinePos + 1,
                    replacement,
                    newlinePos + replacement.length
                )
            }

            // "code|}": the '}' moves to its own line, one level less.
            closesBlock && !beforeIsBlank -> {
                val replacement = eol + dedent(base)
                AutoIndentEdit(newlinePos, closeIdx, replacement, newlinePos + replacement.length)
            }

            // Everything else: keep the current indentation.
            else -> {
                val replacement = eol + base
                AutoIndentEdit(
                    newlinePos,
                    newlinePos + 1,
                    replacement,
                    newlinePos + replacement.length
                )
            }
        }

        // Nothing would change compared to the typed '\n'.
        if (edit.text == "\n" && edit.start == newlinePos && edit.end == newlinePos + 1) return null
        return edit
    }

    /** Removes one indentation level: one tab, or up to four trailing spaces. */
    private fun dedent(base: String): String {
        if (base.endsWith(TAB)) return base.substring(0, base.length - 1)
        var end = base.length
        var removed = 0
        while (end > 0 && base[end - 1] == ' ' && removed < EditorTextOps.INDENT.length) {
            end--
            removed++
        }
        return base.substring(0, end)
    }

    /** Detects line and block comments outside quoted strings on the current line. */
    private fun isInsideComment(text: CharSequence, start: Int, end: Int): Boolean {
        var quote: Char? = null
        var escaped = false
        var inBlockComment = false
        var index = start
        while (index < end) {
            val char = text[index]
            val next = if (index + 1 < end) text[index + 1] else '\u0000'
            if (inBlockComment) {
                if (char == '*' && next == '/') {
                    inBlockComment = false
                    index++
                }
            } else if (quote != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quote -> quote = null
                }
            } else {
                when {
                    char == '\'' || char == '"' -> quote = char
                    char == '#' -> return true
                    char == '/' && next == '/' -> return true
                    char == '/' && next == '*' -> {
                        inBlockComment = true
                        index++
                    }
                }
            }
            index++
        }
        return inBlockComment
    }

    /**
     * Whether the line breaks of this file are "\r\n". Looks at the terminator of the
     * current line (the next '\n' after the typed one); on the last line, at the terminator
     * of the previous line. Defaults to LF.
     */
    private fun usesCrLf(text: CharSequence, newlinePos: Int, lineStart: Int): Boolean {
        val length = text.length
        var i = newlinePos + 1
        var scanned = 0
        while (i < length && scanned <= MAX_SCAN) {
            if (text[i] == '\n') return text[i - 1] == '\r'
            i++
            scanned++
        }
        // No terminator found after the cursor: use the previous line's terminator.
        if (lineStart >= 2 && text[lineStart - 1] == '\n') return text[lineStart - 2] == '\r'
        return false
    }
}

/**
 * Continues indentation when the user presses Enter.
 *
 * Acts only on a single typed '\n' with the cursor right after it. Pasted text, several
 * changes at once, and any other multi-character edit are left untouched. The newline and
 * its indentation are written with one replace, as part of the same user edit, so they are
 * one undo step.
 */
@OptIn(ExperimentalFoundationApi::class)
object AutoIndentTransformation : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        val changeList = changes
        if (changeList.changeCount != 1) return

        val inserted = changeList.getRange(0)
        if (inserted.length != 1) return

        val current = selection
        if (!current.collapsed || current.start != inserted.end) return

        val edit = AutoIndent.computeEnter(asCharSequence(), inserted.start) ?: return

        replace(edit.start, edit.end, edit.text)
        selection = TextRange(edit.cursor)
    }
}
