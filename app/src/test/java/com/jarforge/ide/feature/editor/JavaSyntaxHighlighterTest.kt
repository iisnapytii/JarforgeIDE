package com.jarforge.ide.feature.editor

import com.jarforge.ide.feature.editor.JavaTokenKind.ANNOTATION
import com.jarforge.ide.feature.editor.JavaTokenKind.CHAR
import com.jarforge.ide.feature.editor.JavaTokenKind.COMMENT
import com.jarforge.ide.feature.editor.JavaTokenKind.KEYWORD
import com.jarforge.ide.feature.editor.JavaTokenKind.NUMBER
import com.jarforge.ide.feature.editor.JavaTokenKind.OPERATOR
import com.jarforge.ide.feature.editor.JavaTokenKind.STRING
import com.jarforge.ide.feature.editor.JavaTokenKind.TYPE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Tests of the pure Java tokenizer. They need no Android or Compose classes. */
class JavaSyntaxHighlighterTest {

    /** Tokens as (text of the token, kind), in order. */
    private fun tokens(source: String): List<Pair<String, JavaTokenKind>> {
        val result = ArrayList<Pair<String, JavaTokenKind>>()
        JavaSyntaxHighlighter.tokenize(source) { start, end, kind ->
            result.add(source.substring(start, end) to kind)
        }
        return result
    }

    private fun kindsOf(source: String, word: String): List<JavaTokenKind> =
        tokens(source).filter { it.first == word }.map { it.second }

    // ---- keywords and types -------------------------------------------------------------

    @Test
    fun keywords() {
        assertEquals(
            listOf("public" to KEYWORD, "static" to KEYWORD, "void" to TYPE, "return" to KEYWORD),
            tokens("public static void return").filter { it.second != OPERATOR }
        )
        for (word in listOf("true", "false", "null", "this", "super", "new", "var", "instanceof")) {
            assertEquals(word, listOf(word to KEYWORD), tokens(word))
        }
    }

    @Test
    fun primitiveTypesAreTypes() {
        for (word in listOf("int", "long", "double", "float", "boolean", "byte", "char", "short", "void")) {
            assertEquals(word, listOf(word to TYPE), tokens(word))
        }
    }

    @Test
    fun commonAndPascalCaseTypes() {
        assertEquals(listOf("String" to TYPE), tokens("String"))
        assertEquals(listOf("ItemStack" to TYPE), tokens("ItemStack"))
        assertEquals(listOf("UUID" to TYPE), tokens("UUID"))
        assertEquals(listOf("Map" to TYPE), tokens("Map"))
    }

