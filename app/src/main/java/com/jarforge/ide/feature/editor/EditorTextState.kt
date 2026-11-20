package com.jarforge.ide.feature.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
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
 * All functions that change text must be called on the main thread.
 */
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
