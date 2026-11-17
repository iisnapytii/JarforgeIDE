package com.jarforge.ide.data.model

data class FileEntry(
    val name: String,
    /** Path relative to the project root, using '/' separators. */
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long
)
