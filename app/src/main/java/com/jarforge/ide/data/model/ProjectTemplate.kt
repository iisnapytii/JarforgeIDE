package com.jarforge.ide.data.model

enum class ProjectTemplate(
    val id: String,
    val displayName: String,
    val description: String
) {
    BASIC("basic", "Basic", "Mod entry point and loader metadata."),
    MIXIN_READY("mixin_ready", "Mixin-ready", "Basic plus an empty Mixin config and package.");

    companion object {
        fun fromId(id: String): ProjectTemplate? = entries.firstOrNull { it.id == id }
    }
}
