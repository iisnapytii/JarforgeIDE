package com.jarforge.ide.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class JsonSyntaxHighlighterTest {
    private data class Token(val text: String, val kind: JsonTokenKind, val start: Int, val end: Int)

    private fun tokens(source: String): List<Token> = buildList {
        JsonSyntaxHighlighter.tokenize(source) { start, end, kind ->
            add(Token(source.substring(start, end), kind, start, end))
        }
    }

    private fun has(source: String, value: String, kind: JsonTokenKind): Boolean =
        tokens(source).any { it.text == value && it.kind == kind }

    @Test fun objectKeysRequireObjectKeySlotAndColon() {
        assertTrue(has("{\"name\": \"Alex\"}", "\"name\"", JsonTokenKind.OBJECT_KEY))
        assertTrue(has("{ \"name\"  : 1 }", "\"name\"", JsonTokenKind.OBJECT_KEY))
        assertTrue(has("{\"nested\": {\"active\": true}}", "\"nested\"", JsonTokenKind.OBJECT_KEY))
        assertTrue(has("{\"array\": [{\"inside\": 1}]}", "\"inside\"", JsonTokenKind.OBJECT_KEY))
        assertTrue(has("{\"x\": [\"not a key\", {\"y\": 2}]}", "\"not a key\"", JsonTokenKind.STRING))
        assertFalse(has("[\"not a key\": 1]", "\"not a key\"", JsonTokenKind.OBJECT_KEY))
        assertTrue(has("{\"x\": \"value\", \"y\": 2}", "\"y\"", JsonTokenKind.OBJECT_KEY))
    }

    @Test fun escapedQuotesAndBackslashesAreScannedCorrectly() {
        val source = "{\"quoted\\\"key\": \"say \\\"hello\\\"\", \"slash\": \"\\\\\"}"
        assertTrue(has(source, "\"quoted\\\"key\"", JsonTokenKind.OBJECT_KEY))
        assertTrue(has(source, "\"say \\\"hello\\\"\"", JsonTokenKind.STRING))
        assertTrue(has(source, "\"\\\\\"", JsonTokenKind.STRING))
    }

    @Test fun unclosedStringsStopAtPhysicalLineBoundary() {
        val source = "{\"broken: text\n, \"valid\": true}"
        val result = tokens(source)
        assertTrue(result.any { it.text == "\"broken: text" && it.kind == JsonTokenKind.STRING })
        assertTrue(result.any { it.text == "\"valid\"" && it.kind == JsonTokenKind.OBJECT_KEY })
        assertTrue(result.any { it.text == "true" && it.kind == JsonTokenKind.BOOLEAN })
        assertFalse(result.any { it.text.contains("valid") && it.kind == JsonTokenKind.STRING })

        val crlf = "{\"broken\r\n, \"ok\": null}"
        assertTrue(has(crlf, "\"ok\"", JsonTokenKind.OBJECT_KEY))

        val escapedLineBreak = "{\"bad\": \"text\\\n, \"good\": 1}"
        assertTrue(has(escapedLineBreak, "\"text\\", JsonTokenKind.STRING))
        assertTrue(has(escapedLineBreak, "\"good\"", JsonTokenKind.OBJECT_KEY))
    }

    @Test fun unclosedStringAtEofIsBoundedByEndOfInput() {
        val source = "{\"broken\": \"unterminated"
        val result = tokens(source)
        val last = result.last()
        assertEquals(source.length, last.end)
        assertEquals(JsonTokenKind.STRING, last.kind)
        assertEquals("\"unterminated", last.text)
    }

    @Test fun literalsAndStrictJsonNumbersAreClassified() {
        for (value in listOf("true", "false")) assertTrue(has("[$value]", value, JsonTokenKind.BOOLEAN))
        assertTrue(has("[null]", "null", JsonTokenKind.NULL))
        for (value in listOf("0", "-0", "12", "-12.5", "1e10", "-2.5E-3")) {
            assertTrue("$value", has("[$value]", value, JsonTokenKind.NUMBER))
        }
        for (value in listOf("01", "+1", ".5", "1.", "1e", "--1")) {
            assertFalse("$value", has("[$value]", value, JsonTokenKind.NUMBER))
        }
    }

    @Test fun punctuationAndStringsAreEmittedAsNonOverlappingRanges() {
        val source = "{\"a\":[1, {\"b\":\"c\"}]}"
        val result = tokens(source)
        assertTrue(result.any { it.text == "{" && it.kind == JsonTokenKind.PUNCTUATION })
        assertTrue(result.any { it.text == ":" && it.kind == JsonTokenKind.PUNCTUATION })
        assertTrue(result.any { it.text == "," && it.kind == JsonTokenKind.PUNCTUATION })
        var previousEnd = 0
        for (token in result) {
            assertTrue("overlap at $token", token.start >= previousEnd)
            assertTrue(token.end > token.start)
            assertTrue(token.end <= source.length)
            previousEnd = token.end
        }
    }

    @Test fun keyLookingStringsInValuesAndArraysAreNotMisclassified() {
        val source = "{\"value\": [\"x\", \"looksLikeKey\"], \"next\": {\"inner\": 3}}"
        assertTrue(has(source, "\"looksLikeKey\"", JsonTokenKind.STRING))
        assertFalse(has(source, "\"looksLikeKey\"", JsonTokenKind.OBJECT_KEY))
        assertTrue(has(source, "\"next\"", JsonTokenKind.OBJECT_KEY))
        assertTrue(has(source, "\"inner\"", JsonTokenKind.OBJECT_KEY))
    }

    @Test fun maximumNestingDepthIsBoundedAndOverflowDoesNotGrowStack() {
        val within = "[".repeat(JsonSyntaxHighlighter.MAX_NESTING_DEPTH) + "0" + "]".repeat(JsonSyntaxHighlighter.MAX_NESTING_DEPTH)
        assertTrue(tokens(within).any { it.text == "0" && it.kind == JsonTokenKind.NUMBER })

        val beyond = "[".repeat(JsonSyntaxHighlighter.MAX_NESTING_DEPTH + 10) +
            "{\"deepKey\": true}" +
            "]".repeat(JsonSyntaxHighlighter.MAX_NESTING_DEPTH + 10)
        val result = tokens(beyond)
        assertTrue(result.any { it.text == "{" && it.kind == JsonTokenKind.PUNCTUATION })
        assertTrue(result.any { it.text == "}" && it.kind == JsonTokenKind.PUNCTUATION })
        assertFalse(result.any { it.text == "\"deepKey\"" && it.kind == JsonTokenKind.OBJECT_KEY })
    }

    @Test fun documentsOverHighlightLimitAreNotTokenized() {
        var count = 0
        JsonSyntaxHighlighter.tokenize("[" + "1".repeat(JsonSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH - 2) + "]") { _, _, _ -> count++ }
        assertTrue("the exact 100,000-character boundary must be highlighted", count > 0)

        count = 0
        JsonSyntaxHighlighter.tokenize("[" + "1".repeat(JsonSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH - 1) + "]") { _, _, _ -> count++ }
        assertEquals(0, count)

        assertEquals(100_000, JsonSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH)
    }

    @Test fun randomInputsPreserveTokenRangeInvariants() {
        val random = Random(327)
        val alphabet = "{}[],:\"\\0123456789.truefalsnul abc\r\n+-eE"
        repeat(500) {
            val source = buildString {
                repeat(random.nextInt(400)) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
            var previousEnd = 0
            JsonSyntaxHighlighter.tokenize(source) { start, end, _ ->
                assertTrue("start=$start previous=$previousEnd source=$source", start >= previousEnd)
                assertTrue(end > start)
                assertTrue(end <= source.length)
                previousEnd = end
            }
        }
    }

    @Test fun largeInputTokenizationRemainsLinearAndBounded() {
        val source = "{\"key\":123,\"value\":true}\n".repeat(4_000)
        assertTrue(source.length <= JsonSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH)
        val started = System.nanoTime()
        val result = tokens(source)
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue(result.isNotEmpty())
        assertTrue("JSON tokenizer took $elapsedMillis ms", elapsedMillis < 2_000)
    }
}
