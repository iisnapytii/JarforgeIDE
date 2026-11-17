package com.jarforge.ide.feature.settings

import androidx.compose.runtime.Composable
import com.jarforge.ide.ui.components.PlaceholderAction
import com.jarforge.ide.ui.components.PlaceholderScreen

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Settings",
        description = "Placeholder. Settings content arrives in Part 3.",
        actions = listOf(PlaceholderAction("Back", onBack))
    )
}
