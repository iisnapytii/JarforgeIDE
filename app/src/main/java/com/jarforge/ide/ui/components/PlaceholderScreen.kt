package com.jarforge.ide.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PlaceholderAction(
    val label: String,
    val onClick: () -> Unit
)

/**
 * Temporary screen used in Part 1 to verify navigation.
 * Each feature screen will replace its use of this in later parts.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    actions: List<PlaceholderAction>
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            actions.forEach { action ->
                Button(onClick = action.onClick) { Text(action.label) }
            }
        }
    }
}
