package com.jarforge.ide.domain

object ProjectNaming {

    val JAVA_KEYWORDS: Set<String> = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new",
        "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "true", "false", "null"
    )

    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** Folder-safe slug used as the first part of a project id. */
    fun slug(name: String): String {
        val s = name.lowercase()
            .replace(NON_ALNUM, "-")
            .trim('-')
            .take(24)
            .trim('-')
        return s.ifEmpty { "project" }
    }

    fun suggestModId(name: String): String {
        var s = name.lowercase().replace(NON_ALNUM, "_").trim('_')
        if (s.isEmpty() || !s[0].isLetter()) {
            s = "mod_$s".trimEnd('_')
        }
        s = s.take(64).trimEnd('_')
        return if (s.length < 2) "mod" else s
    }

    fun suggestPackage(modId: String): String {
        val segment = if (modId in JAVA_KEYWORDS) "${modId}_mod" else modId
        return "com.example.$segment"
    }

    /** Java class name for the generated mod entry point. */
    fun className(modId: String): String {
        val pascal = modId
            .split('_', '-')
            .filter { it.isNotEmpty() }
            .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
        return if (pascal.length > 3 && pascal.endsWith("mod", ignoreCase = true)) {
            pascal
        } else {
            pascal + "Mod"
        }
    }
}
