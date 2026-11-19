package com.jarforge.ide.feature.newproject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarforge.ide.data.model.Loader
import com.jarforge.ide.data.model.MinecraftVersion
import com.jarforge.ide.data.model.MinecraftVersions
import com.jarforge.ide.data.model.NewProjectRequest
import com.jarforge.ide.data.model.ProjectTemplate
import com.jarforge.ide.data.storage.ProjectRepository
import com.jarforge.ide.data.storage.templates.LoaderTemplates
import com.jarforge.ide.domain.ProjectNaming
import com.jarforge.ide.domain.ProjectValidators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val DEFAULT_MOD_VERSION = "1.0.0"

enum class FormField { NAME, MOD_ID, PACKAGE, MOD_VERSION }

data class NewProjectUiState(
    val loader: Loader = Loader.FABRIC,
    val minecraftVersion: String = MinecraftVersions.forLoader(Loader.FABRIC).first().id,
    val template: ProjectTemplate = ProjectTemplate.BASIC,
    val name: String = "",
    val modId: String = "",
    val packageName: String = "",
    val modVersion: String = DEFAULT_MOD_VERSION,
    /** True once the user typed in the Mod ID field; auto-suggestion then stops. */
    val modIdEdited: Boolean = false,
    /** True once the user typed in the package field; auto-suggestion then stops. */
    val packageEdited: Boolean = false,
    val touched: Set<FormField> = emptySet(),
    val showAllErrors: Boolean = false,
    val isCreating: Boolean = false,
    val submitError: String? = null,
    val created: Boolean = false
) {
    val availableVersions: List<MinecraftVersion>
        get() = MinecraftVersions.forLoader(loader)

    val javaVersion: Int?
        get() = MinecraftVersions.find(loader, minecraftVersion)?.javaVersion

    val mixinSupported: Boolean
        get() = LoaderTemplates.forLoader(loader).supportsMixin

    private fun rawError(field: FormField): String? = when (field) {
        FormField.NAME -> ProjectValidators.validateName(name)
        FormField.MOD_ID -> ProjectValidators.validateModId(modId.trim())
        FormField.PACKAGE -> ProjectValidators.validatePackage(packageName.trim())
        FormField.MOD_VERSION -> ProjectValidators.validateModVersion(modVersion.trim())
    }

    /** Error to display for a field: only after the field was touched or a submit was attempted. */
    fun errorFor(field: FormField): String? {
        if (!showAllErrors && field !in touched) return null
        return rawError(field)
    }

    val isFormValid: Boolean
        get() = FormField.entries.all { rawError(it) == null } &&
            javaVersion != null &&
            (mixinSupported || template != ProjectTemplate.MIXIN_READY)
}

class NewProjectViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewProjectUiState())
    val uiState: StateFlow<NewProjectUiState> = _uiState.asStateFlow()

    /** Applies a user edit. Ignored while creating; any edit clears the previous submit error. */
    private fun edit(transform: (NewProjectUiState) -> NewProjectUiState) {
        _uiState.update { state ->
            if (state.isCreating || state.created) state
            else transform(state).copy(submitError = null)
        }
    }

    fun onLoaderSelected(loader: Loader) = edit { s ->
        if (s.loader == loader) {
            s
        } else {
            val versions = MinecraftVersions.forLoader(loader)
            val version = if (versions.any { it.id == s.minecraftVersion }) {
                s.minecraftVersion
            } else {
                versions.first().id
            }
            // Keep the selected template valid for the new loader.
            val template = if (
                s.template == ProjectTemplate.MIXIN_READY &&
                !LoaderTemplates.forLoader(loader).supportsMixin
            ) {
                ProjectTemplate.BASIC
            } else {
                s.template
            }
            s.copy(loader = loader, minecraftVersion = version, template = template)
        }
    }

    fun onMinecraftVersionSelected(id: String) = edit { s ->
        if (MinecraftVersions.find(s.loader, id) != null) s.copy(minecraftVersion = id) else s
    }

    fun onTemplateSelected(template: ProjectTemplate) = edit { s ->
        val allowed = template != ProjectTemplate.MIXIN_READY || s.mixinSupported
        if (allowed) s.copy(template = template) else s
    }

    fun onNameChange(value: String) = edit { s ->
        val next = s.copy(name = value, touched = s.touched + FormField.NAME)
        if (next.modIdEdited) next else withSuggestedModId(next)
    }

    fun onModIdChange(value: String) = edit { s ->
        val next = s.copy(
            modId = value,
            modIdEdited = true,
            touched = s.touched + FormField.MOD_ID
        )
        if (next.packageEdited) next else withSuggestedPackage(next)
    }

    fun onPackageChange(value: String) = edit { s ->
        s.copy(
            packageName = value,
            packageEdited = true,
            touched = s.touched + FormField.PACKAGE
        )
    }

    fun onModVersionChange(value: String) = edit { s ->
        s.copy(modVersion = value, touched = s.touched + FormField.MOD_VERSION)
    }

    fun onCreateClicked() {
        val state = _uiState.value
        if (state.isCreating || state.created) return

        if (!state.isFormValid) {
            _uiState.update {
                it.copy(showAllErrors = true, submitError = "Fix the highlighted fields first.")
            }
            return
        }

        // Keep the previous submission error visible during retries. It is cleared
        // when the user edits a field, or when creation succeeds.
        _uiState.update {
            it.copy(showAllErrors = true, isCreating = true)
        }

        val request = NewProjectRequest(
            name = state.name.trim(),
            loader = state.loader,
            minecraftVersion = state.minecraftVersion,
            modId = state.modId.trim(),
            packageName = state.packageName.trim(),
            modVersion = state.modVersion.trim(),
            template = state.template
        )

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.createProject(request)
            }
            result
                .onSuccess {
                    _uiState.update { it.copy(isCreating = false, created = true) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            submitError = error.message ?: "Could not create the project."
                        )
                    }
                }
        }
    }

    // ---- auto-suggestion (reuses ProjectNaming; no duplicated rules) ----

    private fun withSuggestedModId(s: NewProjectUiState): NewProjectUiState {
        val suggested = if (s.name.isBlank()) "" else ProjectNaming.suggestModId(s.name)
        val next = s.copy(
            modId = suggested,
            touched = if (suggested.isNotEmpty()) s.touched + FormField.MOD_ID else s.touched
        )
        return if (next.packageEdited) next else withSuggestedPackage(next)
    }

    private fun withSuggestedPackage(s: NewProjectUiState): NewProjectUiState {
        val suggested = packageSuggestionFor(s.modId.trim())
        return s.copy(
            packageName = suggested,
            touched = if (suggested.isNotEmpty()) s.touched + FormField.PACKAGE else s.touched
        )
    }

    /** Package suggestion for a Mod ID; a half-typed or invalid Mod ID is sanitized first. */
    private fun packageSuggestionFor(modId: String): String {
        if (modId.isEmpty()) return ""
        val base = if (ProjectValidators.validateModId(modId) == null) {
            modId
        } else {
            ProjectNaming.suggestModId(modId)
        }
        return ProjectNaming.suggestPackage(base)
    }
}
