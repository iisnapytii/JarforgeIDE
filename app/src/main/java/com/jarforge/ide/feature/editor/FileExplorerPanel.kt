package com.jarforge.ide.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarforge.ide.R
import com.jarforge.ide.ui.theme.JarforgeColors

private val BaseIndent = 12.dp
private val IndentPerLevel = 16.dp
private val ChevronSlot = 24.dp

/** Content of the File explorer drawer: header with actions and the lazily loaded tree. */
@Composable
fun FileExplorerPanel(
    projectName: String,
    rows: List<TreeRow>,
    selectedPath: String?,
    onFolderClick: (String) -> Unit,
    onFileClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "File explorer",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = "Refresh files",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Close file explorer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = JarforgeColors.Border)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(items = rows, key = { row -> row.key }) { row ->
                when (row) {
                    is TreeRow.Node -> TreeNodeRow(
                        row = row,
                        selected = !row.entry.isDirectory &&
                            row.entry.relativePath == selectedPath,
                        onFolderClick = onFolderClick,
                        onFileClick = onFileClick
                    )
                    is TreeRow.Message -> TreeMessageRow(row = row)
                }
            }
        }
    }
}

@Composable
private fun TreeNodeRow(
    row: TreeRow.Node,
    selected: Boolean,
    onFolderClick: (String) -> Unit,
    onFileClick: (String) -> Unit
) {
    val entry = row.entry
    val background = if (selected) JarforgeColors.Blue.copy(alpha = 0.16f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable {
                if (entry.isDirectory) onFolderClick(entry.relativePath)
                else onFileClick(entry.relativePath)
            }
            .padding(
                start = BaseIndent + IndentPerLevel * row.depth,
                end = 12.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (entry.isDirectory) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = if (row.expanded) "Collapse" else "Expand",
                tint = JarforgeColors.TextSecondary,
                modifier = Modifier
                    .size(ChevronSlot)
                    .rotate(if (row.expanded) 90f else 0f)
            )
        } else {
            Spacer(modifier = Modifier.width(ChevronSlot))
        }
        Icon(
            painter = painterResource(
                if (entry.isDirectory) R.drawable.ic_folder else R.drawable.ic_file
            ),
            contentDescription = null,
            tint = if (entry.isDirectory) JarforgeColors.Blue else JarforgeColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (row.loading) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun TreeMessageRow(row: TreeRow.Message) {
    Text(
        text = row.text,
        style = MaterialTheme.typography.bodySmall,
        color = if (row.isError) JarforgeColors.Danger else JarforgeColors.TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BaseIndent + IndentPerLevel * row.depth + ChevronSlot,
                end = 12.dp,
                top = 6.dp,
                bottom = 6.dp
            )
    )
}
