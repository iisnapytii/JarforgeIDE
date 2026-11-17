package com.jarforge.ide.data.storage

import com.jarforge.ide.data.model.Project
import com.jarforge.ide.data.model.ProjectException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Low-level file access for the projects root (filesDir/projects).
 * Blocking: call from a background thread.
 */
class ProjectStorage(val rootDir: File) {

    fun ensureRoot() {
        if (!rootDir.isDirectory && !rootDir.mkdirs()) {
            throw IOException("Cannot create the projects directory.")
        }
    }

    /** Resolves a project directory; rejects ids that could escape the root. */
    fun projectDir(id: String): File {
        require(ID_REGEX.matches(id)) { "Invalid project id." }
        return File(rootDir, id)
    }

    fun readProject(dir: File): Project {
        if (!dir.isDirectory) throw ProjectException("Project not found.")
        val file = File(File(dir, METADATA_DIR), METADATA_FILE)
        if (!file.isFile) throw ProjectException("Project metadata is missing.")
        val project = ProjectJson.parse(file.readText(Charsets.UTF_8))
        if (project.id != dir.name) {
            throw ProjectException("Project metadata does not match its folder.")
        }
        return project
    }

    /** Writes project.json via a temp file so a crash never leaves a half-written file. */
    fun writeProject(dir: File, project: Project) {
        val metaDir = File(dir, METADATA_DIR)
        if (!metaDir.isDirectory && !metaDir.mkdirs()) {
            throw IOException("Cannot create the metadata directory.")
        }
        val target = File(metaDir, METADATA_FILE)
        val tmp = File(metaDir, "$METADATA_FILE.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(ProjectJson.toJson(project).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        try {
            Files.move(
                tmp.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun newTempDir(kind: String): File {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(12)
        val dir = File(rootDir, ".$kind-$suffix")
        if (!dir.mkdirs()) throw IOException("Cannot create a temporary directory.")
        return dir
    }

    /** Renames a fully prepared temp directory into its final place. */
    fun promote(tempDir: File, id: String) {
        val target = projectDir(id)
        if (target.exists()) throw IOException("A project folder with this id already exists.")
        if (!tempDir.renameTo(target)) throw IOException("Cannot finalize the project folder.")
    }

    /** Removes leftovers of interrupted create/copy/delete operations. */
    fun cleanupTemporaryDirs() {
        rootDir.listFiles()?.forEach { f ->
            if (f.isDirectory && TEMP_PREFIXES.any { f.name.startsWith(it) }) {
                f.deleteRecursively()
            }
        }
    }

    companion object {
        const val METADATA_DIR = ".jarforge"
        const val METADATA_FILE = "project.json"
        const val DELETING_PREFIX = ".deleting-"
        private val TEMP_PREFIXES = listOf(".creating-", ".copying-", DELETING_PREFIX)
        private val ID_REGEX = Regex("^[a-z0-9][a-z0-9-]{0,40}$")
    }
}
