package com.jarforge.ide.feature.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModelStore
import com.jarforge.ide.data.model.FilePreview
import com.jarforge.ide.data.model.Loader
import com.jarforge.ide.data.model.Project
import com.jarforge.ide.data.model.ProjectTemplate
import com.jarforge.ide.data.storage.ProjectRepository
import com.jarforge.ide.data.storage.ProjectStorage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalFoundationApi::class)
class EditorViewModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val mainDispatcher = UnconfinedTestDispatcher()
    private val viewModelStore = ViewModelStore()

    @Before fun setMainDispatcher() { Dispatchers.setMain(mainDispatcher) }
    @After fun resetMainDispatcher() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    private fun createFixture(): Pair<EditorViewModel, File> {
        val root = temporaryFolder.newFolder("projects")
        val storage = ProjectStorage(root)
        val projectDir = storage.projectDir("editor-test").apply { mkdirs() }
        val project = Project(
            id = "editor-test",
            name = "Editor Test",
            loader = Loader.FABRIC,
            minecraftVersion = "1.21.1",
            modId = "editor_test",
            packageName = "com.example.editortest",
            modVersion = "1.0.0",
            template = ProjectTemplate.BASIC,
            createdAtMillis = 1L,
            modifiedAtMillis = 1L
        )
        storage.writeProject(projectDir, project)
        val file = File(projectDir, "example.txt").apply { writeText("before", Charsets.UTF_8) }
        val viewModel = EditorViewModel(ProjectRepository(storage), project.id)
        viewModelStore.put("editor", viewModel)
        return viewModel to file
    }

    private suspend fun EditorViewModel.openTestFile() {
        onFileClicked("example.txt")
        withTimeout(5_000) {
            uiState.first { it.preview is PreviewState.Ready }
        }
    }

    @Test fun dirtyStateTracksTextAndSaveResetsIt() = runBlocking {
        val (viewModel, file) = createFixture()
        viewModel.openTestFile()
        viewModel.textState.fieldState.edit { placeCursorAtEnd() }
        assertTrue(viewModel.textState.insertText("!"))
        withTimeout(5_000) { viewModel.uiState.first { it.editorContent == "before!" } }
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.onSaveFile()
        withTimeout(5_000) {
            viewModel.uiState.first { it.savedContent == "before!" && !it.isSaving }
        }
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals("before!", file.readText(Charsets.UTF_8))
    }

    @Test fun undoAndRedoUpdateDirtyStateThroughTextMirror() = runBlocking {
        val (viewModel, _) = createFixture()
        viewModel.openTestFile()
        viewModel.textState.fieldState.edit { placeCursorAtEnd() }
        viewModel.textState.insertText("!")
        withTimeout(5_000) { viewModel.uiState.first { it.editorContent == "before!" } }
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.textState.undo()
        withTimeout(5_000) { viewModel.uiState.first { it.editorContent == "before" } }
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.textState.redo()
        withTimeout(5_000) { viewModel.uiState.first { it.editorContent == "before!" } }
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test fun discardRestoresSavedTextAndClearsPendingAction() = runBlocking {
        val (viewModel, _) = createFixture()
        viewModel.openTestFile()
        viewModel.textState.fieldState.edit { placeCursorAtEnd() }
        viewModel.textState.insertText(" changed")
        withTimeout(5_000) { viewModel.uiState.first { it.editorContent == "before changed" } }

        viewModel.onNavigateBackRequested()
        assertNotNull(viewModel.uiState.value.pendingAction)
        viewModel.onDiscardAndProceed()

        assertEquals("before", viewModel.textState.currentText())
        assertEquals("before", viewModel.uiState.value.editorContent)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals(null, viewModel.uiState.value.pendingAction)
    }

    @Test fun cancelPendingNavigationKeepsUnsavedChanges() = runBlocking {
        val (viewModel, _) = createFixture()
        viewModel.openTestFile()
        viewModel.textState.insertText("changed")
        withTimeout(5_000) { viewModel.uiState.first { it.editorContent == "changedbefore" } }

        viewModel.onNavigateBackRequested()
        viewModel.onDismissPendingAction()

        assertEquals(null, viewModel.uiState.value.pendingAction)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals("changedbefore", viewModel.textState.currentText())
    }

    @Test fun saveAndProceedPersistsSnapshotBeforeCompletingPendingAction() = runBlocking {
        val (viewModel, file) = createFixture()
        viewModel.openTestFile()
        viewModel.textState.fieldState.edit { selection = TextRange(6) }
        viewModel.textState.insertText("!")
        withTimeout(5_000) { viewModel.uiState.first { it.editorContent == "before!" } }

        val backEvent = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.navigateBackEvents.first()
        }
        viewModel.onNavigateBackRequested()
        viewModel.onSaveAndProceed()
        withTimeout(5_000) {
            viewModel.uiState.first { it.savedContent == "before!" && !it.isSaving }
        }

        assertEquals("before!", file.readText(Charsets.UTF_8))
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        backEvent.await()
    }

    @Test fun failedSaveKeepsUnsavedTextAndReportsError() = runBlocking {
        val (viewModel, file) = createFixture()
        viewModel.openTestFile()
        viewModel.textState.fieldState.edit { placeCursorAtEnd() }
        viewModel.textState.insertText("!")
        withTimeout(5_000) { viewModel.uiState.first { it.editorContent == "before!" } }
        assertTrue(file.delete())

        viewModel.onSaveFile()
        withTimeout(5_000) {
            viewModel.uiState.first { it.saveError != null && !it.isSaving }
        }
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals("before!", viewModel.textState.currentText())
    }

    @Test fun nonTextPreviewIsNotEditableAndCannotBecomeDirty() = runBlocking {
        val (viewModel, file) = createFixture()
        file.writeBytes(byteArrayOf(0, 1, 2))
        viewModel.openTestFile()
        assertTrue((viewModel.uiState.value.preview as PreviewState.Ready).preview is FilePreview.NotText)
        viewModel.onContentChanged("ignored")
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }
}
