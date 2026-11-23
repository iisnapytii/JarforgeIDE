package com.jarforge.ide.data.storage

import com.jarforge.ide.data.model.FileEntry
import com.jarforge.ide.data.model.FilePreview
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Lists one directory level at a time and handles safe text file reads/writes. Blocking: call from a background thread. */
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

    /**
     * Safely writes UTF-8 text to an existing editable file inside the project.
     *
     * - enforces [MAX_EDITABLE_BYTES] independently of [FilePreview.MAX_BYTES];
     * - rejects symbolic links at the root, project folder, and every path segment;
     * - re-verifies path and symlink safety immediately before replacing the target file;
     * - writes to a unique temp file with fd.sync() and moves atomically;
     * - cleans up the temp file on failure without touching the original file.
     */
    fun writeTextFile(projectId: String, relativePath: String, content: String): Long {
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_EDITABLE_BYTES) {
            "File content exceeds the ${MAX_EDITABLE_BYTES / 1024} KB editing limit."
        }

        val verifiedTarget = verifyEditableTarget(projectId, relativePath)
        val parentDir = verifiedTarget.parentFile
            ?: throw IOException("Cannot resolve the parent directory.")

        val tmpFile = File(parentDir, ".jarforge-save-${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(tmpFile).use { out ->
                out.write(bytes)
                out.fd.sync()
            }

            // Re-verify target path and symlink safety right before atomic replacement.
            val reverifiedTarget = verifyEditableTarget(projectId, relativePath)
            require(!Files.isSymbolicLink(tmpFile.toPath())) {
                "Temporary save file cannot be a symbolic link."
            }

            try {
                Files.move(
                    tmpFile.toPath(),
                    reverifiedTarget.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(
                    tmpFile.toPath(),
                    reverifiedTarget.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (t: Throwable) {
            tmpFile.delete()
            throw t
        }

        return bytes.size.toLong()
    }

    private fun verifyEditableTarget(projectId: String, relativePath: String): File {
        require(relativePath.isNotEmpty()) { "No file selected." }

        val rawRootDir = storage.rootDir
        require(!Files.isSymbolicLink(rawRootDir.toPath())) {
            "Projects root directory cannot be a symbolic link."
        }
        val canonicalRootDir = rawRootDir.canonicalFile

        val rawProjectDir = storage.projectDir(projectId)
        require(!Files.isSymbolicLink(rawProjectDir.toPath())) {
            "Project directory cannot be a symbolic link."
        }
        val projectDir = rawProjectDir.canonicalFile
        require(projectDir.parentFile == canonicalRootDir) {
            "Project directory is outside the projects root."
        }

        val normalized = relativePath.replace('\\', '/')
        val segments = normalized.split('/')
        require(segments.all { it.isNotEmpty() && it != "." && it != ".." }) {
            "Invalid relative file path."
        }
        require(segments.first() != ProjectStorage.METADATA_DIR) {
            "This file is managed by Jarforge IDE."
        }

        var current = projectDir
        for (segment in segments) {
            current = File(current, segment)
            require(!Files.isSymbolicLink(current.toPath())) {
                "Symbolic links cannot be modified."
            }
        }

        val canonicalTarget = current.canonicalFile
        require(canonicalTarget.path.startsWith(projectDir.path + File.separator)) {
            "Path is outside the project."
        }
        require(canonicalTarget == current) {
            "Resolved path does not match the expected file."
        }
        require(current.isFile) { "Target file does not exist." }

        return current
    }

    companion object {
        const val MAX_EDITABLE_BYTES: Int = FilePreview.MAX_BYTES
    }
}
