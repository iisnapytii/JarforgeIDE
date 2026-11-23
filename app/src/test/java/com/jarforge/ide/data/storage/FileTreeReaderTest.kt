package com.jarforge.ide.data.storage

import com.jarforge.ide.data.model.FilePreview
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Assert.fail

class FileTreeReaderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun fixture(): Pair<FileTreeReader, File> {
        val root = temporaryFolder.newFolder("projects")
        val project = File(root, "test-project").apply { mkdirs() }
        return FileTreeReader(ProjectStorage(root)) to project
    }

    @Test fun readsValidUtf8AndReportsExactByteSize() {
        val (reader, project) = fixture()
        val file = File(project, "text.txt").apply { writeText("héllo", Charsets.UTF_8) }
        val preview = reader.readTextPreview("test-project", "text.txt") as FilePreview.Text
        assertEquals("héllo", preview.content)
        assertEquals(file.length(), preview.sizeBytes)
    }

    @Test fun refusesFilesAbovePreviewLimit() {
        val (reader, project) = fixture()
        File(project, "large.txt").writeBytes(ByteArray(FilePreview.MAX_BYTES + 1) { 'a'.code.toByte() })
        val preview = reader.readTextPreview("test-project", "large.txt")
        assertTrue(preview is FilePreview.TooLarge)
    }

    @Test fun refusesBinaryAndMalformedUtf8() {
        val (reader, project) = fixture()
        File(project, "binary.bin").writeBytes(byteArrayOf(0, 1, 2))
        File(project, "invalid.txt").writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
        assertTrue(reader.readTextPreview("test-project", "binary.bin") is FilePreview.NotText)
        assertTrue(reader.readTextPreview("test-project", "invalid.txt") is FilePreview.NotText)
    }

    @Test fun writesAtExactEditableLimit() {
        val (reader, project) = fixture()
        val file = File(project, "text.txt").apply { writeText("old") }
        val content = "x".repeat(FileTreeReader.MAX_EDITABLE_BYTES)
        assertEquals(FileTreeReader.MAX_EDITABLE_BYTES.toLong(), reader.writeTextFile("test-project", "text.txt", content))
        assertEquals(content, file.readText())
    }

    @Test fun enforcesEditableLimitUsingUtf8BytesNotCharacterCount() {
        val (reader, project) = fixture()
        val file = File(project, "unicode.txt").apply { writeText("old") }
        val exact = "é".repeat(FileTreeReader.MAX_EDITABLE_BYTES / 2)
        assertEquals(FileTreeReader.MAX_EDITABLE_BYTES.toLong(), reader.writeTextFile("test-project", "unicode.txt", exact))
        assertEquals(exact, file.readText(Charsets.UTF_8))
        try {
            reader.writeTextFile("test-project", "unicode.txt", exact + "é")
            fail("Expected multibyte UTF-8 overflow to be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals(exact, file.readText(Charsets.UTF_8))
        }
    }

    @Test fun rejectsOversizedWriteWithoutChangingOriginalFile() {
        val (reader, project) = fixture()
        val file = File(project, "text.txt").apply { writeText("original") }
        val oversized = "x".repeat(FileTreeReader.MAX_EDITABLE_BYTES + 1)
        try {
            reader.writeTextFile("test-project", "text.txt", oversized)
            fail("Expected oversized write to be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals("original", file.readText())
        }
    }

    @Test fun rejectsTraversalAndMetadataWrites() {
        val (reader, project) = fixture()
        File(project, "text.txt").writeText("original")
        val outside = File(project.parentFile, "outside.txt").apply { writeText("outside") }
        assertWriteRejected(reader, "../outside.txt")
        assertWriteRejected(reader, ".jarforge/project.json")
        assertEquals("outside", outside.readText())
    }

    @Test fun rejectsSymbolicLinkTargets() {
        val (reader, project) = fixture()
        val outside = File(project.parentFile, "outside.txt").apply { writeText("safe") }
        val link = File(project, "link.txt")
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
        } catch (_: UnsupportedOperationException) {
            return
        } catch (_: SecurityException) {
            return
        }
        assertWriteRejected(reader, "link.txt")
        assertEquals("safe", outside.readText())
        assertTrue(Files.isSymbolicLink(link.toPath()))
    }

    private fun assertWriteRejected(reader: FileTreeReader, path: String) {
        try {
            reader.writeTextFile("test-project", path, "changed")
            fail("Expected write to $path to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }
}
