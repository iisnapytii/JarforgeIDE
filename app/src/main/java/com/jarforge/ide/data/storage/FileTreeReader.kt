package com.jarforge.ide.data.storage

import com.jarforge.ide.data.model.FileEntry
import java.io.File
import java.io.IOException

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
}
