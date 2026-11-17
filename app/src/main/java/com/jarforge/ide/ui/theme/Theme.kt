package com.jarforge.ide.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarforgeColorScheme = darkColorScheme(
    primary = JarforgeColors.Blue,
    onPrimary = Color.White,
    secondary = JarforgeColors.Purple,
    onSecondary = Color.White,
    background = JarforgeColors.Background,
    onBackground = JarforgeColors.TextPrimary,
    surface = JarforgeColors.Panel,
    onSurface = JarforgeColors.TextPrimary,
    surfaceVariant = JarforgeColors.Panel,
    onSurfaceVariant = JarforgeColors.TextSecondary,
    outline = JarforgeColors.Border
)

@Composable
fun JarforgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarforgeColorScheme,
        typography = JarforgeTypography,
        content = content
    )
}
