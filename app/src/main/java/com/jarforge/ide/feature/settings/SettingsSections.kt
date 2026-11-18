package com.jarforge.ide.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarforge.ide.ui.components.LoaderBadge
import com.jarforge.ide.ui.components.WarningNotice
import com.jarforge.ide.ui.theme.JarforgeColors

@Composable
fun AboutSection(info: AppInfo) {
    SettingsSection(title = "About") {
        SettingsCard {
            InfoRow(label = "Application", value = info.name)
            SettingsDivider()
            InfoRow(label = "Version", value = info.versionName)
            SettingsDivider()
            InfoRow(label = "Build number", value = info.buildNumber)
            SettingsDivider()
            InfoRow(label = "Package", value = info.packageName)
        }
    }
}

@Composable
fun BuildEnvironmentSection() {
    SettingsSection(title = "Build environment") {
        if (!BuildEnvironmentInfo.isConfigured) {
            WarningNotice(text = BuildEnvironmentInfo.SUMMARY)
        }
        SettingsCard {
            BuildEnvironmentInfo.items.forEachIndexed { index, item ->
                if (index > 0) SettingsDivider()
                InfoRow(
                    label = item.name,
                    value = item.statusText,
                    valueColor = if (item.ready) Color.Unspecified else JarforgeColors.Warning
                )
            }
        }
    }
}

@Composable
fun SupportedTargetsSection(targets: List<LoaderTargetInfo>) {
    SettingsSection(title = "Supported Minecraft targets") {
        SettingsCard {
            targets.forEachIndexed { index, target ->
                if (index > 0) SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                ) {
                    LoaderBadge(loader = target.loader)
                }
                target.javaGroups.forEach { group ->
                    InfoRow(
                        label = "Minecraft (Java ${group.javaVersion})",
                        value = group.minecraftVersions.joinToString(", ")
                    )
                }
                InfoRow(
                    label = "Mixin-ready template",
                    value = if (target.mixinSupported) "Available" else "Not available",
                    valueColor = if (target.mixinSupported) {
                        Color.Unspecified
                    } else {
                        JarforgeColors.TextSecondary
                    }
                )
                InfoRow(
                    label = "Build verified",
                    value = if (target.buildVerified) "Yes" else "No",
                    valueColor = if (target.buildVerified) {
                        Color.Unspecified
                    } else {
                        JarforgeColors.Warning
                    }
                )
            }
        }
        SettingsNote(text = "Generation targets only. Not verified by a real build.")
    }
}

@Composable
fun StorageSection() {
    SettingsSection(title = "Storage") {
        SettingsCard {
            Text(
                text = "Projects are stored in the app's private storage. " +
                    "Other apps cannot access them, and they are removed when you " +
                    "uninstall Jarforge IDE or clear its data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
