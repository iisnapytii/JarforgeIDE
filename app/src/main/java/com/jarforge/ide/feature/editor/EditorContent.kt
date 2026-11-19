package com.jarforge.ide.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Part 2D shows a read-only preview here. This composable is the single place the
 * real code editor will replace in a later stage.
 */
@Composable
fun EditorContent(
    state: EditorUiState,
    onOpenExplorer: () -> Unit,
    onRetryProject: () -> Unit,
    onRetryPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val project = state.project) {
            is ProjectLoad.Loading -> LoadingState()
            is ProjectLoad.Failed -> ErrorState(
                message = project.message,
                onRetry = onRetryProject
            )
            is ProjectLoad.Ready -> PreviewArea(
                state = state,
                onOpenExplorer = onOpenExplorer,
                onRetryPreview = onRetryPreview
            )
        }
    }
}

@Composable
private fun PreviewArea(
    state: EditorUiState,
    onOpenExplorer: () -> Unit,
    onRetryPreview: () -> Unit
) {
    val path = state.selectedPath
    if (path == null) {
        EmptyState(
            title = "No file open",
            message = "Open the File explorer and select a file to preview it.",
            actionLabel = "File explorer",
            onAction = onOpenExplorer
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PreviewHeader(path = path, preview = state.preview)
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
                is FilePreview.Text -> CodeLines(
                    content = file.content,
                    modifier = Modifier.weight(1f)
                )
                is FilePreview.TooLarge -> PreviewMessage(
                    title = "File is too large to preview",
                    message = "This file is ${formatFileSize(file.sizeBytes)}. " +
                        "Files larger than ${FilePreview.MAX_BYTES / 1024} KB cannot be previewed.",
                    modifier = Modifier.weight(1f)
                )
                is FilePreview.NotText -> PreviewMessage(
                    title = "Not a text file",
                    message = "This file is binary or is not valid UTF-8 text, " +
                        "so it cannot be previewed.",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PreviewHeader(path: String, preview: PreviewState) {
    val sizeBytes: Long? = (preview as? PreviewState.Ready)?.let { ready ->
        when (val file = ready.preview) {
            is FilePreview.Text -> file.sizeBytes
            is FilePreview.TooLarge -> file.sizeBytes
            is FilePreview.NotText -> file.sizeBytes
        }
    }
    val details = if (sizeBytes != null) {
        "Read-only preview  ·  ${formatFileSize(sizeBytes)}"
    } else {
        "Read-only preview"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = path,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = details,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "The code editor arrives in a later stage.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Read-only numbered lines. Lazy, so large files stay cheap to display. */
@Composable
private fun CodeLines(
    content: String,
    modifier: Modifier = Modifier
) {
    if (content.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(JarforgeColors.EditorBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "This file is empty.",
                style = MaterialTheme.typography.bodyMedium,
                color = JarforgeColors.TextSecondary
            )
        }
        return
    }

    val lines = remember(content) { content.lines() }
    val numberWidth = maxOf(24, lines.size.toString().length * 9).dp
    val codeStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(JarforgeColors.EditorBackground),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = codeStyle,
                    color = JarforgeColors.TextSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(numberWidth)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = line.replace("\t", "    "),
                    style = codeStyle,
                    color = JarforgeColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PreviewMessage(
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
