package com.jarforge.ide.feature.projects

import androidx.compose.runtime.Composable
import com.jarforge.ide.ui.components.PlaceholderAction
import com.jarforge.ide.ui.components.PlaceholderScreen

@Composable
fun ProjectsScreen(
    onNewProject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEditor: () -> Unit
) {
    PlaceholderScreen(
        title = "Projects",
        description = "Placeholder. Project cards and loader filters arrive in Part 2.",
        actions = listOf(
            PlaceholderAction("New project", onNewProject),
            PlaceholderAction("Open editor", onOpenEditor),
            PlaceholderAction("Settings", onOpenSettings)
        )
    )
}
