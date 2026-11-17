package com.jarforge.ide.data.model

data class Project(
    val id: String,
    val name: String,
    val loader: Loader,
    val minecraftVersion: String,
    val modId: String,
    val packageName: String,
    val modVersion: String,
    val template: ProjectTemplate,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long
)

data class NewProjectRequest(
    val name: String,
    val loader: Loader,
    val minecraftVersion: String,
    val modId: String,
    val packageName: String,
    val modVersion: String,
    val template: ProjectTemplate
)

sealed interface ProjectEntry {
    data class Valid(val project: Project) : ProjectEntry
    data class Damaged(val directoryName: String, val reason: String) : ProjectEntry
}

/** Error with a message that is safe to show to the user. */
class ProjectException(message: String) : Exception(message)
