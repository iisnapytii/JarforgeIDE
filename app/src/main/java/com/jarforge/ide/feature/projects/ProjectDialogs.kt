package com.jarforge.ide.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jarforge.ide.ui.components.JarforgeTextField
import com.jarforge.ide.ui.theme.JarforgeColors

@Composable
fun RenameProjectDialog(
    state: ProjectDialog.Rename,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.isWorking) onDismiss() },
        containerColor = JarforgeColors.Panel,
        title = { Text("Rename project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                JarforgeTextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Project name",
                    enabled = !state.isWorking,
                    isError = state.error != null,
                    supportingText = state.error,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() })
                )
                Text(
                    text = "Only the display name changes. " +
                        "Mod ID, package and files stay the same.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.isWorking) {
                Text(if (state.isWorking) "Renaming..." else "Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isWorking) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteProjectDialog(
    state: ProjectDialog.Delete,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val name = state.target.displayName
    val message = if (state.target.isDamaged) {
        "The damaged project folder \"$name\" will be permanently deleted. " +
            "This cannot be undone."
    } else {
        "\"$name\" will be permanently deleted together with all of its files. " +
            "This cannot be undone."
    }

    AlertDialog(
        onDismissRequest = { if (!state.isWorking) onDismiss() },
        containerColor = JarforgeColors.Panel,
        title = { Text("Delete project?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
                val error = state.error
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = JarforgeColors.Danger
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.isWorking,
                colors = ButtonDefaults.textButtonColors(contentColor = JarforgeColors.Danger)
            ) {
                Text(if (state.isWorking) "Deleting..." else "Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isWorking) {
                Text("Cancel")
            }
        }
    )
}
