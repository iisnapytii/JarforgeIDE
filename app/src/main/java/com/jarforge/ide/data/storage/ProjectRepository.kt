package com.jarforge.ide.data.storage

import com.jarforge.ide.data.model.FileEntry
import com.jarforge.ide.data.model.MinecraftVersions
import com.jarforge.ide.data.model.NewProjectRequest
import com.jarforge.ide.data.model.Project
import com.jarforge.ide.data.model.ProjectEntry
import com.jarforge.ide.data.model.ProjectException
import com.jarforge.ide.data.model.ProjectTemplate
import com.jarforge.ide.data.storage.templates.LoaderTemplates
import com.jarforge.ide.domain.ProjectNaming
import com.jarforge.ide.domain.ProjectValidators
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Project operations on top of the file system.
 *
 * All functions are blocking: call them from a background dispatcher.
 *
 * Safety rules:
 * - create/duplicate build into a temp directory and rename it into place;
 * - rename only rewrites .jarforge/project.json;
 * - delete moves the folder out of the project list first.
 */
class ProjectRepository(
    private val storage: ProjectStorage,
    private val scaffolder: ProjectScaffolder = ProjectScaffolder(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private val fileTree = FileTreeReader(storage)

    fun listProjects(): List<ProjectEntry> {
        return synchronized(lock) {
            storage.ensureRoot()
            storage.cleanupTemporaryDirs()
            val dirs = storage.rootDir.listFiles { f ->
                f.isDirectory && !f.name.startsWith(".")
            } ?: throw IOException("Cannot read the projects directory.")

            val entries = dirs.map<File, ProjectEntry> { dir ->
                try {
                    ProjectEntry.Valid(storage.readProject(dir))
                } catch (e: Exception) {
                    ProjectEntry.Damaged(dir.name, e.message ?: "Unreadable project.")
                }
            }

            val valid = entries.filterIsInstance<ProjectEntry.Valid>()
                .sortedByDescending { it.project.modifiedAtMillis }

            val damaged = entries.filterIsInstance<ProjectEntry.Damaged>()
                .sortedBy { it.directoryName }

            valid + damaged
        }
    }

    fun getProject(id: String): Result<Project> = runCatching {
        synchronized(lock) {
            storage.readProject(storage.projectDir(id))
        }
    }

    fun createProject(request: NewProjectRequest): Result<Project> = runCatching {
        synchronized(lock) {
            val r = request.copy(
                name = request.name.trim(),
                modId = request.modId.trim(),
                packageName = request.packageName.trim(),
                modVersion = request.modVersion.trim()
            )

            validate(r)
            storage.ensureRoot()

            if (nameExists(r.name, excludeId = null)) {
                throw ProjectException("A project named \"${r.name}\" already exists.")
            }

            val id = newUniqueId(r.name)
            val now = clock()

            val project = Project(
                id = id,
                name = r.name,
                loader = r.loader,
                minecraftVersion = r.minecraftVersion,
                modId = r.modId,
                packageName = r.packageName,
                modVersion = r.modVersion,
                template = r.template,
                createdAtMillis = now,
                modifiedAtMillis = now
            )

            val temp = storage.newTempDir("creating")

            try {
                scaffolder.scaffold(temp, project)
                storage.writeProject(temp, project)
                storage.promote(temp, id)
            } catch (e: Throwable) {
                temp.deleteRecursively()
                throw e
            }

            project
        }
    }

    /** Changes the display name only. Generated mod files are left untouched. */
    fun renameProject(id: String, newName: String): Result<Project> = runCatching {
        synchronized(lock) {
            val name = newName.trim()

            ProjectValidators.validateName(name)?.let {
                throw ProjectException(it)
            }

            val dir = storage.projectDir(id)
            val project = storage.readProject(dir)

            if (nameExists(name, excludeId = id)) {
                throw ProjectException("A project named \"$name\" already exists.")
            }

            val updated = project.copy(
                name = name,
                modifiedAtMillis = clock()
            )

            storage.writeProject(dir, updated)
            updated
        }
    }

    fun duplicateProject(id: String): Result<Project> = runCatching {
        synchronized(lock) {
            val sourceDir = storage.projectDir(id)
            val source = storage.readProject(sourceDir)
            val name = uniqueCopyName(source.name)
            val newId = newUniqueId(name)
            val now = clock()

            val copy = source.copy(
                id = newId,
                name = name,
                createdAtMillis = now,
                modifiedAtMillis = now
            )

            val temp = storage.newTempDir("copying")

            try {
                sourceDir.copyRecursively(temp, overwrite = true)
                storage.writeProject(temp, copy)
                storage.promote(temp, newId)
            } catch (e: Throwable) {
                temp.deleteRecursively()
                throw e
            }

            copy
        }
    }

    fun deleteProject(id: String): Result<Unit> = runCatching {
        synchronized(lock) {
            val dir = storage.projectDir(id)

            if (!dir.isDirectory) {
                throw ProjectException("Project not found.")
            }

            val doomed = File(
                storage.rootDir,
                "${ProjectStorage.DELETING_PREFIX}$id"
            )

            if (doomed.exists()) {
                doomed.deleteRecursively()
            }

            if (!dir.renameTo(doomed)) {
                throw IOException("Could not remove the project.")
            }

            doomed.deleteRecursively()
            Unit
        }
    }

    fun listFiles(
        projectId: String,
        relativePath: String = ""
    ): Result<List<FileEntry>> =
        runCatching {
            fileTree.listDirectory(projectId, relativePath)
        }

    // ---- helpers ----

    private fun validate(r: NewProjectRequest) {
        ProjectValidators.validateName(r.name)?.let {
            throw ProjectException(it)
        }

        ProjectValidators.validateModId(r.modId)?.let {
            throw ProjectException(it)
        }

        ProjectValidators.validatePackage(r.packageName)?.let {
            throw ProjectException(it)
        }

        ProjectValidators.validateModVersion(r.modVersion)?.let {
            throw ProjectException(it)
        }

        if (MinecraftVersions.find(r.loader, r.minecraftVersion) == null) {
            throw ProjectException(
                "Unsupported Minecraft version for ${r.loader.displayName}."
            )
        }

        if (
            r.template == ProjectTemplate.MIXIN_READY &&
            !LoaderTemplates.forLoader(r.loader).supportsMixin
        ) {
            throw ProjectException(
                "The Mixin-ready template is not available for ${r.loader.displayName}."
            )
        }
    }

    private fun nameExists(name: String, excludeId: String?): Boolean {
        val dirs = storage.rootDir.listFiles { f ->
            f.isDirectory && !f.name.startsWith(".")
        } ?: return false

        return dirs.any { dir ->
            val project = try {
                storage.readProject(dir)
            } catch (e: Exception) {
                null
            }

            project != null &&
                project.id != excludeId &&
                project.name.equals(name, ignoreCase = true)
        }
    }

    private fun newUniqueId(name: String): String {
        while (true) {
            val suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .take(8)

            val id = "${ProjectNaming.slug(name)}-$suffix"

            if (!File(storage.rootDir, id).exists()) {
                return id
            }
        }
    }

    private fun uniqueCopyName(original: String): String {
        val base = original.take(ProjectValidators.MAX_NAME_LENGTH - 10)
        var candidate = "$base copy"
        var n = 2

        while (nameExists(candidate, excludeId = null)) {
            candidate = "$base copy $n"
            n++
        }

        return candidate
    }
}
