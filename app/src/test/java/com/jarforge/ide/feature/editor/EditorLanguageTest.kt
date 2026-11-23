package com.jarforge.ide.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLanguageTest {
 @Test fun extensions() {
  assertEquals(EditorLanguage.JAVA,EditorLanguage.fromPath("src/Main.java"))
  assertEquals(EditorLanguage.TOML,EditorLanguage.fromPath("mods.toml"))
  assertEquals(EditorLanguage.TOML,EditorLanguage.fromPath("neoforge.mods.toml"))
  assertEquals(EditorLanguage.TOML,EditorLanguage.fromPath("libs.versions.toml"))
  assertEquals(EditorLanguage.MARKDOWN,EditorLanguage.fromPath("README.md"))
  assertEquals(EditorLanguage.JAVA,EditorLanguage.fromPath("A.JAVA"))
  assertEquals(EditorLanguage.TOML,EditorLanguage.fromPath("A.TOML"))
  assertEquals(EditorLanguage.MARKDOWN,EditorLanguage.fromPath("A.MD"))
  assertEquals(EditorLanguage.JSON,EditorLanguage.fromPath("DATA.JSON"))
  assertEquals(EditorLanguage.PLAIN,EditorLanguage.fromPath("noext"))
  assertEquals(EditorLanguage.PLAIN,EditorLanguage.fromPath("file.json.bak"))
  assertEquals(EditorLanguage.PLAIN,EditorLanguage.fromPath("x.java.bak"))
  assertEquals(EditorLanguage.PLAIN,EditorLanguage.fromPath("x.markdown"))
 }
 @Test fun allSyntaxHighlightersShareTheExactSizeBoundary() {
  for(n in listOf(99_999,100_000)) {
   assertEquals(100_000,JavaSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH)
   var jsonCount=0; JsonSyntaxHighlighter.tokenize("["+"1".repeat(n-2)+"]"){_,_,_->jsonCount++}; assertTrue(jsonCount>0)
   var tomlCount=0; TomlSyntaxHighlighter.tokenize("key="+"1".repeat(n-4)){_,_,_->tomlCount++}; assertTrue(tomlCount>0)
   var mdCount=0; MarkdownSyntaxHighlighter.tokenize("# "+"x".repeat(n-2)){_,_,_->mdCount++}; assertEquals(1,mdCount)
  }
  assertEquals(100_000,JavaSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH)
  assertEquals(100_000,JsonSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH)
 }
}
