package com.jarforge.ide.feature.editor

/** Token categories used by the lightweight Gradle/Groovy/Kotlin DSL lexer. */
enum class GradleTokenKind {
    COMMENT, STRING, NUMBER, KEYWORD, BOOLEAN, NULL, KEY, PUNCTUATION, OPERATOR
}

enum class GradleFileMode { SCRIPT, PROPERTIES }

/**
 * Visual-only, allocation-bounded lexer for Gradle scripts and gradle.properties.
 * It is deliberately not a Groovy/Kotlin parser and never evaluates input.
 */
object GradleSyntaxHighlighter {
    const val MAX_HIGHLIGHT_LENGTH = 100_000

    private data class NumberScan(val end: Int, val valid: Boolean)

    private val keywords = setOf(
        // Gradle DSL blocks and common configuration methods.
        "plugins", "pluginManagement", "dependencyResolutionManagement", "repositories",
        "dependencies", "constraints", "configurations", "sourceSets", "tasks", "task",
        "include", "includeBuild", "project", "allprojects", "subprojects", "buildscript",
        "settings", "publishing", "signing", "android", "kotlin", "java", "application",
        "group", "version", "description", "rootProject", "rootProjectName", "id",
        "alias", "versionCatalogs", "libs", "platform", "enforcedPlatform", "classpath",
        "implementation", "api", "compileOnly", "runtimeOnly", "testImplementation",
        "testRuntimeOnly", "annotationProcessor", "kapt", "ksp", "mavenCentral", "google",
        "gradlePluginPortal", "maven", "flatDir", "url", "credentials", "content",
        "exclude", "because", "force", "strictly", "prefer", "reject", "resolutionStrategy",
        "defaultConfig", "buildTypes", "productFlavors", "compileSdk", "minSdk",
        "targetSdk", "namespace", "applicationId", "versionCode", "versionName",
        "compileOptions", "sourceCompatibility", "targetCompatibility", "kotlinOptions",
        "compilerOptions", "jvmTarget", "composeOptions", "buildFeatures", "testOptions",
        "lint", "packaging", "manifest", "proguardFiles", "minifyEnabled", "isMinifyEnabled",
        "debuggable", "signingConfig", "getByName", "create", "register", "named",
        "configureEach", "withType", "dependsOn", "doLast", "doFirst", "finalizedBy",
        "mustRunAfter", "shouldRunAfter", "onlyIf", "enabled", "outputs", "inputs",
        "from", "into", "destinationDirectory", "archiveBaseName", "archiveClassifier",
        "archiveVersion", "mainClass", "toolchain", "languageVersion", "test", "useJUnit",
        // Common Kotlin and Groovy DSL language words.
        "def", "val", "var", "fun", "class", "interface", "object", "enum", "data",
        "sealed", "open", "override", "public", "private", "protected", "internal",
        "abstract", "final", "static", "const", "lateinit", "by", "lazy", "this", "super",
        "if", "else", "when", "for", "while", "do", "try", "catch", "finally", "throw",
        "return", "break", "continue", "as", "is", "in", "out", "where", "import", "package",
        "new", "extends", "implements", "apply", "with", "delegate", "it", "projectDir",
        "rootDir", "buildDir", "gradle", "extra", "extensions", "providers", "property",
        "file", "files", "uri", "exec", "copy", "delete", "mkdir", "logger"
    )

    fun tokenize(
        text: CharSequence,
        mode: GradleFileMode = GradleFileMode.SCRIPT,
        emit: (start: Int, end: Int, kind: GradleTokenKind) -> Unit
    ) {
        if (text.isEmpty() || text.length > MAX_HIGHLIGHT_LENGTH) return
        when (mode) {
            GradleFileMode.SCRIPT -> tokenizeScript(text, emit)
            GradleFileMode.PROPERTIES -> tokenizeProperties(text, emit)
        }
    }

