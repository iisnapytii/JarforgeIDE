package com.jarforge.ide.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LineIndexTest {
    @Test fun emptyTextHasOneEmptyLine() {
        val index = LineIndex.of("")
        assertEquals(1, index.lineCount)
        assertEquals(0, index.lineStart(0))
        assertEquals(0, index.lineEnd(0))
    }

    @Test fun indexesLogicalLinesAndTrailingEmptyLine() {
        val index = LineIndex.of("one\ntwo\n")
        assertEquals(3, index.lineCount)
        assertEquals(0, index.lineStart(0))
        assertEquals(4, index.lineStart(1))
        assertEquals(8, index.lineStart(2))
        assertEquals(3, index.lineEnd(0))
        assertEquals(7, index.lineEnd(1))
        assertEquals(8, index.lineEnd(2))
    }

    @Test fun offsetLookupClampsAndTreatsNewlineAsPreviousLineTerminator() {
        val index = LineIndex.of("a\nbc")
        assertEquals(0, index.lineOfOffset(-10))
        assertEquals(0, index.lineOfOffset(1))
        assertEquals(1, index.lineOfOffset(2))
        assertEquals(1, index.lineOfOffset(99))
    }

    @Test fun crlfCountsAsOneLogicalLineBreak() {
        val index = LineIndex.of("a\r\nb")
        assertEquals(2, index.lineCount)
        assertEquals(2, index.lineEnd(0))
        assertEquals(3, index.lineStart(1))
    }

    @Test fun indexesTextAtTheMaximumEditableFileSize() {
        val text = "x\n".repeat(128 * 1024)
        val index = LineIndex.of(text)
        assertEquals(128 * 1024 + 1, index.lineCount)
        assertEquals(text.length, index.lineStart(index.lineCount - 1))
    }

    @Test fun cacheReusesOnlyTheSameImmutableStringInstance() {
        val cache = LineIndexCache()
        val text = String(charArrayOf('a', '\n', 'b'))
        val first = cache.get(text)
        assertSame(first, cache.get(text))
        val equalButDifferent = String(charArrayOf('a', '\n', 'b'))
        assertEquals(first.lineCount, cache.get(equalButDifferent).lineCount)
    }
}
