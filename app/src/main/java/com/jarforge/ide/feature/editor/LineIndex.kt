package com.jarforge.ide.feature.editor

/**
 * Start offsets of the logical lines of a text. A logical line ends at '\n' (or at the end
 * of the text). Soft wrapping is not considered here; the layout decides that.
 *
 * Pure Kotlin, no Compose types.
 */
class LineIndex private constructor(
    private val starts: IntArray,
    private val textLength: Int
) {
    /** Number of logical lines. An empty text has one (empty) line. */
    val lineCount: Int get() = starts.size

    fun lineStart(line: Int): Int = starts[line]

    /** Exclusive end of the line's content: the offset of its '\n', or the text length. */
    fun lineEnd(line: Int): Int =
        if (line + 1 < starts.size) starts[line + 1] - 1 else textLength

    /** Index of the logical line that contains [offset]; the offset is clamped to the text. */
    fun lineOfOffset(offset: Int): Int {
        val target = offset.coerceIn(0, textLength)
        var low = 0
        var high = starts.size - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (starts[mid] <= target) low = mid else high = mid - 1
        }
        return low
    }

    companion object {
        fun of(text: String): LineIndex {
            var newlines = 0
            var found = text.indexOf('\n')
            while (found >= 0) {
                newlines++
                found = text.indexOf('\n', found + 1)
            }

            val starts = IntArray(newlines + 1)
            var line = 1
            found = text.indexOf('\n')
            while (found >= 0) {
                starts[line++] = found + 1
                found = text.indexOf('\n', found + 1)
            }
            return LineIndex(starts, text.length)
        }
    }
}

/**
 * Remembers the [LineIndex] of the last text it was asked about.
 *
 * The key is the identity of the String, which is immutable, so a hit is always correct and
 * costs nothing. Meant for a single thread (the UI thread).
 */
class LineIndexCache {
    private var lastText: String? = null
    private var lastIndex: LineIndex? = null

    fun get(text: String): LineIndex {
        val cached = lastIndex
        if (cached != null && text === lastText) return cached
        val index = LineIndex.of(text)
        lastText = text
        lastIndex = index
        return index
    }
}
