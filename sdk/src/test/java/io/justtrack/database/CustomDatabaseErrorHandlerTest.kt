package io.justtrack.database

import io.justtrack.TestLogger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
internal class CustomDatabaseErrorHandlerTest {
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        dbFile = File(context.cacheDir, "corruption-test.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun onCorruption_nullDb_doesNothing() {
        val logger = spy(TestLogger())
        val subject = CustomDatabaseErrorHandler(logger)

        subject.onCorruption(null)

        verify(logger, never()).warn(any())
    }

    @Test
    fun onCorruption_nonNullDb_logsWarningClosesAndDeletesFile() {
        val logger = spy(TestLogger())
        val subject = CustomDatabaseErrorHandler(logger)
        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        assertTrue("DB file should exist before corruption", dbFile.exists())

        subject.onCorruption(db)

        val captor = argumentCaptor<String>()
        verify(logger, times(1)).warn(captor.capture())
        assertTrue(
            "Warning should mention DB path, was: ${captor.firstValue}",
            captor.firstValue.contains(dbFile.absolutePath),
        )
        assertFalse("DB file should be deleted after corruption", dbFile.exists())
        assertFalse("Closed DB should no longer be open", db.isOpen)
    }

    @Test
    fun setLogger_replacesLoggerUsedByOnCorruption() {
        val initial = spy(TestLogger())
        val replacement = spy(TestLogger())
        val subject = CustomDatabaseErrorHandler(initial)
        subject.setLogger(replacement)
        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)

        subject.onCorruption(db)

        verify(initial, never()).warn(any())
        verify(replacement, times(1)).warn(any())
    }

    private fun any(): String = org.mockito.kotlin.any()
}
