package com.jarforge.ide.domain

/** Each validator returns an error message, or null when the value is valid. */
object ProjectValidators {

    const val MAX_NAME_LENGTH = 48

    private val RESERVED_MOD_IDS = setOf(
        "minecraft", "forge", "neoforge", "fabric", "fabricloader", "java", "mixin"
    )
    private val MOD_ID_REGEX = Regex("^[a-z][a-z0-9_]{1,63}$")
    private val PACKAGE_SEGMENT_REGEX = Regex("^[a-z][a-z0-9_]*$")
    private val VERSION_REGEX = Regex("^\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.]+)?$")

    fun validateName(raw: String): String? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> "Enter a project name."
            name.length > MAX_NAME_LENGTH -> "Use at most $MAX_NAME_LENGTH characters."
            name.any { it.isISOControl() } -> "Remove control characters."
            else -> null
        }
    }

    fun validateModId(modId: String): String? {
        return when {
            modId.isEmpty() -> "Enter a mod ID."
            !MOD_ID_REGEX.matches(modId) ->
                "Use 2-64 characters: lowercase letters, digits and underscores, starting with a letter."
            modId in RESERVED_MOD_IDS -> "\"$modId\" is reserved."
            else -> null
        }
    }

    fun validatePackage(value: String): String? {
        if (value.isEmpty()) return "Enter a package name."
        if (value.length > 128) return "Use at most 128 characters."
        for (segment in value.split('.')) {
            if (!PACKAGE_SEGMENT_REGEX.matches(segment)) {
                return "Each package part must be lowercase letters, digits or underscores, starting with a letter."
            }
            if (segment in ProjectNaming.JAVA_KEYWORDS) {
                return "\"$segment\" is a reserved Java word."
            }
        }
        return null
    }

    fun validateModVersion(value: String): String? {
        return if (VERSION_REGEX.matches(value)) null
        else "Use a version like 1.0.0 or 1.0.0-beta.1."
    }
}
