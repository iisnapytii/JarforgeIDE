package com.jarforge.ide.data.storage

import com.jarforge.ide.data.model.FileEntry
import com.jarforge.ide.data.model.FilePreview
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Lists one directory level at a time. Blocking: call from a background thread. */
class FileTreeReader(private val storage: ProjectStorage) {

    fun listDirectory(projectId: String, relativePath: String = ""): List<FileEntry> {
        val projectDir = storage.projectDir(projectId).canonicalFile
        val target = if (relativePath.isEmpty()) projectDir
        else File(projectDir, relativePath).canonicalFile

        require(
            target.path == projectDir.path ||
                target.path.startsWith(projectDir.path + File.separator)
        ) { "Path is outside the project." }
        require(target.isDirectory) { "Not a directory." }

        val children = target.listFiles() ?: throw IOException("Cannot read the directory.")
        val atRoot = target.path == projectDir.path

        return children
            .filter { !(atRoot && it.name == ProjectStorage.METADATA_DIR) }
            .map { file ->
                FileEntry(
                    name = file.name,
                    relativePath = file.relativeTo(projectDir).path.replace(File.separatorChar, '/'),
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L
                )
            }
            .sortedWith(
                compareByDescending<FileEntry> { it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
    }

    /**
     * Reads a project file for a read-only preview.
     *
     * - rejects paths that resolve outside the project (including through symlinks);
     * - rejects the app's own metadata folder;
     * - refuses files above [FilePreview.MAX_BYTES] without reading them fully;
     * - refuses binary files and anything that is not valid UTF-8.
     */
    fun readTextPreview(projectId: String, relativePath: String): FilePreview {
        require(relativePath.isNotEmpty()) { "No file selected." }

        val projectDir = storage.projectDir(projectId).canonicalFile
        val target = File(projectDir, relativePath).canonicalFile

        require(target.path.startsWith(projectDir.path + File.separator)) {
            "Path is outside the project."
        }
        val firstSegment = target.relativeTo(projectDir).path
            .substringBefore(File.separatorChar)
        require(firstSegment != ProjectStorage.METADATA_DIR) {
            "This file is managed by Jarforge IDE."
        }
        require(target.isFile) { "Not a file." }

        val size = target.length()
        if (size > FilePreview.MAX_BYTES) return FilePreview.TooLarge(size)

        val buffer = ByteArrayOutputStream()
        target.inputStream().use { input ->
            val chunk = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                // The file may have grown since the size check.
                if (buffer.size() > FilePreview.MAX_BYTES) return FilePreview.TooLarge(size)
            }
        }
        val bytes = buffer.toByteArray()

        // A NUL byte means binary content.
        if (bytes.any { it.toInt() == 0 }) return FilePreview.NotText(size)

        return try {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
            FilePreview.Text(text, size)
        } catch (e: CharacterCodingException) {
            FilePreview.NotText(size)
        }
    }
}
