package com.jarforge.ide.feature.editor

/** Language selected for the editor's visual-only syntax highlighting. */
enum class EditorLanguage {
    JAVA, TOML, MARKDOWN, PLAIN;
    companion object {
        fun fromPath(path: String): EditorLanguage = when {
            path.endsWith(".java", true) -> JAVA
            path.endsWith(".toml", true) -> TOML
            path.endsWith(".md", true) -> MARKDOWN
            else -> PLAIN
        }
    }
}
