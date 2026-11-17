package com.jarforge.ide.data.storage

import com.jarforge.ide.data.model.Project
import com.jarforge.ide.data.storage.templates.LoaderTemplates
import java.io.File

/** Writes the template files of a project into an (empty) directory. */
class ProjectScaffolder {

    fun scaffold(projectDir: File, project: Project) {
        val files = LoaderTemplates.forLoader(project.loader).generate(project)
        for (generated in files) {
            val target = File(projectDir, generated.relativePath)
            target.parentFile?.mkdirs()
            target.writeText(generated.content, Charsets.UTF_8)
        }
    }
}
