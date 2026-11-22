package com.jarforge.ide.feature.editor

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarforge.ide.data.model.FilePreview
import com.jarforge.ide.ui.components.EmptyState
import com.jarforge.ide.ui.components.ErrorState
import com.jarforge.ide.ui.components.LoadingState
import com.jarforge.ide.ui.theme.JarforgeColors
import java.util.Locale

/**
 * Content area of the editor.
 *
 * Part 3.1a: editable text field for UTF-8 text files within the size limit,
 * with unsaved indicator, save status, and error feedback.
 * Part 3.2.0: the field is backed by a TextFieldState owned by the ViewModel ([EditorTextState]).
 * Part 3.2.1: the editing surface (line numbers, current-line highlight, text field)
 * lives in [CodeEditorView].
 * Part 3.2.2: Undo/Redo and the symbol toolbar ([EditorToolbar]), shown above the keyboard
 * while the text field has focus.
 * Part 3.2.4: Java files (.java) get syntax highlighting; other files stay plain.
 */
@Composable
fun EditorContent(
    state: EditorUiState,
    onOpenExplorer: () -> Unit,
    onRetryProject: () -> Unit,
    onRetryPreview: () -> Unit,
    editorText: EditorTextState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val project = state.project) {
            is ProjectLoad.Loading -> LoadingState()
            is ProjectLoad.Failed -> ErrorState(
                message = project.message,
                onRetry = onRetryProject
            )
            is ProjectLoad.Ready -> EditorArea(
                state = state,
                onOpenExplorer = onOpenExplorer,
                onRetryPreview = onRetryPreview,
                editorText = editorText
            )
        }
    }
}

@Composable
private fun EditorArea(
    state: EditorUiState,
    onOpenExplorer: () -> Unit,
    onRetryPreview: () -> Unit,
    editorText: EditorTextState
) {
    val path = state.selectedPath
    if (path == null) {
        EmptyState(
            title = "No file open",
            message = "Open the File explorer and select a file to edit it.",
            actionLabel = "File explorer",
            onAction = onOpenExplorer
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        EditorHeader(path = path, state = state)
        HorizontalDivider(color = JarforgeColors.Border)

        when (val preview = state.preview) {
            is PreviewState.None -> LoadingState(modifier = Modifier.weight(1f))
            is PreviewState.Loading -> LoadingState(modifier = Modifier.weight(1f))
            is PreviewState.Failed -> ErrorState(
                message = preview.message,
                onRetry = onRetryPreview,
                modifier = Modifier.weight(1f)
            )
            is PreviewState.Ready -> when (val file = preview.preview) {
                is FilePreview.Text -> EditorWorkspace(
                    editorText = editorText,
                    highlightJava = path.endsWith(".java", ignoreCase = true),
                    modifier = Modifier.weight(1f)
                )
                is FilePreview.TooLarge -> EditorMessage(
                    title = "File is too large to open",
                    message = "This file is ${formatFileSize(file.sizeBytes)}. " +
                        "Files larger than ${FilePreview.MAX_BYTES / 1024} KB cannot be opened in the editor.",
                    modifier = Modifier.weight(1f)
                )
                is FilePreview.NotText -> EditorMessage(
                    title = "Not a text file",
                    message = "This file is binary or is not valid UTF-8 text, " +
                        "so it cannot be edited as text.",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * The editing surface together with the symbol toolbar.
 *
 * imePadding() is applied once, here, to both, so the toolbar always sits directly above the
 * keyboard. The toolbar is shown only while the text field has focus.
 */
@Composable
private fun EditorWorkspace(
    editorText: EditorTextState,
    highlightJava: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val view = LocalView.current

    // Reading the text subscribes this composable to every edit, so the Undo/Redo state is
    // re-read after each change even if the history flags were not observable on their own.
    @Suppress("UNUSED_VARIABLE")
    val observedText = editorText.fieldState.text

    Column(modifier = modifier.imePadding()) {
        CodeEditorView(
            textState = editorText.fieldState,
            interactionSource = interactionSource,
            modifier = Modifier.weight(1f),
            highlightJava = highlightJava
        )
        if (focused) {
            EditorToolbar(
                canUndo = editorText.canUndo,
                canRedo = editorText.canRedo,
                onUndo = editorText::undo,
                onRedo = editorText::redo,
                onKey = { key -> handleToolbarKey(key, editorText, view) }
            )
        }
    }
}

private fun handleToolbarKey(key: EditorKey, editorText: EditorTextState, view: View) {
    when (key) {
        is EditorKey.Symbol -> editorText.insertText(key.text)
        is EditorKey.SymbolPair -> editorText.insertPair(key.open, key.close)
        EditorKey.Tab -> editorText.insertText(EditorTextOps.INDENT)
        EditorKey.CursorLeft -> dispatchCursorKey(view, KeyEvent.KEYCODE_DPAD_LEFT)
        EditorKey.CursorRight -> dispatchCursorKey(view, KeyEvent.KEYCODE_DPAD_RIGHT)
    }
}

/**
 * Moves the cursor by sending the same arrow key a hardware keyboard would send to the
 * focused text field. The text field handles it itself: the text is not touched, no undo
 * entry is created, and with a selection the cursor collapses to its start or end.
 */
private fun dispatchCursorKey(view: View, keyCode: Int) {
    val now = SystemClock.uptimeMillis()
    view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
    view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
}

@Composable
private fun EditorHeader(path: String, state: EditorUiState) {
    val sizeBytes: Long? = (state.preview as? PreviewState.Ready)?.let { ready ->
        when (val file = ready.preview) {
            is FilePreview.Text -> file.sizeBytes
            is FilePreview.TooLarge -> file.sizeBytes
            is FilePreview.NotText -> file.sizeBytes
        }
    }

    val statusLabel = when {
        state.isSaving -> "Saving..."
        state.hasUnsavedChanges -> "Unsaved changes"
        state.isTextEditable -> "Saved"
        else -> "Read-only"
    }

    val details = if (sizeBytes != null) {
        "$statusLabel  ·  ${formatFileSize(sizeBytes)}"
    } else {
        statusLabel
    }

    val displayPath = if (state.hasUnsavedChanges) "$path *" else path

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = displayPath,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = if (state.hasUnsavedChanges) JarforgeColors.Blue else JarforgeColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = details,
            style = MaterialTheme.typography.labelSmall,
            color = if (state.hasUnsavedChanges) {
                JarforgeColors.Blue
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        state.saveError?.let { error ->
            Text(
                text = "Save failed: $error",
                style = MaterialTheme.typography.labelSmall,
                color = JarforgeColors.Danger,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        state.metadataWarning?.let { warning ->
            Text(
                text = warning,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun EditorMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
