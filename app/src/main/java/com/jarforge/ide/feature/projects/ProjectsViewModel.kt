package com.jarforge.ide.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarforge.ide.data.model.Loader
import com.jarforge.ide.data.model.ProjectEntry
import com.jarforge.ide.data.storage.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProjectsUiState(
    val isLoading: Boolean = true,
    val hasLoaded: Boolean = false,
    val entries: List<ProjectEntry> = emptyList(),
    val query: String = "",
    val loaderFilter: Loader? = null,
    val errorMessage: String? = null
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
}
