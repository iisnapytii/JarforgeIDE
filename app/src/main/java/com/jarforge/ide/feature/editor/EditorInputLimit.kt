package com.jarforge.ide.feature.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import com.jarforge.ide.data.model.FilePreview

/** Counts the UTF-8 encoding size without allocating an intermediate String or ByteArray. */
internal fun utf8ByteCount(text: CharSequence, stopAfter: Int = Int.MAX_VALUE): Int {
    var bytes = 0
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            char.code <= 0x7F -> bytes += 1
            char.code <= 0x7FF -> bytes += 2
            char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate() -> {
                bytes += 4
                index++
            }
            char.isSurrogate() -> bytes += 1 // Matches String.toByteArray(UTF_8)'s replacement behavior.
            else -> bytes += 3
        }
        if (bytes > stopAfter) return bytes
        index++
    }
    return bytes
}

/** Rejects an entire user edit if its resulting text would exceed the file's UTF-8 byte limit. */
@OptIn(ExperimentalFoundationApi::class)
internal object Utf8FileSizeTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (utf8ByteCount(asCharSequence(), FilePreview.MAX_BYTES) > FilePreview.MAX_BYTES) {
            revertAllChanges()
        }
    }
}
