package com.jarforge.ide.feature.projects

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jarforge.ide.R
import com.jarforge.ide.data.model.Loader
import com.jarforge.ide.ui.components.JarforgeTextField
import com.jarforge.ide.ui.components.SelectableChip

@Composable
fun ProjectSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    JarforgeTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = "Search projects",
        leadingIconRes = R.drawable.ic_search,
        onClear = { onQueryChange("") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
    )
}

@Composable
fun LoaderFilterRow(
    selected: Loader?,
    onSelected: (Loader?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SelectableChip(
            label = "All",
            selected = selected == null,
            onClick = { onSelected(null) }
        )
        Loader.entries.forEach { loader ->
            SelectableChip(
                label = loader.displayName,
                selected = selected == loader,
                onClick = { onSelected(loader) }
            )
        }
    }
}
