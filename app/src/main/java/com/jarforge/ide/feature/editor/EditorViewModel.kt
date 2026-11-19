package com.jarforge.ide.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarforge.ide.data.model.FilePreview
import com.jarforge.ide.data.model.Project
import com.jarforge.ide.data.storage.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ProjectLoad {
    data object Loading : ProjectLoad
    data class Ready(val project: Project) : ProjectLoad
    data class Failed(val message: String) : ProjectLoad
}

sealed interface PreviewState {
    data object None : PreviewState
    data object Loading : PreviewState
    data class Ready(val preview: FilePreview) : PreviewState
    data class Failed(val message: String) : PreviewState
}

data class EditorUiState(
    val project: ProjectLoad = ProjectLoad.Loading,
    val tree: FileTreeState = FileTreeState(),
    val selectedPath: String? = null,
    val preview: PreviewState = PreviewState.None
)

/**
 * State of the editor screen for one project.
 *
 * In Part 2D the content area is a read-only preview. The selected file path and the
 * project are already held here, so the code editor can later replace the preview
 * without changing the explorer or navigation.
 */
class EditorViewModel(
    private val repository: ProjectRepository,
    private val projectId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        loadProject()
        loadDirectory(ROOT_PATH)
    }

    // ---- project ----

    private fun loadProject() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getProject(projectId) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { project -> state.copy(project = ProjectLoad.Ready(project)) },
                    onFailure = { error ->
                        state.copy(
                            project = ProjectLoad.Failed(
                                error.message ?: "Could not open the project."
                            )
                        )
                    }
                )
            }
        }
    }

    fun onRetryProject() {
        _uiState.update { it.copy(project = ProjectLoad.Loading) }
        loadProject()
        loadDirectory(ROOT_PATH)
    }

    // ---- file tree ----

    fun onFolderClicked(path: String) {
        val tree = _uiState.value.tree
        if (path in tree.expanded) {
            _uiState.update { it.copy(tree = it.tree.copy(expanded = it.tree.expanded - path)) }
        } else {
            _uiState.update { it.copy(tree = it.tree.copy(expanded = it.tree.expanded + path)) }
            if (tree.children[path] == null) {
                loadDirectory(path)
            }
        }
    }

    /** Re-reads the root and every expanded folder, and the open file. */
    fun onRefresh() {
        val expanded = _uiState.value.tree.expanded
        // Drop cached content of collapsed folders; keep visible content until new data arrives.
        _uiState.update { state ->
            val kept = state.tree.children.filterKeys { it == ROOT_PATH || it in expanded }
            state.copy(tree = state.tree.copy(children = kept))
        }
        loadDirectory(ROOT_PATH)
        expanded.forEach { loadDirectory(it) }
        _uiState.value.selectedPath?.let { loadPreview(it) }
    }

    private fun loadDirectory(path: String) {
        if (path in _uiState.value.tree.loading) return

        _uiState.update {
            it.copy(
                tree = it.tree.copy(
                    loading = it.tree.loading + path,
                    errors = it.tree.errors - path
                )
            )
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.listFiles(projectId, path) }
            _uiState.update { state ->
                val tree = state.tree
                val newTree = result.fold(
                    onSuccess = { entries ->
                        tree.copy(
                            children = tree.children + (path to entries),
                            loading = tree.loading - path,
                            errors = tree.errors - path
                        )
                    },
                    onFailure = { error ->
                        tree.copy(
                            loading = tree.loading - path,
                            errors = tree.errors +
                                (path to (error.message ?: "Could not read the folder."))
                        )
                    }
                )
                state.copy(tree = newTree)
            }
        }
    }

    // ---- read-only preview ----

    fun onFileClicked(path: String) {
        _uiState.update { it.copy(selectedPath = path, preview = PreviewState.Loading) }
        loadPreview(path)
    }

    fun onRetryPreview() {
        val path = _uiState.value.selectedPath ?: return
        _uiState.update { it.copy(preview = PreviewState.Loading) }
        loadPreview(path)
    }

    private fun loadPreview(path: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.readFilePreview(projectId, path)
            }
            _uiState.update { state ->
                // Ignore a late result for a file that is no longer selected.
                if (state.selectedPath != path) {
                    state
                } else {
                    result.fold(
                        onSuccess = { preview ->
                            state.copy(preview = PreviewState.Ready(preview))
                        },
                        onFailure = { error ->
                            state.copy(
                                preview = PreviewState.Failed(
                                    error.message ?: "Could not read the file."
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}
