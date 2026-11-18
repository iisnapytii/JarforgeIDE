package com.jarforge.ide.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarforge.ide.ui.components.JarforgeTopBar

/** Read-only information screen. Editor preferences arrive together with the code editor. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appInfo = remember(context) { readAppInfo(context) }
    val targets = remember { loadLoaderTargets() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            JarforgeTopBar(
                title = "Settings",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AboutSection(info = appInfo)
            BuildEnvironmentSection()
            SupportedTargetsSection(targets = targets)
            StorageSection()
            SettingsNote(text = "Editor preferences will appear together with the code editor.")
        }
    }
}
