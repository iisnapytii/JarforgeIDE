package com.jarforge.ide.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarforge.ide.R
import com.jarforge.ide.data.model.ProjectEntry
import com.jarforge.ide.ui.AppViewModelProvider
import com.jarforge.ide.ui.components.EmptyState
import com.jarforge.ide.ui.components.ErrorState
import com.jarforge.ide.ui.components.JarforgeTopBar
import com.jarforge.ide.ui.components.LoadingState
import com.jarforge.ide.ui.components.TopBarIconButton
import com.jarforge.ide.ui.rememberAppContainer
import com.jarforge.ide.ui.theme.JarforgeColors

@Composable
fun ProjectsScreen(
    onNewProject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProject: (projectId: String) -> Unit
) {
    val container = rememberAppContainer()
    val viewModel: ProjectsViewModel = viewModel(
        factory = AppViewModelProvider.projectsFactory(container)
    )
    val state by viewModel.uiState.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    // Runs each time this screen enters composition, including when
    // returning from the New Project screen or the editor, so the list stays current.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val visible = state.visibleEntries
    val errorMessage = state.errorMessage

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            JarforgeTopBar(
                title = "Jarforge IDE",
                actions = {
                    TopBarIconButton(
                        iconRes = R.drawable.ic_add,
                        contentDescription = "New project",
                        onClick = onNewProject
                    )
                    Box {
                        TopBarIconButton(
                            iconRes = R.drawable.ic_more_vert,
                            contentDescription = "More",
                            onClick = { menuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSettings()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.hasAnyProjects) {
                ProjectSearchField(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                )
                LoaderFilterRow(
                    selected = state.loaderFilter,
                    onSelected = viewModel::onLoaderFilterChange,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            when {
                state.isLoading -> LoadingState(modifier = Modifier.weight(1f))

                errorMessage != null && !state.hasAnyProjects -> ErrorState(
                    message = errorMessage,
                    onRetry = viewModel::refresh,
                    modifier = Modifier.weight(1f)
                )

                !state.hasAnyProjects -> EmptyState(
                    title = "No projects yet",
                    message = "Create a mod project to get started.",
                    modifier = Modifier.weight(1f),
                    actionLabel = "New project",
                    onAction = onNewProject
                )

                visible.isEmpty() -> EmptyState(
                    title = "No matching projects",
                    message = "Try a different search or filter.",
                    modifier = Modifier.weight(1f)
                )

                else -> {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = JarforgeColors.Danger,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = visible, key = { entry -> entryKey(entry) }) { entry ->
                            when (entry) {
                                is ProjectEntry.Valid -> ProjectCard(
                                    project = entry.project,
                                    onClick = { onOpenProject(entry.project.id) },
                                    onRename = { viewModel.onRenameRequested(entry.project) },
                                    onDelete = { viewModel.onDeleteRequested(entry) }
                                )
                                is ProjectEntry.Damaged -> DamagedProjectCard(
                                    directoryName = entry.directoryName,
                                    reason = entry.reason,
                                    onDelete = { viewModel.onDeleteRequested(entry) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (val dialog = state.dialog) {
        is ProjectDialog.Rename -> RenameProjectDialog(
            state = dialog,
            onInputChange = viewModel::onRenameInputChange,
            onConfirm = viewModel::onRenameConfirm,
            onDismiss = viewModel::onDialogDismiss
        )
        is ProjectDialog.Delete -> DeleteProjectDialog(
            state = dialog,
            onConfirm = viewModel::onDeleteConfirm,
            onDismiss = viewModel::onDialogDismiss
        )
        null -> Unit
    }
}

private fun entryKey(entry: ProjectEntry): String = when (entry) {
    is ProjectEntry.Valid -> "p:${entry.project.id}"
    is ProjectEntry.Damaged -> "d:${entry.directoryName}"
}
