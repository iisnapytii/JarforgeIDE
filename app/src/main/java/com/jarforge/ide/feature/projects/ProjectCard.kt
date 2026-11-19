package com.jarforge.ide.feature.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarforge.ide.R
import com.jarforge.ide.data.model.Project
import com.jarforge.ide.data.storage.templates.LoaderTemplates
import com.jarforge.ide.ui.components.LoaderBadge
import com.jarforge.ide.ui.components.loaderColor
import com.jarforge.ide.ui.theme.JarforgeColors
import java.text.DateFormat
import java.util.Date

/**
 * Project card. Tapping the card opens the project in the editor;
 * the overflow menu offers Rename and Delete.
 */
@Composable
fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = loaderColor(project.loader)
    val modified = remember(project.modifiedAtMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(project.modifiedAtMillis))
    }
    val buildVerified = remember(project.loader) {
        LoaderTemplates.forLoader(project.loader).buildVerified
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = JarforgeColors.Panel,
        border = BorderStroke(1.dp, JarforgeColors.Border)
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = project.modId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                LoaderBadge(loader = project.loader)
                Spacer(modifier = Modifier.width(4.dp))
                CardMenu(onRename = onRename, onDelete = onDelete)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Minecraft ${project.minecraftVersion}  ·  v${project.modVersion}  ·  ${project.template.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Modified $modified",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!buildVerified) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_warning),
                        contentDescription = null,
                        tint = JarforgeColors.Warning,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Source skeleton, not build-verified",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarforgeColors.Warning
                    )
                }
            }
        }
    }
}

/** A damaged project cannot be opened; it can only be deleted. */
@Composable
fun DamagedProjectCard(
    directoryName: String,
    reason: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = JarforgeColors.Panel,
        border = BorderStroke(1.dp, JarforgeColors.Warning.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = JarforgeColors.Warning,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Damaged project",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = directoryName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CardMenu(onDelete = onDelete)
        }
    }
}

/** Overflow menu of a card. Rename is shown only when [onRename] is provided. */
@Composable
private fun CardMenu(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onRename: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = "Project actions",
                tint = JarforgeColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        expanded = false
                        onRename()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = JarforgeColors.Danger) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}
