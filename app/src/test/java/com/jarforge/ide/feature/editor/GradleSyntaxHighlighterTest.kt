package com.jarforge.ide.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleSyntaxHighlighterTest {
    private data class Token(val text: String, val kind: GradleTokenKind, val start: Int, val end: Int)

    private fun tokens(source: String, mode: GradleFileMode = GradleFileMode.SCRIPT): List<Token> =
        buildList {
            GradleSyntaxHighlighter.tokenize(source, mode) { start, end, kind ->
                add(Token(source.substring(start, end), kind, start, end))
            }
        }

    private fun has(
        source: String,
        value: String,
        kind: GradleTokenKind,
        mode: GradleFileMode = GradleFileMode.SCRIPT
    ): Boolean = tokens(source, mode).any { it.text == value && it.kind == kind }

    @Test fun recognizesGradleDslKeywordsAndLiterals() {
        val source = "plugins { id(\"com.android.application\") version \"8.1\" }\n" +
            "dependencies { implementation(\"g:a:1\"); testImplementation(\"junit:junit:4\") }\n" +
            "val enabled = true; val missing = null"
        for (word in listOf("plugins", "id", "version", "dependencies", "implementation", "testImplementation", "val")) {
            assertTrue("keyword $word", has(source, word, GradleTokenKind.KEYWORD))
        }
        assertTrue(has(source, "true", GradleTokenKind.BOOLEAN))
        assertTrue(has(source, "null", GradleTokenKind.NULL))
    }

    @Test fun highlightsRepresentativeRealGradleProjectFiles() {
        val script = """plugins { alias(libs.plugins.android.application) }
            |android { namespace = "com.jarforge.ide"; compileSdk = 36 }
            |dependencies { implementation(platform(libs.androidx.compose.bom)) }
        """.trimMargin()
        assertTrue(has(script, "plugins", GradleTokenKind.KEYWORD))
        assertTrue(has(script, "alias", GradleTokenKind.KEYWORD))
        assertTrue(has(script, "android", GradleTokenKind.KEYWORD))
        assertTrue(has(script, "compileSdk", GradleTokenKind.KEYWORD))
        assertTrue(has(script, "36", GradleTokenKind.NUMBER))
        assertTrue(has(script, "implementation", GradleTokenKind.KEYWORD))
        assertTrue(has(script, "\"com.jarforge.ide\"", GradleTokenKind.STRING))

        val properties = "org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8\nandroid.useAndroidX=true"
        assertTrue(has(properties, "org.gradle.jvmargs", GradleTokenKind.KEY, GradleFileMode.PROPERTIES))
        assertTrue(has(properties, "-Xmx3g -Dfile.encoding=UTF-8", GradleTokenKind.STRING, GradleFileMode.PROPERTIES))
        assertTrue(has(properties, "android.useAndroidX", GradleTokenKind.KEY, GradleFileMode.PROPERTIES))
        assertTrue(has(properties, "true", GradleTokenKind.BOOLEAN, GradleFileMode.PROPERTIES))
    }

    @Test fun recognizesLineAndBlockCommentsIncludingUnclosedBlock() {
        val source = "// line comment\nplugins { /* block comment */ id(\"x\") }"
        assertTrue(has(source, "// line comment", GradleTokenKind.COMMENT))
        assertTrue(has(source, "/* block comment */", GradleTokenKind.COMMENT))
        val unclosed = "plugins { /* not closed\nmore text"
        assertTrue(has(unclosed, "/* not closed\nmore text", GradleTokenKind.COMMENT))
    }

    @Test fun recognizesQuotedAndTripleQuotedStringsAndEscapes() {
        val source = "val a = 'single'; val b = \"escaped \\\"quote\\\"\"; val c = '''multi\\nline'''; val d = \"\"\"long\\ntext\"\"\""
        assertTrue(has(source, "'single'", GradleTokenKind.STRING))
        assertTrue(has(source, "\"escaped \\\"quote\\\"\"", GradleTokenKind.STRING))
        assertTrue(has(source, "'''multi\\nline'''", GradleTokenKind.STRING))
        assertTrue(has(source, "\"\"\"long\\ntext\"\"\"", GradleTokenKind.STRING))
    }

    @Test fun unfinishedStringsAreBoundedAndDoNotThrow() {
        val source = "val broken = \"unfinished\nval next = true"
        assertTrue(has(source, "\"unfinished", GradleTokenKind.STRING))
        assertTrue(has(source, "true", GradleTokenKind.BOOLEAN))
        val triple = "val broken = '''unfinished"
        assertTrue(has(triple, "'''unfinished", GradleTokenKind.STRING))
    }

    @Test fun recognizesNumbersAndLeavesMalformedNumericIdentifiersUncolored() {
        val source = "val a = 42; val b = 3.14; val c = 1.2e-3; val d = 0xFF; val e = 0b101; val f = 7L"
        for (number in listOf("42", "3.14", "1.2e-3", "0xFF", "0b101", "7L")) {
            assertTrue("number $number", has(source, number, GradleTokenKind.NUMBER))
        }
        assertFalse(has("val x = 123abc", "123abc", GradleTokenKind.NUMBER))
    }

    @Test fun emitsPunctuationAndOperatorsSeparately() {
        val source = "tasks.register(\"check\") { dependsOn(test) }; x += 2"
        assertTrue(has(source, "(", GradleTokenKind.PUNCTUATION))
        assertTrue(has(source, ")", GradleTokenKind.PUNCTUATION))
        assertTrue(has(source, "{", GradleTokenKind.PUNCTUATION))
        assertTrue(has(source, "}", GradleTokenKind.PUNCTUATION))
        assertTrue(has(source, ";", GradleTokenKind.PUNCTUATION))
        assertTrue(has(source, "+=", GradleTokenKind.OPERATOR))
    }

    @Test fun propertiesModeRecognizesCommentsKeysSeparatorsAndValues() {
        val source = "# hash comment\n! bang comment\norg.gradle.jvmargs=-Xmx2g\norg.gradle.parallel = true\nversion: 42\nname=value"
        val mode = GradleFileMode.PROPERTIES
        assertTrue(has(source, "# hash comment", GradleTokenKind.COMMENT, mode))
        assertTrue(has(source, "! bang comment", GradleTokenKind.COMMENT, mode))
        assertTrue(has(source, "org.gradle.jvmargs", GradleTokenKind.KEY, mode))
        assertTrue(has(source, "=", GradleTokenKind.OPERATOR, mode))
        assertTrue(has(source, "true", GradleTokenKind.BOOLEAN, mode))
        assertTrue(has(source, "42", GradleTokenKind.NUMBER, mode))
        assertTrue(has(source, "-Xmx2g", GradleTokenKind.STRING, mode))
        assertTrue(has(source, "value", GradleTokenKind.STRING, mode))
        assertTrue(has("hex=0x10\nbinary=0b101\nfraction=.75", "0x10", GradleTokenKind.NUMBER, mode))
        assertTrue(has("hex=0x10\nbinary=0b101\nfraction=.75", "0b101", GradleTokenKind.NUMBER, mode))
        assertTrue(has("hex=0x10\nbinary=0b101\nfraction=.75", ".75", GradleTokenKind.NUMBER, mode))
        assertFalse(has("dot=.", ".", GradleTokenKind.NUMBER, mode))
    }

    @Test fun propertiesModeHandlesMissingValuesAndFinalLineWithoutNewline() {
        val source = "empty=\nlast.property"
        val result = tokens(source, GradleFileMode.PROPERTIES)
        assertTrue(result.any { it.text == "empty" && it.kind == GradleTokenKind.KEY })
        assertTrue(result.any { it.text == "last.property" && it.kind == GradleTokenKind.KEY })
    }

    @Test fun malformedAndRandomInputsKeepTokenRangesValid() {
        val alphabet = "abcXYZ_0123{}[]():;,.=+-*/%?!&|<>@'\"\\\n\r# !"
        var seed = 328L
        repeat(400) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val length = ((seed ushr 32) and 0xFF).toInt()
            val source = buildString {
                repeat(length) {
                    seed = seed * 6364136223846793005L + 1442695040888963407L
                    append(alphabet[((seed ushr 32) and 0x7FFFFFFF).toInt() % alphabet.length])
                }
            }
            for (mode in GradleFileMode.values()) {
                var previousEnd = 0
                GradleSyntaxHighlighter.tokenize(source, mode) { start, end, _ ->
                    assertTrue("overlap start=$start previous=$previousEnd", start >= previousEnd)
                    assertTrue(end > start)
                    assertTrue(end <= source.length)
                    previousEnd = end
                }
            }
        }
    }

    @Test fun exactLengthLimitIsHighlightedAndOverLimitIsSkipped() {
        var count = 0
        val exact = "x".repeat(GradleSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH)
        GradleSyntaxHighlighter.tokenize("//" + exact.drop(2)) { _, _, _ -> count++ }
        assertTrue(count > 0)
        count = 0
        GradleSyntaxHighlighter.tokenize("//" + "x".repeat(GradleSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH - 1)) { _, _, _ -> count++ }
        assertEquals(0, count)
    }

    @Test fun longMalformedNumberRunDoesNotCauseRepeatedRescans() {
        val source = "1".repeat(90_000) + "notANumber"
        var count = 0
        GradleSyntaxHighlighter.tokenize(source) { _, _, _ -> count++ }
        assertEquals(0, count)
    }
}
