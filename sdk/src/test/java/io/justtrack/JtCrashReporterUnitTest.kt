package io.justtrack
import android.content.Context
import android.content.SharedPreferences
import io.justtrack.crashes.CrashType
import io.justtrack.dtos.LogLevel
import io.justtrack.exceptions.ANRException
import io.justtrack.log.Logger
import io.justtrack.util.FileAccessor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

class JtCrashReporterUnitTest {
    private lateinit var fileAccessor: FileAccessor
    private lateinit var logger: Logger
    private lateinit var mockFile: File
    private lateinit var context: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    // Valid native crash JSON (crashType defaults to NATIVE_CRASH = 1 when absent, but here we set it explicitly)
    private val validNativeCrashJson = JSONObject().apply {
        put(
            "data",
            JSONObject().apply {
                put("timestamp", "2024-01-01T00:00:00.000Z")
                put("stacktrace", "io.justtrack.SomeClass.method(SomeClass.kt:10)")
                put("signal", 8) // SIGFPE
            },
        )
        put("breadcrumbs", org.json.JSONArray())
    }.toString()

    private val validNormalCrashJson = JSONObject().apply {
        put(
            "data",
            JSONObject().apply {
                put("timestamp", "2024-01-01T00:00:00.000Z")
                put("crashType", CrashType.NORMAL_CRASH.id)
                put("stacktrace", "io.justtrack.SomeClass.method(SomeClass.kt:10)")
                put("name", "SomeException")
            },
        )
        put("breadcrumbs", org.json.JSONArray())
    }.toString()

    private val validAnrCrashJson = JSONObject().apply {
        put(
            "data",
            JSONObject().apply {
                put("timestamp", "2024-01-01T00:00:00.000Z")
                put("crashType", CrashType.ANR.id)
                put("stacktrace", "io.justtrack.SomeClass.method(SomeClass.kt:10)")
                put("name", "ANR")
            },
        )
        put("breadcrumbs", org.json.JSONArray())
    }.toString()

    private val corruptJson = "{ invalid json ,,, }"

    @Before
    fun setUp() {
        fileAccessor = mock()
        logger = mock()
        mockFile = mock()
        context = mock()
        mockSharedPreferences = mock()
        mockEditor = mock()

        whenever(mockEditor.remove(any())).thenReturn(mockEditor)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(mockSharedPreferences)

        whenever(fileAccessor.getFile(any())).thenReturn(mockFile)
        whenever(mockFile.exists()).thenReturn(false)
    }

    private fun createReporter(isTracking: Boolean = true) = JtCrashReporter(context, fileAccessor, logger, Formatter, AtomicBoolean(isTracking))

    // region captureException

    @Test
    fun captureException_normalThrowable_storesNormalCrashType() {
        val reporter = createReporter()
        val throwable = RuntimeException("test error")

        reporter.captureException(throwable)

        val contentCaptor = argumentCaptor<String>()
        verify(fileAccessor).editFile(any(), contentCaptor.capture())
        val written = JSONObject(contentCaptor.firstValue)
        assertEquals(CrashType.NORMAL_CRASH.id, written.getJSONObject("data").getInt("crashType"))
    }

    @Test
    fun captureException_anrException_storesANRCrashType() {
        val reporter = createReporter()
        val anr = ANRException("anr message", emptyArray())

        reporter.captureException(anr)

        val contentCaptor = argumentCaptor<String>()
        verify(fileAccessor).editFile(any(), contentCaptor.capture())
        val written = JSONObject(contentCaptor.firstValue)
        assertEquals(CrashType.ANR.id, written.getJSONObject("data").getInt("crashType"))
    }

    // endregion

    // region close

    @Test
    fun close_doesNotThrow() {
        val reporter = createReporter()
        reporter.close()
        verify(fileAccessor, never()).deleteFile(any())
        verify(logger, never()).error(any<String>(), any<io.justtrack.log.LoggerFields>())
    }

    // endregion

    // region init

    @Test
    fun init_removesKeyUncaughtExceptionListFromSharedPreferences() {
        createReporter()

        verify(context).getSharedPreferences("justtrack-crash-report", Context.MODE_PRIVATE)
        verify(mockEditor).remove("KEY_UNCAUGHT_EXCEPTION_LIST")
        verify(mockEditor).apply()
    }

    // endregion

    // region addBreadCrumb

    @Test
    fun addBreadCrumb_whenTracking_breadcrumbAppearsInWrittenFile() {
        val reporter = createReporter(isTracking = true)
        val breadCrumb = BreadCrumb("hello breadcrumb", "test", LogLevel.INFO, Date())

        reporter.addBreadCrumb(breadCrumb)
        reporter.storeUncaughtException(message = "crash", stacktrace = null as String?, date = Date())

        val contentCaptor = argumentCaptor<String>()
        verify(fileAccessor).editFile(any(), contentCaptor.capture())
        val written = JSONObject(contentCaptor.firstValue)
        val breadcrumbs = written.getJSONArray("breadcrumbs")
        assertEquals(1, breadcrumbs.length())
        assertEquals("hello breadcrumb", breadcrumbs.getJSONObject(0).getString("message"))
    }

