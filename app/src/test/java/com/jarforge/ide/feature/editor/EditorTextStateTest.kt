package com.jarforge.ide.feature.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
class EditorTextStateTest {
    @Test fun loadReplacesTextPlacesCursorAtStartAndClearsUndoHistory() {
        val state = EditorTextState()
        state.load("before")
        state.fieldState.edit { replace(0, 0, "x") }
        assertTrue(state.canUndo)

        state.load("after")
        assertEquals("after", state.currentText())
        assertEquals(TextRange(0), state.fieldState.selection)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test fun replaceIfDifferentKeepsSelectionAndHistoryForIdenticalText() {
        val state = EditorTextState()
        state.load("abc")
        state.fieldState.edit { selection = TextRange(2) }
        state.fieldState.edit { replace(2, 2, "!") }
        assertTrue(state.canUndo)
        val selection = state.fieldState.selection

        state.replaceIfDifferent(state.currentText())

        assertEquals(selection, state.fieldState.selection)
        assertTrue(state.canUndo)
    }

    @Test fun replaceIfDifferentResetsHistoryWhenContentChanges() {
        val state = EditorTextState()
        state.load("abc")
        state.fieldState.edit { replace(0, 0, "!") }
        state.replaceIfDifferent("replacement")
        assertEquals("replacement", state.currentText())
        assertFalse(state.canUndo)
    }

    @Test fun undoAndRedoRestoreText() {
        val state = EditorTextState()
        state.load("abc")
        state.fieldState.edit { replace(3, 3, "!") }
        assertEquals("abc!", state.currentText())
        state.undo()
        assertEquals("abc", state.currentText())
        assertTrue(state.canRedo)
        state.redo()
        assertEquals("abc!", state.currentText())
    }

    @Test fun symbolInsertionsAreRejectedWhenUtf8LimitWouldBeExceeded() {
        val maxBytes = 4
        val state = EditorTextState(maxUtf8Bytes = maxBytes)
        state.load("😀")
        state.fieldState.edit { placeCursorAtEnd() }

        assertFalse(state.insertText("a"))
        assertFalse(state.insertPair("(", ")"))
        assertEquals("😀", state.currentText())

        state.fieldState.edit { selection = TextRange(0, 2) }
        assertTrue(state.insertText("abc"))
        assertEquals("abc", state.currentText())
    }

    @Test fun symbolPairInsertionIsOneUndoableEdit() {
        val state = EditorTextState()
        state.load("abc")
        state.fieldState.edit { selection = TextRange(1, 2) }
        assertTrue(state.insertPair("(", ")"))
        assertEquals("a(b)c", state.currentText())
        state.undo()
        assertEquals("abc", state.currentText())
    }
}
