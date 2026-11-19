package com.jarforge.ide.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarforge.ide.data.model.Loader
import com.jarforge.ide.data.model.Project
import com.jarforge.ide.data.model.ProjectEntry
import com.jarforge.ide.data.storage.ProjectRepository
import com.jarforge.ide.domain.ProjectValidators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a delete confirmation refers to. Works for valid and damaged projects. */
data class DeleteTarget(
    val id: String,
    val displayName: String,
    val isDamaged: Boolean
)

/** At most one dialog is open at a time. */
sealed interface ProjectDialog {
    data class Rename(
        val project: Project,
        val input: String,
        val error: String? = null,
        val isWorking: Boolean = false
    ) : ProjectDialog

    data class Delete(
        val target: DeleteTarget,
        val error: String? = null,
        val isWorking: Boolean = false
    ) : ProjectDialog
}

data class ProjectsUiState(
    val isLoading: Boolean = true,
    val hasLoaded: Boolean = false,
    val entries: List<ProjectEntry> = emptyList(),
    val query: String = "",
    val loaderFilter: Loader? = null,
    val errorMessage: String? = null,
    val dialog: ProjectDialog? = null
) {
    val hasAnyProjects: Boolean get() = entries.isNotEmpty()

    /** Entries after applying search and loader filter. Damaged ones hide under a loader filter. */
    val visibleEntries: List<ProjectEntry>
        get() {
            val q = query.trim()
            return entries.filter { entry ->
                when (entry) {
                    is ProjectEntry.Valid -> {
                        val p = entry.project
                        (loaderFilter == null || p.loader == loaderFilter) &&
                            (q.isEmpty() ||
                                p.name.contains(q, ignoreCase = true) ||
                                p.modId.contains(q, ignoreCase = true))
                    }
                    is ProjectEntry.Damaged ->
                        loaderFilter == null &&
                            (q.isEmpty() || entry.directoryName.contains(q, ignoreCase = true))
                }
            }
        }
}

class ProjectsViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            // Show the spinner only before the first successful load, to avoid flicker.
            _uiState.update { it.copy(isLoading = !it.hasLoaded, errorMessage = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.listProjects() }
            }
            result
                .onSuccess { entries ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasLoaded = true,
                            entries = entries,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Could not load projects."
                        )
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onLoaderFilterChange(loader: Loader?) {
        _uiState.update { it.copy(loaderFilter = loader) }
    }

    // ---- dialogs ----

    /** Closes the open dialog, unless it is busy with a repository operation. */
    fun onDialogDismiss() {
        _uiState.update { state ->
            val dialog = state.dialog
            val busy = (dialog is ProjectDialog.Rename && dialog.isWorking) ||
                (dialog is ProjectDialog.Delete && dialog.isWorking)
            if (busy) state else state.copy(dialog = null)
        }
    }

    // ---- rename (display name only; the repository rewrites only project metadata) ----

    fun onRenameRequested(project: Project) {
        _uiState.update {
            it.copy(dialog = ProjectDialog.Rename(project = project, input = project.name))
        }
    }

    fun onRenameInputChange(input: String) {
        _uiState.update { state ->
            val dialog = state.dialog
            if (dialog is ProjectDialog.Rename && !dialog.isWorking) {
                state.copy(dialog = dialog.copy(input = input, error = null))
            } else {
                state
            }
        }
    }

    fun onRenameConfirm() {
        val dialog = _uiState.value.dialog
        if (dialog !is ProjectDialog.Rename || dialog.isWorking) return

        val newName = dialog.input.trim()
        val validationError = ProjectValidators.validateName(newName)
        if (validationError != null) {
            setRenameError(validationError)
            return
        }
        if (newName == dialog.project.name) {
            // Nothing to change.
            _uiState.update { it.copy(dialog = null) }
            return
        }

        _uiState.update { state ->
            val current = state.dialog
            if (current is ProjectDialog.Rename) {
                state.copy(dialog = current.copy(isWorking = true, error = null))
            } else {
                state
            }
        }

        val projectId = dialog.project.id
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.renameProject(projectId, newName)
            }
            result
                .onSuccess {
                    _uiState.update { it.copy(dialog = null) }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        val current = state.dialog
                        if (current is ProjectDialog.Rename) {
                            state.copy(
                                dialog = current.copy(
                                    isWorking = false,
                                    error = error.message ?: "Could not rename the project."
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
        }
    }

    private fun setRenameError(message: String) {
        _uiState.update { state ->
            val current = state.dialog
            if (current is ProjectDialog.Rename) {
                state.copy(dialog = current.copy(error = message))
            } else {
                state
            }
        }
    }

    // ---- delete (only after confirmation; affects exactly one project folder) ----

    fun onDeleteRequested(entry: ProjectEntry) {
        val target = when (entry) {
            is ProjectEntry.Valid -> DeleteTarget(
                id = entry.project.id,
                displayName = entry.project.name,
                isDamaged = false
            )
            is ProjectEntry.Damaged -> DeleteTarget(
                id = entry.directoryName,
                displayName = entry.directoryName,
                isDamaged = true
            )
        }
        _uiState.update { it.copy(dialog = ProjectDialog.Delete(target = target)) }
    }

    fun onDeleteConfirm() {
        val dialog = _uiState.value.dialog
        if (dialog !is ProjectDialog.Delete || dialog.isWorking) return

        _uiState.update { state ->
            val current = state.dialog
            if (current is ProjectDialog.Delete) {
                state.copy(dialog = current.copy(isWorking = true, error = null))
            } else {
                state
            }
        }

        val targetId = dialog.target.id
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (dialog.target.isDamaged) {
                    repository.deleteDamagedProject(targetId)
                } else {
                    repository.deleteProject(targetId)
                }
            }
            result
                .onSuccess {
                    _uiState.update { it.copy(dialog = null) }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        val current = state.dialog
                        if (current is ProjectDialog.Delete) {
                            state.copy(
                                dialog = current.copy(
                                    isWorking = false,
                                    error = error.message ?: "Could not delete the project."
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
        }
    }
}
