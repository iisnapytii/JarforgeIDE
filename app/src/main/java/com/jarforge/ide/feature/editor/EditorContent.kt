package com.jarforge.ide.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 */
@Composable
fun EditorContent(
    state: EditorUiState,
    onOpenExplorer: () -> Unit,
    onRetryProject: () -> Unit,
    onRetryPreview: () -> Unit,
    onContentChanged: (String) -> Unit,
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
                onContentChanged = onContentChanged
            )
        }
    }
}

@Composable
private fun EditorArea(
    state: EditorUiState,
    onOpenExplorer: () -> Unit,
    onRetryPreview: () -> Unit,
    onContentChanged: (String) -> Unit
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
                is FilePreview.Text -> EditableCodeArea(
                    content = state.editorContent,
                    onContentChanged = onContentChanged,
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
private fun EditableCodeArea(
    content: String,
    onContentChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val codeStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = JarforgeColors.TextPrimary
    )

    BasicTextField(
        value = content,
        onValueChange = onContentChanged,
        textStyle = codeStyle,
        cursorBrush = SolidColor(JarforgeColors.Blue),
        modifier = modifier
            .fillMaxSize()
            .background(JarforgeColors.EditorBackground)
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (content.isEmpty()) {
                    Text(
                        text = "Empty file — tap here to start typing...",
                        style = codeStyle,
                        color = JarforgeColors.TextSecondary
                    )
                }
                innerTextField()
            }
        }
    )
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
