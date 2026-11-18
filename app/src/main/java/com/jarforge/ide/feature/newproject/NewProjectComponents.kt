package com.jarforge.ide.feature.newproject

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jarforge.ide.data.model.Loader
import com.jarforge.ide.data.model.MinecraftVersion
import com.jarforge.ide.ui.components.SelectableChip
import com.jarforge.ide.ui.theme.JarforgeColors

@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
fun LoaderSelector(
    selected: Loader,
    onSelected: (Loader) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Loader.entries.forEach { loader ->
            SelectableChip(
                label = loader.displayName,
                selected = selected == loader,
                onClick = { onSelected(loader) },
                enabled = enabled
            )
        }
    }
}

@Composable
fun VersionSelector(
    versions: List<MinecraftVersion>,
    selected: String,
    onSelected: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        versions.forEach { version ->
            SelectableChip(
                label = version.id,
                selected = selected == version.id,
                onClick = { onSelected(version.id) },
                enabled = enabled
            )
        }
    }
}

/**
 * Radio-style option card. When [available] is false the option is disabled
 * and [unavailableReason] explains why.
 */
@Composable
fun TemplateOption(
    title: String,
    description: String,
    selected: Boolean,
    available: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unavailableReason: String? = null
) {
    val clickable = available && enabled
    val borderColor = if (selected && available) JarforgeColors.Blue else JarforgeColors.Border

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = JarforgeColors.Panel,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        enabled = clickable,
                        role = Role.RadioButton,
                        onClick = onClick
                    )
                    .alpha(if (available) 1f else 0.5f)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    enabled = clickable
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!available && unavailableReason != null) {
                Text(
                    text = unavailableReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = JarforgeColors.Warning,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                )
            }
        }
    }
}
