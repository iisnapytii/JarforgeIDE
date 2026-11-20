package com.jarforge.ide.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarforge.ide.R
import com.jarforge.ide.ui.AppViewModelProvider
import com.jarforge.ide.ui.components.JarforgeTopBar
import com.jarforge.ide.ui.components.TopBarIconButton
import com.jarforge.ide.ui.rememberAppContainer
import com.jarforge.ide.ui.theme.JarforgeColors
import kotlinx.coroutines.launch

/**
 * Editor of one project. Opened directly from a project card.
 *
 * Part 3.1a: top bar with Save action, File explorer drawer, basic text editing,
 * and unsaved-changes confirmation guard.
 */
@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit
) {
    val container = rememberAppContainer()
    val viewModel: EditorViewModel = viewModel(
        factory = AppViewModelProvider.editorFactory(container, projectId)
    )
    val state by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val rows = remember(state.tree) { buildRows(state.tree) }

    val projectName = (state.project as? ProjectLoad.Ready)?.project?.name

    LaunchedEffect(viewModel) {
        viewModel.navigateBackEvents.collect {
            onBack()
        }
    }

    // The first system "back" closes the explorer; when closed, guard unsaved changes.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = !drawerState.isOpen && (state.hasUnsavedChanges || state.isSaving)) {
        if (!state.isSaving) {
            viewModel.onNavigateBackRequested()
        }
    }

    state.pendingAction?.let {
        UnsavedChangesDialog(
            filePath = state.selectedPath ?: "Current file",
            isSaving = state.isSaving,
            onSave = viewModel::onSaveAndProceed,
            onDiscard = viewModel::onDiscardAndProceed,
            onCancel = viewModel::onDismissPendingAction
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Edge swipe is off while closed so it cannot clash with the system back gesture.
        // While open, swiping or tapping outside closes the drawer.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = JarforgeColors.Panel) {
                FileExplorerPanel(
                    projectName = projectName ?: "Project",
                    rows = rows,
                    selectedPath = state.selectedPath,
                    onFolderClick = viewModel::onFolderClicked,
                    onFileClick = { path ->
                        scope.launch { drawerState.close() }
                        viewModel.onFileClicked(path)
                    },
                    onRefresh = viewModel::onRefresh,
                    onClose = { scope.launch { drawerState.close() } },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                JarforgeTopBar(
                    title = projectName ?: "Editor",
                    onBack = viewModel::onNavigateBackRequested,
                    actions = {
                        if (state.isTextEditable) {
                            TopBarIconButton(
                                iconRes = R.drawable.ic_save,
                                contentDescription = "Save file",
                                // Save handles stale dirty-state mirrors by syncing the field first.
                                enabled = !state.isSaving,
                                onClick = viewModel::onSaveFile
                            )
                        }
                        TopBarIconButton(
                            iconRes = R.drawable.ic_folder,
                            contentDescription = "File explorer",
                            onClick = { scope.launch { drawerState.open() } }
                        )
                    }
                )
            }
        ) { padding ->
            EditorContent(
                state = state,
                onOpenExplorer = { scope.launch { drawerState.open() } },
                onRetryProject = viewModel::onRetryProject,
                onRetryPreview = viewModel::onRetryPreview,
                textState = viewModel.textState.fieldState,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun UnsavedChangesDialog(
    filePath: String,
    isSaving: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onCancel() },
        containerColor = JarforgeColors.Panel,
        title = {
            Text(text = "Unsaved changes")
        },
        text = {
            Text(
                text = "Save changes to \"$filePath\" before continuing?",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !isSaving
            ) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDiscard,
                enabled = !isSaving
            ) {
                Text(
                    text = "Don't save",
                    color = JarforgeColors.Danger
                )
            }
            TextButton(
                onClick = onCancel,
                enabled = !isSaving
            ) {
                Text(text = "Cancel")
            }
        }
    )
}