    private fun tokenizeScript(
        text: CharSequence,
        emit: (Int, Int, GradleTokenKind) -> Unit
    ) {
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            val next = if (i + 1 < n) text[i + 1] else '\u0000'
            when {
                c == '/' && next == '/' -> {
                    var end = i + 2
                    while (end < n && text[end] != '\n' && text[end] != '\r') end++
                    emit(i, end, GradleTokenKind.COMMENT)
                    i = end
                }
                c == '/' && next == '*' -> {
                    var end = i + 2
                    while (end + 1 < n && !(text[end] == '*' && text[end + 1] == '/')) end++
                    end = if (end + 1 < n) end + 2 else n
                    emit(i, end, GradleTokenKind.COMMENT)
                    i = end
                }
                c == '\'' || c == '"' -> {
                    val end = scanString(text, i, c)
                    emit(i, end, GradleTokenKind.STRING)
                    i = end
                }
                c.isDigit() || (c == '.' && next.isDigit()) -> {
                    val number = scanNumber(text, i)
                    if (number.valid) emit(i, number.end, GradleTokenKind.NUMBER)
                    i = number.end
                }
                isIdentifierStart(c) -> {
                    var end = i + 1
                    while (end < n && isIdentifierPart(text[end])) end++
                    val kind = classifyIdentifier(text, i, end)
                    if (kind != null) emit(i, end, kind)
                    i = end
                }
                c in "(){}[],:;." -> {
                    emit(i, i + 1, GradleTokenKind.PUNCTUATION)
                    i++
                }
                c in "=+-*/%?!&|<>^~@" -> {
                    var end = i + 1
                    while (end < n && text[end] in "=+-*/%?!&|<>^~@") {
                        if (text[end] == '/' && end + 1 < n &&
                            (text[end + 1] == '/' || text[end + 1] == '*')) break
                        end++
                    }
                    emit(i, end, GradleTokenKind.OPERATOR)
                    i = end
                }
                else -> i++
            }
        }
    }

    private fun tokenizeProperties(
        text: CharSequence,
        emit: (Int, Int, GradleTokenKind) -> Unit
    ) {
        val n = text.length
        var lineStart = 0
        while (lineStart < n) {
            var lineEnd = lineStart
            while (lineEnd < n && text[lineEnd] != '\n' && text[lineEnd] != '\r') lineEnd++

            var first = lineStart
            while (first < lineEnd && (text[first] == ' ' || text[first] == '\t' || text[first] == '\u000c')) first++
            if (first < lineEnd && (text[first] == '#' || text[first] == '!')) {
                emit(first, lineEnd, GradleTokenKind.COMMENT)
            } else if (first < lineEnd) {
                var keyEnd = first
                while (keyEnd < lineEnd && text[keyEnd] != '=' && text[keyEnd] != ':' &&
                    !text[keyEnd].isWhitespace()) keyEnd++
                if (keyEnd > first) emit(first, keyEnd, GradleTokenKind.KEY)

                var valueStart = keyEnd
                while (valueStart < lineEnd && text[valueStart].isWhitespace()) valueStart++
                if (valueStart < lineEnd && (text[valueStart] == '=' || text[valueStart] == ':')) {
                    emit(valueStart, valueStart + 1, GradleTokenKind.OPERATOR)
                    valueStart++
                    while (valueStart < lineEnd && text[valueStart].isWhitespace()) valueStart++
                }
                var valueEnd = lineEnd
                while (valueEnd > valueStart && text[valueEnd - 1].isWhitespace()) valueEnd--
                if (valueStart < valueEnd) {
                    val kind = classifyPropertyValue(text, valueStart, valueEnd)
                    emit(valueStart, valueEnd, kind)
                }
            }
            lineStart = when {
                lineEnd < n && text[lineEnd] == '\r' && lineEnd + 1 < n && text[lineEnd + 1] == '\n' -> lineEnd + 2
                lineEnd < n -> lineEnd + 1
                else -> n
            }
        }
    }

    private fun classifyPropertyValue(text: CharSequence, start: Int, end: Int): GradleTokenKind {
        if (matches(text, start, end, "true") || matches(text, start, end, "false")) {
            return GradleTokenKind.BOOLEAN
        }
        if (matches(text, start, end, "null")) return GradleTokenKind.NULL
        if (isNumber(text, start, end)) return GradleTokenKind.NUMBER
        // Properties values are scalar text; quoted values are visually grouped as strings too.
        return GradleTokenKind.STRING
    }

    private fun classifyIdentifier(text: CharSequence, start: Int, end: Int): GradleTokenKind? {
        val length = end - start
        if ((length == 4 && matches(text, start, end, "true")) ||
            (length == 5 && matches(text, start, end, "false"))) return GradleTokenKind.BOOLEAN
        if (length == 4 && matches(text, start, end, "null")) return GradleTokenKind.NULL
        // Avoid allocating/copying arbitrary long identifiers during repeated Compose passes.
        if (length > 32) return null
        return if (text.subSequence(start, end).toString() in keywords) GradleTokenKind.KEYWORD else null
    }

    private fun scanString(text: CharSequence, start: Int, quote: Char): Int {
        val n = text.length
        val triple = start + 2 < n && text[start + 1] == quote && text[start + 2] == quote
        var i = start + if (triple) 3 else 1
        while (i < n) {
            if (triple && i + 2 < n && text[i] == quote &&
                text[i + 1] == quote && text[i + 2] == quote) return i + 3
            if (!triple && text[i] == quote) return i + 1
            if (text[i] == '\\') {
                if (i + 1 >= n) return n
                if (!triple && (text[i + 1] == '\n' || text[i + 1] == '\r')) return i
                i += 2
                continue
            }
            if (!triple && (text[i] == '\n' || text[i] == '\r')) return i
            i++
        }
        return n
    }

    private fun scanNumber(text: CharSequence, start: Int): NumberScan {
        val n = text.length
        var i = start
        if (i + 1 < n && text[i] == '0' && (text[i + 1] == 'x' || text[i + 1] == 'X' ||
                text[i + 1] == 'b' || text[i + 1] == 'B')) {
            val radix = if (text[i + 1] == 'x' || text[i + 1] == 'X') 16 else 2
            i += 2
            val digits = i
            while (i < n && (text[i] == '_' || if (radix == 16) text[i].digitToIntOrNull(16) != null
                else text[i] == '0' || text[i] == '1')) i++
            if (i == digits) {
                while (i < n && isIdentifierPart(text[i])) i++
                return NumberScan(i, false)
            }
        } else {
            while (i < n && (text[i].isDigit() || text[i] == '_')) i++
            if (i < n && text[i] == '.') {
                i++
                while (i < n && (text[i].isDigit() || text[i] == '_')) i++
            }
            if (i < n && (text[i] == 'e' || text[i] == 'E')) {
                var exponent = i + 1
                if (exponent < n && (text[exponent] == '+' || text[exponent] == '-')) exponent++
                val digits = exponent
                while (exponent < n && (text[exponent].isDigit() || text[exponent] == '_')) exponent++
                if (exponent > digits) i = exponent
            }
        }
        if (i < n && text[i] in "lLfFdDuU") i++
        if (i < n && isIdentifierPart(text[i])) {
            while (i < n && isIdentifierPart(text[i])) i++
            return NumberScan(i, false)
        }
        return NumberScan(i, i > start)
    }

    private fun isNumber(text: CharSequence, start: Int, end: Int): Boolean {
        var i = start
        if (i < end && (text[i] == '+' || text[i] == '-')) i++
        if (i + 1 < end && text[i] == '0' &&
            (text[i + 1] == 'x' || text[i + 1] == 'X' || text[i + 1] == 'b' || text[i + 1] == 'B')) {
            val radix = if (text[i + 1] == 'x' || text[i + 1] == 'X') 16 else 2
            i += 2
            val digits = i
            while (i < end && (text[i] == '_' || if (radix == 16) text[i].digitToIntOrNull(16) != null
                else text[i] == '0' || text[i] == '1')) i++
            if (i == digits) return false
        } else {
            val integerStart = i
            while (i < end && (text[i].isDigit() || text[i] == '_')) i++
            val hasIntegerDigits = i > integerStart
            var hasFractionDigits = false
            if (i < end && text[i] == '.') {
                i++
                val fractionStart = i
                while (i < end && (text[i].isDigit() || text[i] == '_')) i++
                hasFractionDigits = i > fractionStart
            }
            if (!hasIntegerDigits && !hasFractionDigits) return false
            if (i < end && (text[i] == 'e' || text[i] == 'E')) {
                i++
                if (i < end && (text[i] == '+' || text[i] == '-')) i++
                val exponent = i
                while (i < end && text[i].isDigit()) i++
                if (i == exponent) return false
            }
        }
        if (i < end && text[i] in "lLfFdDuU") i++
        return i == end
    }

    private fun matches(text: CharSequence, start: Int, end: Int, value: String): Boolean {
        if (end - start != value.length) return false
        for (i in value.indices) if (text[start + i] != value[i]) return false
        return true
    }

    private fun isIdentifierStart(c: Char): Boolean = c.isLetter() || c == '_' || c == '$'
    private fun isIdentifierPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'
}