    @Test
    fun addBreadCrumb_whenNotTracking_breadcrumbDoesNotAppearInWrittenFile() {
        val reporter = createReporter(isTracking = false)
        val breadCrumb = BreadCrumb("ignored breadcrumb", "test", LogLevel.INFO, Date())

        reporter.addBreadCrumb(breadCrumb)
        reporter.storeUncaughtException(message = "crash", stacktrace = null as String?, date = Date())

        val contentCaptor = argumentCaptor<String>()
        verify(fileAccessor).editFile(any(), contentCaptor.capture())
        val written = JSONObject(contentCaptor.firstValue)
        assertEquals(0, written.getJSONArray("breadcrumbs").length())
    }

    // endregion

    // region report

    @Test
    fun report_emptyFileList_doesNotInteractWithLogger() = runBlocking {
        whenever(fileAccessor.listFileNames()).thenReturn(emptyList())
        val reporter = createReporter()

        reporter.report().join()

        verify(logger, never()).error(any<String>(), any<io.justtrack.log.LoggerFields>())
        verify(fileAccessor, never()).deleteFile(any())
    }

    @Test
    fun report_validNativeCrashFile_callsLoggerErrorAndDeletesFile() = runBlocking {
        val fileName = "${JtCrashReporter.STACKTRACE_FILE_PREFIX}2024-01-01T00:00:00.000Z"
        whenever(fileAccessor.listFileNames()).thenReturn(listOf(fileName))
        whenever(fileAccessor.loadFile(fileName)).thenReturn(validNativeCrashJson)
        val reporter = createReporter()

        reporter.report().join()

        verify(logger, times(1)).error(eq("Application shutdown due to crash"), any<io.justtrack.log.LoggerFields>())
        verify(fileAccessor, times(1)).deleteFile(fileName)
    }

    @Test
    fun report_validNormalCrashFile_callsLoggerErrorWithNormalCrashType() = runBlocking {
        val fileName = "${JtCrashReporter.CRASH_FILE_PREFIX}2024-01-01T00:00:00.000Z.json"
        whenever(fileAccessor.listFileNames()).thenReturn(listOf(fileName))
        whenever(fileAccessor.loadFile(fileName)).thenReturn(validNormalCrashJson)
        val reporter = createReporter()

        reporter.report().join()

        val fieldsCaptor = argumentCaptor<io.justtrack.log.LoggerFields>()
        verify(logger, times(1)).error(eq("Application shutdown due to crash"), fieldsCaptor.capture())
        assertEquals(CrashType.NORMAL_CRASH.toString(), (fieldsCaptor.firstValue as io.justtrack.log.LoggerFieldsBuilder).fields["type"])
    }

    @Test
    fun report_validAnrCrashFile_callsLoggerErrorWithANRType() = runBlocking {
        val fileName = "${JtCrashReporter.CRASH_FILE_PREFIX}2024-01-01T00:00:00.000Z.json"
        whenever(fileAccessor.listFileNames()).thenReturn(listOf(fileName))
        whenever(fileAccessor.loadFile(fileName)).thenReturn(validAnrCrashJson)
        val reporter = createReporter()

        reporter.report().join()

        val fieldsCaptor = argumentCaptor<io.justtrack.log.LoggerFields>()
        verify(logger, times(1)).error(eq("Application shutdown due to crash"), fieldsCaptor.capture())
        assertEquals(CrashType.ANR.toString(), (fieldsCaptor.firstValue as io.justtrack.log.LoggerFieldsBuilder).fields["type"])
    }

    @Test
    fun report_corruptFile_logsWarningAndDeletesFile() = runBlocking {
        val fileName = "${JtCrashReporter.CRASH_FILE_PREFIX}bad.json"
        whenever(fileAccessor.listFileNames()).thenReturn(listOf(fileName))
        whenever(fileAccessor.loadFile(fileName)).thenReturn(corruptJson)
        val reporter = createReporter()

        reporter.report().join()

        verify(logger, times(1)).warn(eq("Unable to read stored crash file"), any<Throwable>())
        verify(fileAccessor, times(2)).deleteFile(fileName)
        verify(logger, never()).error(eq("Application shutdown due to crash"), any<io.justtrack.log.LoggerFields>())
    }

    @Test
    fun report_multipleFiles_processesAndDeletesAll() = runBlocking {
        val file1 = "${JtCrashReporter.CRASH_FILE_PREFIX}first.json"
        val file2 = "${JtCrashReporter.CRASH_FILE_PREFIX}second.json"
        whenever(fileAccessor.listFileNames()).thenReturn(listOf(file1, file2))
        whenever(fileAccessor.loadFile(file1)).thenReturn(validNormalCrashJson)
        whenever(fileAccessor.loadFile(file2)).thenReturn(validAnrCrashJson)
        val reporter = createReporter()

        reporter.report().join()

        verify(fileAccessor, times(1)).deleteFile(file1)
        verify(fileAccessor, times(1)).deleteFile(file2)
        verify(logger, times(2)).error(eq("Application shutdown due to crash"), any<io.justtrack.log.LoggerFields>())
    }

