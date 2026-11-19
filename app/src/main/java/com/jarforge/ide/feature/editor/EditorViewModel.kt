package com.jarforge.ide.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarforge.ide.data.model.FilePreview
import com.jarforge.ide.data.model.Project
import com.jarforge.ide.data.storage.FileWriteOutcome
import com.jarforge.ide.data.storage.ProjectRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

sealed interface PendingEditorAction {
    data class OpenFile(val path: String) : PendingEditorAction
    data object Refresh : PendingEditorAction
    data object NavigateBack : PendingEditorAction
}

data class EditorUiState(
    val project: ProjectLoad = ProjectLoad.Loading,
    val tree: FileTreeState = FileTreeState(),
    val selectedPath: String? = null,
    val preview: PreviewState = PreviewState.None,
    val savedContent: String = "",
    val editorContent: String = "",
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val metadataWarning: String? = null,
    val pendingAction: PendingEditorAction? = null
) {
    val isTextEditable: Boolean
        get() = (preview as? PreviewState.Ready)?.preview is FilePreview.Text

    val hasUnsavedChanges: Boolean
        get() = isTextEditable && editorContent != savedContent
}

/**
 * State of the editor screen for one project.
 */
class EditorViewModel(
    private val repository: ProjectRepository,
    private val projectId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _navigateBackEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBackEvents: SharedFlow<Unit> = _navigateBackEvents.asSharedFlow()

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
        val current = _uiState.value
        if (current.isSaving) return
        if (current.hasUnsavedChanges) {
            _uiState.update { it.copy(pendingAction = PendingEditorAction.Refresh) }
            return
        }
        performRefresh()
    }

    private fun performRefresh() {
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

    // ---- file loading & editing ----

    fun onFileClicked(path: String) {
        val current = _uiState.value
        if (current.isSaving) return
        if (current.selectedPath == path && current.preview !is PreviewState.Failed) {
            return
        }
        if (current.hasUnsavedChanges) {
            _uiState.update { it.copy(pendingAction = PendingEditorAction.OpenFile(path)) }
            return
        }
        openFileInternal(path)
    }

    private fun openFileInternal(path: String) {
        _uiState.update {
            it.copy(
                selectedPath = path,
                preview = PreviewState.Loading,
                savedContent = "",
                editorContent = "",
                saveError = null,
                metadataWarning = null,
                pendingAction = null
            )
        }
        loadPreview(path)
    }

    fun onRetryPreview() {
        val current = _uiState.value
        if (current.isSaving) return
        val path = current.selectedPath ?: return
        _uiState.update {
            it.copy(
                preview = PreviewState.Loading,
                savedContent = "",
                editorContent = "",
                saveError = null,
                metadataWarning = null
            )
        }
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
                            val textContent = (preview as? FilePreview.Text)?.content ?: ""
                            state.copy(
                                preview = PreviewState.Ready(preview),
                                savedContent = textContent,
                                editorContent = textContent,
                                saveError = null,
                                metadataWarning = null
                            )
                        },
                        onFailure = { error ->
                            state.copy(
                                preview = PreviewState.Failed(
                                    error.message ?: "Could not read the file."
                                ),
                                savedContent = "",
                                editorContent = "",
                                saveError = null,
                                metadataWarning = null
                            )
                        }
                    )
                }
            }
        }
    }

    fun onContentChanged(newContent: String) {
        _uiState.update { state ->
            if (!state.isTextEditable) {
                state
            } else {
                state.copy(
                    editorContent = newContent,
                    saveError = null
                )
            }
        }
    }

    fun onSaveFile() {
        val current = _uiState.value
        if (current.isSaving || !current.hasUnsavedChanges) return
        saveCurrentFile(pendingActionAfterSave = null)
    }

    // ---- unsaved changes guard & pending actions ----

    fun onNavigateBackRequested() {
        val current = _uiState.value
        if (current.isSaving) return
        if (current.hasUnsavedChanges) {
            _uiState.update { it.copy(pendingAction = PendingEditorAction.NavigateBack) }
            return
        }
        _navigateBackEvents.tryEmit(Unit)
    }

    fun onDismissPendingAction() {
        val current = _uiState.value
        if (current.isSaving) return
        _uiState.update { it.copy(pendingAction = null) }
    }

    fun onDiscardAndProceed() {
        val current = _uiState.value
        if (current.isSaving) return
        val action = current.pendingAction ?: return
        _uiState.update {
            it.copy(
                editorContent = it.savedContent,
                saveError = null,
                pendingAction = null
            )
        }
        executePendingAction(action)
    }

    fun onSaveAndProceed() {
        val current = _uiState.value
        if (current.isSaving) return
        val action = current.pendingAction ?: return
        if (!current.hasUnsavedChanges) {
            _uiState.update { it.copy(pendingAction = null) }
            executePendingAction(action)
            return
        }
        _uiState.update { it.copy(pendingAction = null) }
        saveCurrentFile(pendingActionAfterSave = action)
    }

    private fun saveCurrentFile(pendingActionAfterSave: PendingEditorAction?) {
        val snapshot = _uiState.value
        val path = snapshot.selectedPath ?: return
        if (snapshot.isSaving || !snapshot.isTextEditable) return
        val contentToSave = snapshot.editorContent

        _uiState.update {
            it.copy(
                isSaving = true,
                saveError = null,
                metadataWarning = null
            )
        }

        viewModelScope.launch {
            try {
                // Allow cancellation before starting disk I/O.
                ensureActive()

                val outcomeResult = withContext(Dispatchers.IO) {
                    ensureActive()
                    // Once disk write begins, guarantee completion and state commit
                    // even if the parent coroutine is cancelled mid-flight.
                    withContext(NonCancellable) {
                        val result = repository.writeFileContent(projectId, path, contentToSave)
                        result.onSuccess { outcome ->
                            commitSuccessfulSave(path, contentToSave, outcome)
                        }
                        result
                    }
                }

                outcomeResult.fold(
                    onSuccess = {
                        if (pendingActionAfterSave != null) {
                            val latest = _uiState.value
                            // Only proceed if the file is still selected and user did not type new edits during save.
                            if (latest.selectedPath == path && !latest.hasUnsavedChanges) {
                                executePendingAction(pendingActionAfterSave)
                            }
                        }
                    },
                    onFailure = { error ->
                        _uiState.update { state ->
                            if (state.selectedPath != path) {
                                state.copy(isSaving = false)
                            } else {
                                state.copy(
                                    isSaving = false,
                                    saveError = error.message ?: "Could not save the file."
                                )
                            }
                        }
                    }
                )
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isSaving = false) }
                throw e
            }
        }
    }

    private fun commitSuccessfulSave(
        path: String,
        contentToSave: String,
        outcome: FileWriteOutcome
    ) {
        _uiState.update { state ->
            val updatedProjectLoad = if (outcome.updatedProject != null) {
                ProjectLoad.Ready(outcome.updatedProject)
            } else {
                state.project
            }
            val updatedTree = updateTreeFileSize(state.tree, path, outcome.sizeBytes)

            if (state.selectedPath != path) {
                state.copy(
                    project = updatedProjectLoad,
                    tree = updatedTree,
                    isSaving = false
                )
            } else {
                val updatedPreview = PreviewState.Ready(
                    FilePreview.Text(
                        content = contentToSave,
                        sizeBytes = outcome.sizeBytes
                    )
                )
                state.copy(
                    project = updatedProjectLoad,
                    tree = updatedTree,
                    preview = updatedPreview,
                    savedContent = contentToSave,
                    isSaving = false,
                    saveError = null,
                    metadataWarning = outcome.metadataWarning
                )
            }
        }
    }

    private fun updateTreeFileSize(
        tree: FileTreeState,
        filePath: String,
        newSizeBytes: Long
    ): FileTreeState {
        val parentPath = filePath.substringBeforeLast('/', missingDelimiterValue = ROOT_PATH)
        val entries = tree.children[parentPath] ?: return tree
        var changed = false
        val updatedEntries = entries.map { entry ->
            if (!entry.isDirectory && entry.relativePath == filePath && entry.sizeBytes != newSizeBytes) {
                changed = true
                entry.copy(sizeBytes = newSizeBytes)
            } else {
                entry
            }
        }
        return if (changed) {
            tree.copy(children = tree.children + (parentPath to updatedEntries))
        } else {
            tree
        }
    }

    private fun executePendingAction(action: PendingEditorAction) {
        when (action) {
            is PendingEditorAction.OpenFile -> openFileInternal(action.path)
            is PendingEditorAction.Refresh -> performRefresh()
            is PendingEditorAction.NavigateBack -> _navigateBackEvents.tryEmit(Unit)
        }
    }
}
