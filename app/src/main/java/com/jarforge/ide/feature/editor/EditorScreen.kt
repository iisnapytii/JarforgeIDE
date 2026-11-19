package com.jarforge.ide.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
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
 * Part 2D: top bar, File explorer drawer with the real project tree, and a read-only
 * file preview. Run, Build, Search and the code editor itself come in a later stage.
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

    // The first system "back" closes the explorer; the next one leaves the editor.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
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
                        viewModel.onFileClicked(path)
                        scope.launch { drawerState.close() }
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
                    onBack = onBack,
                    actions = {
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
                modifier = Modifier.padding(padding)
            )
        }
    }
}
