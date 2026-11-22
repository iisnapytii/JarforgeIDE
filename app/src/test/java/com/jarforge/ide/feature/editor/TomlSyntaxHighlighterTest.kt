package com.jarforge.ide.feature.editor

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class TomlSyntaxHighlighterTest {
 private fun tokens(s:String)=buildList { TomlSyntaxHighlighter.tokenize(s){a,b,k->add(Triple(s.substring(a,b),k,a..(b-1)))} }
 private fun has(s:String,v:String,k:TomlTokenKind)=tokens(s).any{it.first==v&&it.second==k}
 @Test fun tablesKeysAndStrings(){
  assertTrue(has("[table]","[table]",TomlTokenKind.TABLE));assertTrue(has("[[array.table]]","[[array.table]]",TomlTokenKind.TABLE))
  for(k in listOf("foo","1234","true","a"))assertTrue(has("$k = 1",k,TomlTokenKind.KEY))
  assertTrue(has("'quoted key' = 1","'quoted key'",TomlTokenKind.KEY));assertTrue(has("a.b = 1","a",TomlTokenKind.KEY))
  for(v in listOf("\"basic\"","'literal'","\"say \\\"hi\\\"\"","\"\"\"multi\nline\"\"\"","'''literal\nblock'''"))assertTrue(v,tokens("x = $v").any{it.first==v&&it.second==TomlTokenKind.STRING})
 }
 @Test fun valuesAndDateTimes(){
  for(v in listOf("+1","-2","1_000","0xDEAD","0o755","0b101","1.25","1e+2","inf","nan","+inf","-nan"))assertTrue(v,has("x = $v",v,TomlTokenKind.NUMBER))
  assertTrue(has("x=true","true",TomlTokenKind.BOOLEAN));assertFalse(has("x=TRUE","TRUE",TomlTokenKind.BOOLEAN))
  for(v in listOf("1979-05-27T07:32:00Z","1979-05-27 07:32:00-07:00","1979-05-27 07:32:00.999","1979-05-27","07:32:00"))assertTrue(v,has("x = $v",v,TomlTokenKind.DATETIME))
  assertTrue(has("x = 1979-05-27 #note","1979-05-27",TomlTokenKind.DATETIME))
  assertTrue(has("x=1979-05-27t07:32:00z","1979-05-27t07:32:00z",TomlTokenKind.DATETIME))
  assertTrue(has("x=[1979-05-27 07:32:00, 1980-01-01]","1979-05-27 07:32:00",TomlTokenKind.DATETIME))
  assertTrue(has("x = 1979-05-27 nope","1979-05-27",TomlTokenKind.DATETIME))
  assertFalse(has("x = 1979-05-27 nope","1979-05-27 nope",TomlTokenKind.DATETIME))
 }
 @Test fun commentsArraysAndInlineTables(){
  assertTrue(has("x=1 # hi","# hi",TomlTokenKind.COMMENT));assertTrue(has("x=[1,[2,3]]","1",TomlTokenKind.NUMBER))
  assertTrue(has("x={a=1,b={c=2}}","a",TomlTokenKind.KEY));assertTrue(has("x={a=1}","=",TomlTokenKind.OPERATOR))
  assertTrue(has("config = { enabled = true }","enabled",TomlTokenKind.KEY))
  assertTrue(has("config = { enabled = true }","true",TomlTokenKind.BOOLEAN))
  assertTrue(has("config = { enabled = false, count = 3 }","false",TomlTokenKind.BOOLEAN))
  assertTrue(has("config = { nested = { active = true } }","true",TomlTokenKind.BOOLEAN))
  assertTrue(tokens("x=[1\ny=2").any{it.first=="y"&&it.second==TomlTokenKind.KEY})
 }
 @Test fun invariantsLfCrLfAndLimits(){
  val lf="x = 1979-05-27 07:32:00Z\ny = [1, 2]";val crlf=lf.replace("\n","\r\n")
  assertEquals(tokens(lf).map{it.first to it.second},tokens(crlf).map{it.first to it.second})
  val r=Random(42);repeat(300){val s=buildString{repeat(r.nextInt(250)){append("abc012_=[]{}#'\" +-.,\n"[r.nextInt(19)])}};var last=0;TomlSyntaxHighlighter.tokenize(s){a,b,_->assertTrue(a>=last);assertTrue(b>a&&b<=s.length);last=b}}
  for(n in listOf(99_999,100_000)){var count=0;TomlSyntaxHighlighter.tokenize("key="+"1".repeat(n-4)){_,_,_->count++};assertTrue(count>0)}
  var count=0;TomlSyntaxHighlighter.tokenize("key="+"1".repeat(100_001-4)){_,_,_->count++};assertEquals(0,count)
 }
}
