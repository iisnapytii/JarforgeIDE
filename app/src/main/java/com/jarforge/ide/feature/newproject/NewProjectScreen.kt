package com.jarforge.ide.feature.newproject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarforge.ide.data.model.ProjectTemplate
import com.jarforge.ide.ui.AppViewModelProvider
import com.jarforge.ide.ui.components.JarforgeTextField
import com.jarforge.ide.ui.components.JarforgeTopBar
import com.jarforge.ide.ui.components.WarningNotice
import com.jarforge.ide.ui.rememberAppContainer
import com.jarforge.ide.ui.theme.JarforgeColors

private const val SOURCE_SKELETON_WARNING =
    "Source skeleton only. Build not verified. Gradle files are not generated yet."

@Composable
fun NewProjectScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: NewProjectViewModel = viewModel(
        factory = AppViewModelProvider.newProjectFactory(container)
    )
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val currentOnBack by rememberUpdatedState(onBack)
    val inputsEnabled = !state.isCreating

    // On success, return to Projects. Projects refreshes itself when it re-enters composition.
    LaunchedEffect(state.created) {
        if (state.created) currentOnBack()
    }

    val nameError = state.errorFor(FormField.NAME)
    val modIdError = state.errorFor(FormField.MOD_ID)
    val packageError = state.errorFor(FormField.PACKAGE)
    val versionError = state.errorFor(FormField.MOD_VERSION)

    val identifierKeyboard = KeyboardOptions(
        capitalization = KeyboardCapitalization.None,
        keyboardType = KeyboardType.Ascii,
        imeAction = ImeAction.Next
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Insets are handled below (ime + navigation bars) to avoid double padding.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            JarforgeTopBar(
                title = "New Mod Project",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            WarningNotice(text = SOURCE_SKELETON_WARNING)

            FormSection(title = "Loader") {
                LoaderSelector(
                    selected = state.loader,
                    onSelected = viewModel::onLoaderSelected,
                    enabled = inputsEnabled
                )
            }

            FormSection(title = "Minecraft version") {
                VersionSelector(
                    versions = state.availableVersions,
                    selected = state.minecraftVersion,
                    onSelected = viewModel::onMinecraftVersionSelected,
                    enabled = inputsEnabled
                )
                val java = state.javaVersion
                if (java != null) {
                    Text(
                        text = "Requires Java $java",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FormSection(title = "Template") {
                ProjectTemplate.entries.forEach { template ->
                    val available = template != ProjectTemplate.MIXIN_READY || state.mixinSupported
                    TemplateOption(
                        title = template.displayName,
                        description = template.description,
                        selected = state.template == template,
                        available = available,
                        enabled = inputsEnabled,
                        onClick = { viewModel.onTemplateSelected(template) },
                        unavailableReason = if (available) null else
                            "Unavailable for ${state.loader.displayName}: " +
                                "Jarforge does not support Mixin setup for it yet."
                    )
                }
            }

            FormSection(title = "Project") {
                JarforgeTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Project name",
                    enabled = inputsEnabled,
                    isError = nameError != null,
                    supportingText = nameError,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    )
                )
                JarforgeTextField(
                    value = state.modId,
                    onValueChange = viewModel::onModIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Mod ID",
                    enabled = inputsEnabled,
                    isError = modIdError != null,
                    supportingText = modIdError ?: "Unique identifier, for example my_mod",
                    keyboardOptions = identifierKeyboard
                )
                JarforgeTextField(
                    value = state.packageName,
                    onValueChange = viewModel::onPackageChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Package name",
                    enabled = inputsEnabled,
                    isError = packageError != null,
                    supportingText = packageError ?: "Java package for the generated sources",
                    keyboardOptions = identifierKeyboard
                )
                JarforgeTextField(
                    value = state.modVersion,
                    onValueChange = viewModel::onModVersionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Mod version",
                    enabled = inputsEnabled,
                    isError = versionError != null,
                    supportingText = versionError ?: "For example 1.0.0",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }

            val submitError = state.submitError
            if (submitError != null) {
                Text(
                    text = submitError,
                    style = MaterialTheme.typography.bodySmall,
                    color = JarforgeColors.Danger
                )
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.onCreateClicked()
                },
                enabled = !state.isCreating,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creating...")
                } else {
                    Text("Create project")
                }
            }
        }
    }
}
