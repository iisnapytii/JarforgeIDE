package com.jarforge.ide.data.model

/** Result of reading a project file for a read-only preview. */
sealed interface FilePreview {

    /** UTF-8 text content of a file within the preview size limit. */
    data class Text(val content: String, val sizeBytes: Long) : FilePreview

    /** The file is larger than [MAX_BYTES]; its content was not read. */
    data class TooLarge(val sizeBytes: Long) : FilePreview

    /** The file is binary or not valid UTF-8, so it is not shown as text. */
    data class NotText(val sizeBytes: Long) : FilePreview

    companion object {
        const val MAX_BYTES: Int = 256 * 1024
    }
}
