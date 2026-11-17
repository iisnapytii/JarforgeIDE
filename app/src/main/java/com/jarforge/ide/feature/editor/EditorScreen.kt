package com.jarforge.ide.feature.editor

import androidx.compose.runtime.Composable
import com.jarforge.ide.ui.components.PlaceholderAction
import com.jarforge.ide.ui.components.PlaceholderScreen

@Composable
fun EditorScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Code Editor",
        description = "Placeholder. The editor layout arrives in Part 4. " +
            "Build environment: not configured.",
        actions = listOf(PlaceholderAction("Back", onBack))
    )
}
