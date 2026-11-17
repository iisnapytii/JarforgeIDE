package com.jarforge.ide.data.storage

import com.jarforge.ide.data.model.Loader
import com.jarforge.ide.data.model.Project
import com.jarforge.ide.data.model.ProjectTemplate
import org.json.JSONObject

/** Reads and writes .jarforge/project.json using the platform's org.json. */
object ProjectJson {

    const val SCHEMA_VERSION = 1

    fun toJson(project: Project): String {
        val o = JSONObject()
        o.put("schemaVersion", SCHEMA_VERSION)
        o.put("id", project.id)
        o.put("name", project.name)
        o.put("loader", project.loader.id)
        o.put("minecraftVersion", project.minecraftVersion)
        o.put("modId", project.modId)
        o.put("packageName", project.packageName)
        o.put("modVersion", project.modVersion)
        o.put("template", project.template.id)
        o.put("createdAtMillis", project.createdAtMillis)
        o.put("modifiedAtMillis", project.modifiedAtMillis)
        return o.toString(2) + "\n"
    }

    /** Throws on malformed or unsupported content. */
    fun parse(text: String): Project {
        val o = JSONObject(text)
        val version = o.optInt("schemaVersion", -1)
        if (version != SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported project format version: $version")
        }
        val loader = Loader.fromId(o.getString("loader"))
            ?: throw IllegalArgumentException("Unknown loader.")
        val template = ProjectTemplate.fromId(o.getString("template"))
            ?: throw IllegalArgumentException("Unknown template.")
        return Project(
            id = o.getString("id"),
            name = o.getString("name"),
            loader = loader,
            minecraftVersion = o.getString("minecraftVersion"),
            modId = o.getString("modId"),
            packageName = o.getString("packageName"),
            modVersion = o.getString("modVersion"),
            template = template,
            createdAtMillis = o.getLong("createdAtMillis"),
            modifiedAtMillis = o.getLong("modifiedAtMillis")
        )
    }
}
