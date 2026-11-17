package com.jarforge.ide.feature.newproject

import androidx.compose.runtime.Composable
import com.jarforge.ide.ui.components.PlaceholderAction
import com.jarforge.ide.ui.components.PlaceholderScreen

@Composable
fun NewProjectScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "New Mod Project",
        description = "Placeholder. The project form arrives in Part 3.",
        actions = listOf(PlaceholderAction("Back", onBack))
    )
}
