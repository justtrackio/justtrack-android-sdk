package io.justtrack.util

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileAccessorImplTest {

    private lateinit var context: Context
    private lateinit var subject: FileAccessorImpl

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Start from a clean filesDir so listFileNames assertions are deterministic.
        context.filesDir.listFiles()?.forEach { it.delete() }
        subject = FileAccessorImpl(context)
    }

    @After
    fun tearDown() {
        context.filesDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun listFileNames_returnsEmptyListWhenDirectoryIsEmpty() {
        assertEquals(emptyList<String>(), subject.listFileNames())
    }

    @Test
    fun listFileNames_returnsNamesOfFilesInFilesDir() {
        File(context.filesDir, "a.txt").writeText("alpha")
        File(context.filesDir, "b.txt").writeText("beta")

        val names = subject.listFileNames().sorted()

        assertEquals(listOf("a.txt", "b.txt"), names)
    }

    @Test
    fun listFileNames_returnsEmptyListWhenFilesDirListReturnsNull() {
        // File.list() returns null both for non-directory files AND non-existent paths; cover both.
        val notADirectory = File(context.filesDir, "not-a-directory")
        notADirectory.writeText("regular file, not a directory")
        assertTrue(notADirectory.exists() && notADirectory.isFile)
        assertEquals(null, notADirectory.list())

        val nonExistent = File(context.filesDir, "does-not-exist-dir")
        assertFalse(nonExistent.exists())
        assertEquals(null, nonExistent.list())

        for (target in listOf(notADirectory, nonExistent)) {
            val wrapper = object : android.content.ContextWrapper(context) {
                override fun getFilesDir(): File = target
            }
            assertEquals(emptyList<String>(), FileAccessorImpl(wrapper).listFileNames())
        }
    }

    @Test
    fun loadFile_returnsFullContentsOfFile() {
        val payload = "hello world\nsecond line"
        context.openFileOutput("greet.txt", Context.MODE_PRIVATE).use { it.write(payload.toByteArray()) }

        assertEquals(payload, subject.loadFile("greet.txt"))
    }

    @Test
    fun loadFile_returnsEmptyStringForEmptyFile() {
        context.openFileOutput("empty.txt", Context.MODE_PRIVATE).use { /* write nothing */ }

        assertEquals("", subject.loadFile("empty.txt"))
    }

    @Test(expected = java.io.FileNotFoundException::class)
    fun loadFile_propagatesFileNotFoundException() {
        subject.loadFile("does-not-exist.txt")
    }

    @Test
    fun deleteFile_removesAnExistingFile() {
        val target = File(context.filesDir, "to-delete.txt")
        target.writeText("bye")
        assertTrue(target.exists())

        subject.deleteFile("to-delete.txt")

        assertFalse(target.exists())
    }

    @Test
    fun deleteFile_doesNotThrowForMissingFile() {
        // Context.deleteFile returns false but should not throw.
        subject.deleteFile("never-existed.txt")
    }

    @Test
    fun getFile_returnsFileResolvedAgainstFilesDir() {
        val file = subject.getFile("nested/sample.txt")

        assertEquals(File(context.filesDir, "nested/sample.txt"), file)
        assertNotNull(file)
    }

    @Test
    fun createNewFile_createsFileOnDisk() {
        val file = File(context.filesDir, "fresh.txt")
        assertFalse(file.exists())

        subject.createNewFile(file)

        assertTrue(file.exists())
        assertEquals(0L, file.length())
    }

    @Test
    fun createNewFile_isANoOpWhenFileAlreadyExists() {
        val file = File(context.filesDir, "already-here.txt")
        file.writeText("existing")

        subject.createNewFile(file)

        assertTrue(file.exists())
        assertEquals("existing", file.readText())
    }

    @Test
    fun editFile_writesContentReplacingExistingData() {
        val file = File(context.filesDir, "editable.txt")
        file.writeText("old content")

        subject.editFile(file, "new content")

        assertEquals("new content", file.readText())
    }

    @Test
    fun editFile_canWriteEmptyContent() {
        val file = File(context.filesDir, "to-clear.txt")
        file.writeText("not empty")

        subject.editFile(file, "")

        assertEquals("", file.readText())
        assertTrue(file.exists())
    }
}
