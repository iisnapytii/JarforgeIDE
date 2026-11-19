package com.jarforge.ide.navigation

object Routes {
    const val PROJECTS = "projects"
    const val NEW_PROJECT = "new_project"
    const val SETTINGS = "settings"

    const val EDITOR_ARG_PROJECT_ID = "projectId"

    /** Route pattern for the editor of one project. */
    const val EDITOR = "editor/{$EDITOR_ARG_PROJECT_ID}"

    /** Concrete route for opening the editor of [projectId]. Project ids contain only [a-z0-9-]. */
    fun editor(projectId: String): String = "editor/$projectId"
}