    // endregion

    // region storeUncaughtException

    @Test
    fun storeUncaughtException_stringOverload_writesCorrectJSONFields() {
        val reporter = createReporter()
        val date = Date(1592228819000L)
        val expectedTimestamp = Formatter.formatDateMilliseconds(date)

        reporter.storeUncaughtException(
            crashType = CrashType.NORMAL_CRASH.id,
            message = "boom",
            stacktrace = "at io.justtrack.Foo.bar(Foo.kt:42)",
            date = date,
        )

        val contentCaptor = argumentCaptor<String>()
        verify(fileAccessor).editFile(any(), contentCaptor.capture())
        val data = JSONObject(contentCaptor.firstValue).getJSONObject("data")
        assertEquals(expectedTimestamp, data.getString("timestamp"))
        assertEquals(CrashType.NORMAL_CRASH.id, data.getInt("crashType"))
        assertEquals("boom", data.getString("name"))
        assertEquals("at io.justtrack.Foo.bar(Foo.kt:42)", data.getString("stacktrace"))
    }

    @Test
    fun storeUncaughtException_arrayOverload_joinsStacktraceWithNewlines() {
        val reporter = createReporter()
        val elements = arrayOf(
            StackTraceElement("io.justtrack.Foo", "bar", "Foo.kt", 42),
            StackTraceElement("io.justtrack.Baz", "qux", "Baz.kt", 7),
        )

        reporter.storeUncaughtException(
            crashType = CrashType.NORMAL_CRASH.id,
            message = "error",
            stacktrace = elements,
            date = Date(),
        )

        val contentCaptor = argumentCaptor<String>()
        verify(fileAccessor).editFile(any(), contentCaptor.capture())
        val stacktrace = JSONObject(contentCaptor.firstValue).getJSONObject("data").getString("stacktrace")
        val expected = elements.joinToString(separator = "\n") { it.toString() }
        assertEquals(expected, stacktrace)
    }

    @Test
    fun storeUncaughtException_fileDoesNotExist_callsCreateNewFile() {
        whenever(mockFile.exists()).thenReturn(false)
        val reporter = createReporter()

        reporter.storeUncaughtException(message = "crash", stacktrace = null as String?, date = Date())

        verify(fileAccessor, times(1)).createNewFile(mockFile)
    }

    @Test
    fun storeUncaughtException_fileAlreadyExists_skipsCreateNewFile() {
        whenever(mockFile.exists()).thenReturn(true)
        val reporter = createReporter()

        reporter.storeUncaughtException(message = "crash", stacktrace = null as String?, date = Date())

        verify(fileAccessor, never()).createNewFile(any())
    }

    @Test
    fun storeUncaughtException_exceptionThrown_logsWarning() {
        whenever(fileAccessor.getFile(any())).doThrow(RuntimeException("disk full"))
        val reporter = createReporter()

        reporter.storeUncaughtException(message = "crash", stacktrace = null as String?, date = Date())

        verify(logger, times(1)).warn(eq("Unable to store UncaughtException"), any<Throwable>())
    }

    @Test
    fun storeUncaughtException_usesCorrectFileNamePrefix() {
        val reporter = createReporter()
        val date = Date(1592228819000L)
        val expectedTimestamp = Formatter.formatDateMilliseconds(date)

        reporter.storeUncaughtException(message = "crash", stacktrace = null as String?, date = date)

        val fileNameCaptor = argumentCaptor<String>()
        verify(fileAccessor).getFile(fileNameCaptor.capture())
        assertTrue(fileNameCaptor.firstValue.startsWith(JtCrashReporter.CRASH_FILE_PREFIX))
        assertTrue(fileNameCaptor.firstValue.contains(expectedTimestamp))
    }

    // endregion

    // region getCacheFileNames

    @Test
    fun getCacheFileNames_returnsOnlyPrefixMatchingNames() {
        val allFiles = listOf(
            "${JtCrashReporter.CRASH_FILE_PREFIX}crash.json",
            "${JtCrashReporter.STACKTRACE_FILE_PREFIX}native",
            "unrelated_file.txt",
            "another_random.json",
        )
        whenever(fileAccessor.listFileNames()).thenReturn(allFiles)

        val reporter = createReporter()
        val result = reporter.getCacheFileNames()

        assertEquals(2, result.size)
        assertTrue(result.any { it.startsWith(JtCrashReporter.CRASH_FILE_PREFIX) })
        assertTrue(result.any { it.startsWith(JtCrashReporter.STACKTRACE_FILE_PREFIX) })
    }

    @Test
    fun getCacheFileNames_whenListFileNamesReturnsEmpty_returnsEmptyList() {
        whenever(fileAccessor.listFileNames()).thenReturn(emptyList())

        val reporter = createReporter()
        val result = reporter.getCacheFileNames()

        assertEquals(0, result.size)
    }
}
