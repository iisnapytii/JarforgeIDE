package com.jarforge.ide.feature.editor

import com.jarforge.ide.data.model.FilePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorInputLimitTest {
    @Test fun countsUtf8BytesForAsciiAndMultibyteCharacters() {
        assertEquals(3, utf8ByteCount("abc"))
        assertEquals(2, utf8ByteCount("é"))
        assertEquals(3, utf8ByteCount("€"))
        assertEquals(4, utf8ByteCount("😀"))
    }

    @Test fun acceptsExactByteLimitAndReportsFirstOverflow() {
        val exact = "a".repeat(FilePreview.MAX_BYTES)
        assertEquals(FilePreview.MAX_BYTES, utf8ByteCount(exact))
        assertEquals(FilePreview.MAX_BYTES + 1, utf8ByteCount(exact + "b"))
    }

    @Test fun countsMaximumSizedTextWithoutAllocatingEncodedByteArray() {
        val text = "x\n".repeat(FilePreview.MAX_BYTES / 2)
        val started = System.nanoTime()
        assertEquals(FilePreview.MAX_BYTES, utf8ByteCount(text))
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue("UTF-8 size scan took $millis ms", millis < 2_000)
    }

    @Test fun byteLimitIsNotACharacterLimit() {
        val text = "é".repeat(FilePreview.MAX_BYTES / 2)
        assertEquals(FilePreview.MAX_BYTES, utf8ByteCount(text))
        assertEquals(FilePreview.MAX_BYTES + 2, utf8ByteCount(text + "é"))
    }
}
