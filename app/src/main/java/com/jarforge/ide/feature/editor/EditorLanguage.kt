package com.jarforge.ide.feature.editor

/** Language selected for the editor's visual-only syntax highlighting. */
enum class EditorLanguage {
    JAVA, TOML, MARKDOWN, JSON, GRADLE, GRADLE_PROPERTIES, PLAIN;

    companion object {
        fun fromPath(path: String): EditorLanguage {
            val normalizedPath = path.replace('\\', '/')
            val fileName = normalizedPath.substringAfterLast('/')
            return when {
                fileName.equals("gradle.properties", ignoreCase = true) -> GRADLE_PROPERTIES
                normalizedPath.endsWith(".gradle.kts", ignoreCase = true) -> GRADLE
                normalizedPath.endsWith(".gradle", ignoreCase = true) -> GRADLE
                path.endsWith(".java", ignoreCase = true) -> JAVA
                path.endsWith(".toml", ignoreCase = true) -> TOML
                path.endsWith(".md", ignoreCase = true) -> MARKDOWN
                path.endsWith(".json", ignoreCase = true) -> JSON
                else -> PLAIN
            }
        }
    }
}
