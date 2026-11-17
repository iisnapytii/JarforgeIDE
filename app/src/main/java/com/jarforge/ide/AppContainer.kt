package com.jarforge.ide

import android.content.Context
import com.jarforge.ide.data.storage.ProjectRepository
import com.jarforge.ide.data.storage.ProjectStorage
import java.io.File

/** Manual dependency container (no DI framework). */
class AppContainer(context: Context) {

    private val projectsRoot = File(context.filesDir, "projects")

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(ProjectStorage(projectsRoot))
    }
}
