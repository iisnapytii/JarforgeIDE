package com.jarforge.ide.feature.editor

/** One text replacement. A replacement with start == end is a plain insertion. */
data class TextReplacement(
    val start: Int,
    val end: Int,
    val text: String
)

/**
 * A complete edit of the editor text.
 *
 * [replacements] are listed in the order they must be applied: from the highest offset to
 * the lowest, so every offset stays valid while earlier ones are applied. The selection is
 * given in the coordinates of the text AFTER all replacements; [selectionStart] is the
 * anchor and [selectionEnd] is the cursor, so a reversed selection stays reversed.
 */
data class TextEdit(
    val replacements: List<TextReplacement>,
    val selectionStart: Int,
    val selectionEnd: Int
)

/**
 * Pure logic of the symbol toolbar. No Compose or Android types, so it can be checked in
 * isolation. All offsets are clamped to the text, and the selection may be in either direction.
 */
object EditorTextOps {

    /** One indentation level. Shared with the future auto-indent. */
    const val INDENT = "    "

    /** Replaces the selection (or inserts at the cursor) with [text]; the cursor goes after it. */
    fun insertText(text: String, selStart: Int, selEnd: Int, textLength: Int): TextEdit {
        val low = minOf(selStart, selEnd).coerceIn(0, textLength)
        val high = maxOf(selStart, selEnd).coerceIn(0, textLength)
        val caret = low + text.length
        return TextEdit(
            replacements = listOf(TextReplacement(low, high, text)),
            selectionStart = caret,
            selectionEnd = caret
        )
    }

    /**
     * Inserts [open] and [close].
     *
     * - No selection: both are inserted and the cursor goes between them.
     * - With a selection: the selected text is left untouched; [open] is inserted before it
     *   and [close] after it, and the selection stays on the same text inside the pair,
     *   in the same direction as before.
     */
    fun insertPair(
        open: String,
        close: String,
        selStart: Int,
        selEnd: Int,
        textLength: Int
    ): TextEdit {
        val low = minOf(selStart, selEnd).coerceIn(0, textLength)
        val high = maxOf(selStart, selEnd).coerceIn(0, textLength)

        if (low == high) {
            val caret = low + open.length
            return TextEdit(
                replacements = listOf(TextReplacement(low, low, open + close)),
                selectionStart = caret,
                selectionEnd = caret
            )
        }

        val innerStart = low + open.length
        val innerEnd = high + open.length
        val reversed = selStart > selEnd
        return TextEdit(
            // Highest offset first, so inserting [open] does not shift the position of [close].
            replacements = listOf(
                TextReplacement(high, high, close),
                TextReplacement(low, low, open)
            ),
            selectionStart = if (reversed) innerEnd else innerStart,
            selectionEnd = if (reversed) innerStart else innerEnd
        )
    }
}
