package com.jarforge.ide.feature.editor

import org.junit.Assert.*
import org.junit.Test

/** Covers indentation continuation and comment/string edge cases. */
class AutoIndentScenariosTest {
 private fun result(sourceAfterEnter:String,newlinePos:Int)=AutoIndent.computeEnter(sourceAfterEnter,newlinePos)
 @Test fun markdownScenarios(){
  assertNull(result("- item\n",6))
  assertEquals("\n    ",result("    text\n",8)?.text)
  assertEquals("\n    ",result("{\n",1)?.text)
  assertNull(result("```\ncode\n",8))
 }
 @Test fun commentBracesDoNotTriggerBlockIndentation(){
  assertNull(result("// {\n", 4))
  assertNull(result("# {\n", 4))
  assertEquals("\n    ", result("    // {\n", 8)?.text)
  assertEquals("\n    ", result("    value // {\n", 14)?.text)
  assertNull(result("String value = \"{\"\n", 18))
  assertNull(result("/* {\n", 4))
  assertEquals("\n    ", result("    /* {\n", 8)?.text)
 }
 @Test fun tomlScenarios(){
  assertEquals("\n  ",result("  key = 1\n",9)?.text)
  assertEquals("\n    ",result("x = {\n",5)?.text)
  assertNull(result("x = [\n",5))
 }
}
