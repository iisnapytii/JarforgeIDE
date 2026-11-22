package com.jarforge.ide.feature.editor

import org.junit.Assert.*
import org.junit.Test

/** Characterization only: locks current AutoIndent.computeEnter behavior without changing it. */
class AutoIndentScenariosTest {
 private fun result(sourceAfterEnter:String,newlinePos:Int)=AutoIndent.computeEnter(sourceAfterEnter,newlinePos)
 @Test fun markdownScenarios(){
  assertNull(result("- item\n",6))
  assertEquals("\n    ",result("    text\n",8)?.text)
  assertEquals("\n    ",result("{\n",1)?.text)
  assertNull(result("```\ncode\n",8))
 }
 @Test fun tomlScenarios(){
  assertEquals("\n  ",result("  key = 1\n",9)?.text)
  assertEquals("\n    ",result("x = {\n",5)?.text)
  assertNull(result("x = [\n",5))
 }
}