    @Test
    fun ordinaryIdentifiersStayPlain() {
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("counter"))
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("myVariable"))
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("getValue"))
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("_private"))
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("\$dollar"))
        // Constants are not types.
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("MOD_ID"))
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("MAX_VALUE"))
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("T"))
    }

    @Test
    fun keywordsInsideLongerIdentifiersAreNotKeywords() {
        for (word in listOf("classes", "newValue", "iffy", "int2", "returnValue", "format", "interfaceX", "this_", "nullable", "forEach")) {
            assertEquals(word, emptyList<Pair<String, JavaTokenKind>>(), tokens(word))
        }
        // A reserved word after a dot is still lexed as one: nothing to do with context.
        assertEquals(listOf("class" to KEYWORD), tokens("class"))
    }

    // ---- strings and characters ---------------------------------------------------------

    @Test
    fun simpleString() {
        assertEquals(listOf("\"hello\"" to STRING), tokens("\"hello\""))
    }

    @Test
    fun escapedQuoteDoesNotEndString() {
        val s = "\"say \\\"hi\\\" now\""
        assertEquals(listOf(s to STRING), tokens(s))
    }

    @Test
    fun escapedBackslashBeforeQuoteEndsString() {
        // "a\\" is a complete string; the number after it must be seen as a number.
        val tokens = tokens("\"a\\\\\" 5")
        assertEquals(listOf("\"a\\\\\"" to STRING, "5" to NUMBER), tokens)
    }

    @Test
    fun keywordsInsideStringsAreNotHighlightedSeparately() {
        assertEquals(listOf("\"if else 12 // x\"" to STRING), tokens("\"if else 12 // x\""))
    }

    @Test
    fun unterminatedStringEndsAtLineEnd() {
        assertEquals(
            listOf("\"abc" to STRING, "int" to TYPE),
            tokens("\"abc\nint")
        )
        assertEquals(
            listOf("\"abc" to STRING, "int" to TYPE),
            tokens("\"abc\r\nint")
        )
    }

    @Test
    fun emptyString() {
        assertEquals(listOf("\"\"" to STRING, "+" to OPERATOR, "1" to NUMBER), tokens("\"\"+1"))
    }

    @Test
    fun characterLiterals() {
        assertEquals(listOf("'a'" to CHAR), tokens("'a'"))
        assertEquals(listOf("'\\n'" to CHAR), tokens("'\\n'"))
        assertEquals(listOf("'\\''" to CHAR), tokens("'\\''"))
        assertEquals(listOf("'\\u0041'" to CHAR), tokens("'\\u0041'"))
        assertEquals(listOf("'\"'" to CHAR), tokens("'\"'"))
    }

    @Test
    fun quoteInsideCharAndApostropheInStringDoNotConfuse() {
        assertEquals(
            listOf("\"it's\"" to STRING, "'\"'" to CHAR),
            tokens("\"it's\" '\"'")
        )
    }

    @Test
    fun textBlock() {
        val block = "\"\"\"\n  hello \"quoted\"\n  // not a comment\n  \"\"\""
        assertEquals(listOf(block to STRING, "int" to TYPE), tokens("$block int"))
    }

    @Test
    fun unterminatedTextBlockRunsToEnd() {
        val src = "\"\"\"\nabc\nint"
        assertEquals(listOf(src to STRING), tokens(src))
    }

    // ---- comments -----------------------------------------------------------------------

    @Test
    fun lineComment() {
        assertEquals(
            listOf("int" to TYPE, "// a \"b\" /* c */" to COMMENT, "long" to TYPE),
            tokens("int // a \"b\" /* c */\nlong")
        )
    }

    @Test
    fun lineCommentStopsBeforeCarriageReturn() {
        assertEquals(
            listOf("// x" to COMMENT, "int" to TYPE),
            tokens("// x\r\nint")
        )
    }

    @Test
    fun blockCommentSpansLines() {
        val comment = "/* line one\n   int x = 5; // inside\n*/"
        assertEquals(listOf(comment to COMMENT, "int" to TYPE), tokens("$comment int"))
    }

    @Test
    fun javadocIsAComment() {
        val doc = "/**\n * @param x the value\n */"
        assertEquals(listOf(doc to COMMENT), tokens(doc))
    }

    @Test
    fun unterminatedBlockCommentRunsToEnd() {
        val src = "/* never closed\nint x"
        assertEquals(listOf(src to COMMENT), tokens(src))
    }

    @Test
    fun slashStarSlashDoesNotCloseItself() {
        val src = "/*/ int */ long"
        assertEquals(listOf("/*/ int */" to COMMENT, "long" to TYPE), tokens(src))
    }

    @Test
    fun commentMarkersInsideStringsAreStrings() {
        assertEquals(listOf("\"http://x\"" to STRING), tokens("\"http://x\""))
        assertEquals(listOf("\"/* x */\"" to STRING), tokens("\"/* x */\""))
    }

    @Test
    fun divisionIsNotAComment() {
        assertEquals(
            listOf("1" to NUMBER, "/" to OPERATOR, "2" to NUMBER),
            tokens("1 / 2")
        )
    }

    @Test
    fun operatorRunStopsAtCommentStart() {
        assertEquals(
            listOf("=" to OPERATOR, "// c" to COMMENT),
            tokens("=// c")
        )
        assertEquals(
            listOf("+" to OPERATOR, "/* c */" to COMMENT),
            tokens("+/* c */")
        )
    }

    // ---- numbers ------------------------------------------------------------------------

    @Test
    fun numbers() {
        for (n in listOf(
            "0", "42", "1_000_000", "100L", "3.14", "3.14f", "2d", ".5", "1.", "1.e3",
            "1e10", "1.5e-3", "1E+9", "1.e-3", "1.F", "0xFF", "0XCAFE_BABEL",
            "0x1.0p3", "0x1.fp-2", "0x.8p1", "0x1p3F", "0X1.FP+2D",
            "0b1010", "0B1_0", "017"
        )) {
            assertEquals(n, listOf(n to NUMBER), tokens(n))
        }
    }

    @Test
    fun dotAfterIndexIsNotPartOfNumber() {
        assertEquals(
            listOf("0" to NUMBER),
            tokens("a[0].length")
        )
    }

    @Test
    fun digitsInsideIdentifiersAreNotNumbers() {
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("value2"))
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("x1y2"))
    }

    @Test
    fun numberFollowedByDotCall() {
        // Java's longest-match tokenization treats the trailing dot as part of the literal.
        assertEquals(listOf("1." to NUMBER), tokens("1.toString"))
    }

    @Test
    fun decimalAndHexadecimalFloatingPointLiterals() {
        for (literal in listOf("1.", "1.e3", "0x1.0p3", "0x1.fp-2", "0x.8p1", "0x1p3F", "0X1.FP+2D")) {
            assertEquals(literal, listOf(literal to NUMBER), tokens(literal))
        }
    }

    // ---- operators ----------------------------------------------------------------------

    @Test
    fun operators() {
        for (op in listOf("+", "-", "*", "%", "==", "!=", "<=", ">=", "&&", "||", "->", "::", "+=", ">>>=", "?", ":", "!", "~", "^")) {
            assertEquals(op, listOf(op to OPERATOR), tokens(op))
        }
    }

    @Test
    fun punctuationIsPlain() {
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("(){}[];,."))
    }

    // ---- annotations --------------------------------------------------------------------

    @Test
    fun annotations() {
        assertEquals(listOf("@Override" to ANNOTATION), tokens("@Override"))
        assertEquals(
            listOf("@SuppressWarnings" to ANNOTATION, "\"unchecked\"" to STRING),
            tokens("@SuppressWarnings(\"unchecked\")")
        )
        assertEquals(listOf("@net.example.Marker" to ANNOTATION), tokens("@net.example.Marker"))
        assertEquals(listOf("@interface" to KEYWORD, "Marker" to TYPE), tokens("@interface Marker"))
    }

    @Test
    fun loneAtSignIsPlain() {
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("@"))
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens("@ "))
        assertEquals(listOf("1" to NUMBER), tokens("@1"))
    }

    @Test
    fun annotationNameStopsAtParenthesis() {
        assertEquals(
            listOf("@Mod" to ANNOTATION, "\"id\"" to STRING),
            tokens("@Mod(\"id\")")
        )
    }

    // ---- whole snippets and robustness --------------------------------------------------

    @Test
    fun realisticSnippet() {
        val src = """
            @Override
            public void onInitialize() {
                int x = 0x1F + 2; // count
                String s = "a\"b";
            }
        """.trimIndent()
        assertEquals(
            listOf(
                "@Override" to ANNOTATION,
                "public" to KEYWORD, "void" to TYPE,
                "int" to TYPE, "=" to OPERATOR, "0x1F" to NUMBER, "+" to OPERATOR, "2" to NUMBER,
                "// count" to COMMENT,
                "String" to TYPE, "=" to OPERATOR, "\"a\\\"b\"" to STRING
            ),
            tokens(src)
        )
    }

    @Test
    fun crlfSourceGivesSameTokensAsLf() {
        val lf = "int a = 1; // c\nString s = \"x\";\n/* m\nn */ long b;\n"
        assertEquals(tokens(lf).map { it.second }, tokens(lf.replace("\n", "\r\n")).map { it.second })
    }

    @Test
    fun emptyTextHasNoTokens() {
        assertEquals(emptyList<Pair<String, JavaTokenKind>>(), tokens(""))
    }

    @Test
    fun tokensAreOrderedNonEmptyAndInsideTheText() {
        val random = Random(42)
        val alphabet = "ab_Z09 \t\n\r\"'\\/*+-=<>!&|@.{}();,:?xe_LfFdD#é"
        repeat(300) {
            val length = random.nextInt(120)
            val sb = StringBuilder()
            repeat(length) { sb.append(alphabet[random.nextInt(alphabet.length)]) }
            val src = sb.toString()
            var previousEnd = 0
            JavaSyntaxHighlighter.tokenize(src) { start, end, _ ->
                assertTrue("empty token in <$src>", end > start)
                assertTrue("overlap or disorder in <$src>", start >= previousEnd)
                assertTrue("out of bounds in <$src>", end <= src.length)
                previousEnd = end
            }
        }
    }

    @Test
    fun largeInputIsFast() {
        val line = "public static final String NAME = \"value\"; // comment with 123\n"
        val src = line.repeat(MAX_SIZE / line.length)
        val started = System.nanoTime()
        var count = 0
        JavaSyntaxHighlighter.tokenize(src) { _, _, _ -> count++ }
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue("too many tokens: $count", count > 0)
        // Generous bound for a slow CI machine; real time is a few milliseconds.
        assertTrue("took $millis ms", millis < 2_000)
    }

    private companion object {
        const val MAX_SIZE = JavaSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH
    }
}
