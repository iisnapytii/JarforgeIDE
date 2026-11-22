package com.jarforge.ide.feature.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Owns the live text, selection and undo history of the file open in the editor.
 *
 * The [fieldState] is the single owner of text and cursor/selection while the user types.
 * [EditorUiState.editorContent] is only a mirror of the text, kept for unsaved-changes
 * tracking and saving. The ViewModel writes into this object only when a file is loaded,
 * reset or discarded, never while the user is typing.
 *
 * Undo and Redo use the built-in history of [fieldState]. The text they change reaches the
 * ViewModel through [textChanges] like any other edit, so nothing is written back.
 *
 * All functions that change text must be called on the main thread.
 */
@OptIn(ExperimentalFoundationApi::class)
class EditorTextState {

    val fieldState = TextFieldState()

    /** The current text as an immutable String. */
    fun currentText(): String = fieldState.text.toString()

    /**
     * Replaces the whole text, puts the cursor at the start and clears the undo history,
     * so the loading itself can never be undone.
     */
    fun load(text: String) {
        fieldState.edit {
            replace(0, length, text)
            placeCursorBeforeCharAt(0)
        }
        fieldState.undoState.clearHistory()
    }

    /**
     * Like [load], but leaves cursor and undo history untouched when the text is
     * already identical (for example after refreshing an unchanged file).
     */
    fun replaceIfDifferent(text: String) {
        if (currentText() != text) {
            load(text)
        }
    }

    /** Whether there is an edit to undo. Read in composition, it updates the UI by itself. */
    val canUndo: Boolean get() = fieldState.undoState.canUndo

    /** Whether there is an undone edit to redo. */
    val canRedo: Boolean get() = fieldState.undoState.canRedo

    /** Reverts the latest edit, or the latest group of merged edits. Does nothing if none. */
    fun undo() {
        val history = fieldState.undoState
        if (history.canUndo) history.undo()
    }

    /** Re-applies the edit that was just undone. Does nothing if none. */
    fun redo() {
        val history = fieldState.undoState
        if (history.canRedo) history.redo()
    }

    /**
     * Inserts [text] at the cursor, replacing the selection. One call is one undo entry.
     * The selection is read inside the edit, so it is always the current one.
     */
    fun insertText(text: String) {
        fieldState.edit {
            val current = selection
            applyEdit(EditorTextOps.insertText(text, current.start, current.end, length))
        }
    }

    /**
     * Inserts [open] and [close] around the cursor or the selection, as one undo entry.
     * See [EditorTextOps.insertPair] for the exact rules.
     */
    fun insertPair(open: String, close: String) {
        fieldState.edit {
            val current = selection
            applyEdit(EditorTextOps.insertPair(open, close, current.start, current.end, length))
        }
    }

    private fun TextFieldBuffer.applyEdit(edit: TextEdit) {
        for (replacement in edit.replacements) {
            replace(replacement.start, replacement.end, replacement.text)
        }
        selection = TextRange(edit.selectionStart, edit.selectionEnd)
    }

    /**
     * Emits the new text every time the text itself changes. Selection-only changes
     * (moving the cursor, dragging handles) are filtered out without allocating a String.
     * The first value is emitted right away.
     */
    fun textChanges(): Flow<String> = flow {
        var last: String? = null
        snapshotFlow { fieldState.text }.collect { text ->
            val previous = last
            if (previous == null || text.length != previous.length || !text.contentEquals(previous)) {
                val value = text.toString()
                last = value
                emit(value)
            }
        }
    }
}
