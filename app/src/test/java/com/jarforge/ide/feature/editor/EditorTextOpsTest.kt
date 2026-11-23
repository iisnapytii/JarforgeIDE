package com.jarforge.ide.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorTextOpsTest {
    @Test fun insertReplacesSelectionAndPlacesCaretAfterInsertedText() {
        val edit = EditorTextOps.insertText("XYZ", 1, 4, 6)
        assertEquals(listOf(TextReplacement(1, 4, "XYZ")), edit.replacements)
        assertEquals(4, edit.selectionStart)
        assertEquals(4, edit.selectionEnd)
    }

    @Test fun insertClampsOffsetsAndSupportsReversedSelection() {
        val edit = EditorTextOps.insertText("!", 9, -2, 4)
        assertEquals(listOf(TextReplacement(0, 4, "!")), edit.replacements)
        assertEquals(1, edit.selectionStart)
        assertEquals(1, edit.selectionEnd)
    }

    @Test fun pairWrapsSelectionInDescendingReplacementOrder() {
        val edit = EditorTextOps.insertPair("(", ")", 2, 5, 8)
        assertEquals(
            listOf(TextReplacement(5, 5, ")"), TextReplacement(2, 2, "(")),
            edit.replacements
        )
        assertEquals(3, edit.selectionStart)
        assertEquals(6, edit.selectionEnd)
    }

    @Test fun pairPreservesReversedSelectionDirection() {
        val edit = EditorTextOps.insertPair("[", "]", 5, 2, 8)
        assertEquals(6, edit.selectionStart)
        assertEquals(3, edit.selectionEnd)
    }

    @Test fun pairPlacesCaretBetweenSymbolsWhenSelectionIsCollapsed() {
        val edit = EditorTextOps.insertPair("{", "}", 3, 3, 6)
        assertEquals(listOf(TextReplacement(3, 3, "{}")), edit.replacements)
        assertEquals(4, edit.selectionStart)
        assertEquals(4, edit.selectionEnd)
    }
}
