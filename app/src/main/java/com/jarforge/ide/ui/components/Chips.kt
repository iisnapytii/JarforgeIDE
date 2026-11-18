package com.jarforge.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarforge.ide.data.model.Loader
import com.jarforge.ide.ui.theme.JarforgeColors

fun loaderColor(loader: Loader): Color = when (loader) {
    Loader.FABRIC -> JarforgeColors.LoaderFabric
    Loader.FORGE -> JarforgeColors.LoaderForge
    Loader.NEOFORGE -> JarforgeColors.LoaderNeoForge
}

@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = JarforgeColors.Panel,
            labelColor = JarforgeColors.TextSecondary,
            selectedContainerColor = JarforgeColors.Blue.copy(alpha = 0.18f),
            selectedLabelColor = JarforgeColors.Blue
        )
    )
}

@Composable
fun LoaderBadge(loader: Loader, modifier: Modifier = Modifier) {
    val color = loaderColor(loader)
    Text(
        text = loader.displayName,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
