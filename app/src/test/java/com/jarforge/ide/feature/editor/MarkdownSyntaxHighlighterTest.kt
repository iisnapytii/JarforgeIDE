package com.jarforge.ide.feature.editor

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class MarkdownSyntaxHighlighterTest {
 private fun tokens(s:String)=buildList { MarkdownSyntaxHighlighter.tokenize(s){a,b,k->add(Triple(s.substring(a,b),k,a to b))} }
 private fun has(s:String,v:String,k:MarkdownTokenKind)=tokens(s).any{it.first==v&&it.second==k}
 @Test fun blockConstructs(){
  assertTrue(has("### title","### title",MarkdownTokenKind.HEADING));assertTrue(has("---","---",MarkdownTokenKind.RULE));assertTrue(has("- - -","- - -",MarkdownTokenKind.RULE))
  assertTrue(has("> quote",">",MarkdownTokenKind.QUOTE));for(m in listOf("- ","* ","+ ","1. ","2) "))assertTrue(m,has(m+"item",m.trimEnd(),MarkdownTokenKind.LIST_MARKER))
 }
 @Test fun inlineConstructs(){
  assertTrue(has("`code`","`code`",MarkdownTokenKind.CODE_INLINE));assertTrue(has("[text](url)","text",MarkdownTokenKind.LINK_TEXT));assertTrue(has("[text](url)","url",MarkdownTokenKind.LINK_URL))
  assertTrue(has("![alt](img.png)","alt",MarkdownTokenKind.LINK_TEXT));assertTrue(has("[ref]: url","[ref]",MarkdownTokenKind.LINK_TEXT));assertTrue(has("[ref]: url","url",MarkdownTokenKind.LINK_URL))
  assertTrue(has("<https://example.com>","<https://example.com>",MarkdownTokenKind.LINK_URL))
  assertTrue(has("~~gone~~","~~gone~~",MarkdownTokenKind.STRIKE));assertTrue(has("**bold**","**bold**",MarkdownTokenKind.BOLD));assertTrue(has("_italic_","_italic_",MarkdownTokenKind.ITALIC));assertTrue(has("***both***","***both***",MarkdownTokenKind.BOLD));assertTrue(has("__bold__","__bold__",MarkdownTokenKind.BOLD));assertTrue(has("_italic_","_italic_",MarkdownTokenKind.ITALIC));assertTrue(has("___both___","___both___",MarkdownTokenKind.BOLD))
  assertFalse(has("a_b_c","_b_",MarkdownTokenKind.ITALIC));assertFalse(has("\\*escaped*","*escaped*",MarkdownTokenKind.ITALIC))
 }
 @Test fun fencedBlocks(){
  assertTrue(has("```kotlin\nx\n```","```kotlin",MarkdownTokenKind.CODE_FENCE));assertTrue(has("```kotlin\nx\n````  \t\n","````",MarkdownTokenKind.CODE_FENCE));assertTrue(has("~~~ ` info\nx\n~~~","~~~ ` info",MarkdownTokenKind.CODE_FENCE))
  assertTrue(has("```\ncode\n```","code",MarkdownTokenKind.CODE_BLOCK))
  for(close in listOf("``x","~~~","```` tail","    ````","\t````")){val s="````\nbody\n$close\nafter";assertFalse(close,tokens(s).any{it.first=="after"&&it.second!=MarkdownTokenKind.CODE_BLOCK})}
  assertTrue(tokens("```\nunfinished").any{it.first=="unfinished"&&it.second==MarkdownTokenKind.CODE_BLOCK})
  assertFalse(tokens("> ```\nx\n> ```").any{it.first=="x"&&it.second==MarkdownTokenKind.CODE_BLOCK})
  assertFalse(tokens("- ```\nx\n- ```").any{it.first=="x"&&it.second==MarkdownTokenKind.CODE_BLOCK})
  assertFalse(has("http://example.com","http://example.com",MarkdownTokenKind.LINK_URL))
  assertFalse(has("[x][ref]","x",MarkdownTokenKind.LINK_TEXT))
  assertFalse(has("- [ ] task","[ ]",MarkdownTokenKind.LINK_TEXT))
  assertFalse(tokens("```bad`info\nx\n```").any{it.first=="x"&&it.second==MarkdownTokenKind.CODE_BLOCK})
  assertFalse(tokens("\t```\nx\n```").any{it.first=="x"&&it.second==MarkdownTokenKind.CODE_BLOCK})
 }
 @Test fun largeInputIsFast(){
  val line = "# heading\n- item\n**bold**\n"
  val src = line.repeat(MarkdownSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH / line.length)
  val started = System.nanoTime()
  var count = 0
  MarkdownSyntaxHighlighter.tokenize(src){_,_,_->count++}
  val millis = (System.nanoTime() - started) / 1_000_000
  assertTrue("expected tokens", count > 0)
  assertTrue("Markdown tokenization took $millis ms", millis < 2_000)
 }
 @Test fun limitsLfCrLfAndFuzz(){
  val lf="# hi\n- item\n```\nx\n```";val crlf=lf.replace("\n","\r\n")
  assertEquals(tokens(lf).map{it.first to it.second},tokens(crlf).map{it.first to it.second})
  val r=Random(7);repeat(300){val s=buildString{repeat(r.nextInt(200)){append("#*_~`[]()!<> abc\\\n"[r.nextInt(18)])}};var last=0;MarkdownSyntaxHighlighter.tokenize(s){a,b,_->assertTrue(a>=last);assertTrue(b>a&&b<=s.length);last=b}}
  for(n in listOf(99_999,100_000)){var count=0;MarkdownSyntaxHighlighter.tokenize("# "+"x".repeat(n-2)){_,_,_->count++};assertTrue(count>0)}
  var count=0;MarkdownSyntaxHighlighter.tokenize("# "+"x".repeat(99_999)){_,_,_->count++};assertEquals(0,count)
 }
}
