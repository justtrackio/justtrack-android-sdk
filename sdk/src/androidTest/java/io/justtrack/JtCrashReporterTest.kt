package io.justtrack

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.crashes.CrashType
import io.justtrack.crashes.NativeSignal
import io.justtrack.dtos.LogLevel
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.util.FileAccessorImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.OutputStreamWriter
import java.util.Calendar
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class JtCrashReporterTest {
    lateinit var context: Context
    private val formatter = Formatter

    private val corruptDataJson = "{\n" +
        "\t\"data\": {\n" +
        "\t\t\"crashType\": \"NATIVE_CRASH\",\n" +
        "\t\t,\n" +
        "}"

    private val corruptBreadCrumbJson = "{\n" +
        "\t\"data\": {\n" +
        "\t\t\"crashType\": \"NATIVE_CRASH\",\n" +
        "\t\t\"stackTrace\": [io.justtrack],\n" +
        "\t\t\"name\": \"crash_name\"\n" +
        "\t},\n" +
        "\t\"breadcrumbs\": [{\n" +
        "\t\t\"message\": \"breadcrumb1\",\n" +
        "\t\t]\n" +
        "}"

    private val corruptJson = "{\n" +
        "\t\"data\": {\n" +
        "}"

    private val incompleteDataJson = "{\n" +
        "\t\"data\": {\n" +
        "\t},\n" +
        "\t\"breadcrumbs\": [{\n" +
        "\t\t\"message\": \"breadcrumb1\",\n" +
        "\t\t\"category\": \"logs\",\n" +
        "\t\t\"level\": \"error\",\n" +
        "\t\t\"timeStamp\": null\n" +
        "\t}]\n" +
        "}"

    private val incompleteBreadCrumbJson = "{\n" +
        "\t\"data\": {\n" +
        "\t\t\"crashType\": \"NATIVE_CRASH\",\n" +
        "\t\t\"stackTrace\": [io.justtrack],\n" +
        "\t\t\"name\": \"crash_name\"\n" +
        "\t},\n" +
        "\t\"breadcrumbs\": [{\n" +
        "\t\t\"level\": \"error\",\n" +
        "\t\t\"timeStamp\": null\n" +
        "\t}]\n" +
        "}"

    @Before
    fun createDb() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun testReport_NativeCrash() = runBlocking {
        val logger = mock<Logger>()
        val date = Date()
        storeNativeCrashFile(
            context,
            formatter.formatDateMilliseconds(date),
            generateNativeCrashMock(timeStamp = formatter.formatDateMilliseconds(date), formatter = formatter).toString(),
        )
        val fileAccessor = FileAccessorImpl(context)
        val crashReporter = JtCrashReporter(context, fileAccessor, logger, Formatter, AtomicBoolean(true))
        crashReporter.report().join()

        val dimensionCaptor = argumentCaptor<LoggerFieldsBuilder>()
        verify(logger, times(1)).error(
            eq("Application shutdown due to crash"),
            dimensionCaptor.capture(),
        )

//        val breadCrumbDimensionCaptor = argumentCaptor<LoggerFieldsImpl>()
//        verify(logger, atLeastOnce()).error(
//            eq("Application shutdown with breadcrumb"),
//            breadCrumbDimensionCaptor.capture(),
//        )

        Assert.assertEquals(CrashType.NATIVE_CRASH.toString(), dimensionCaptor.firstValue.fields["type"])
        Assert.assertEquals(formatter.formatDateMilliseconds(date), dimensionCaptor.firstValue.fields["timestamp"])

//        if (breadCrumbDimensionCaptor.allValues.isEmpty()) {
//            Assert.fail("No bread crumbs")
//        }

//        breadCrumbDimensionCaptor.allValues.forEach {
//            val resultBreadCrumbJsonArray = JSONArray(it.fields["breadcrumb"])
//            Assert.assertEquals(defaultBreadCrumb.toJSON(formatter).toString(), resultBreadCrumbJsonArray[0].toString())
//            Assert.assertEquals(
//                defaultBreadCrumb.toJSON(formatter).apply {
//                    put("message", "breadcrumb2")
//                }.toString(),
//                resultBreadCrumbJsonArray[1].toString(),
//            )
//        }
    }

    @Test
    fun testReport_NativeCrash_without_breadcrumb() = runBlocking {
        val logger = mock<Logger>()
        val date = Date()
        storeNativeCrashFile(
            context,
            formatter.formatDateMilliseconds(date),
            generateNativeCrashMock(timeStamp = formatter.formatDateMilliseconds(date), formatter = formatter, breadCrumbList = listOf()).toString(),
        )
        val fileAccessor = FileAccessorImpl(context)
        val crashReporter = JtCrashReporter(context, fileAccessor, logger, Formatter, AtomicBoolean(true))
        crashReporter.report().join()

        val dimensionCaptor = argumentCaptor<LoggerFieldsBuilder>()

//        val breadCrumbDimensionCaptor = argumentCaptor<LoggerFieldsImpl>()

        verify(logger, times(1)).error(
            eq("Application shutdown due to crash"),
            dimensionCaptor.capture(),
        )

//        verify(logger, atLeastOnce()).error(
//            eq("Application shutdown with breadcrumb"),
//            breadCrumbDimensionCaptor.capture(),
//        )

        Assert.assertEquals(CrashType.NATIVE_CRASH.toString(), dimensionCaptor.firstValue.fields["type"])
        Assert.assertEquals(formatter.formatDateMilliseconds(date), dimensionCaptor.firstValue.fields["timestamp"])

//        if (breadCrumbDimensionCaptor.allValues.isEmpty()) {
//            Assert.fail("No bread crumbs")
//        }

//        breadCrumbDimensionCaptor.allValues.forEach {
//            val resultBreadCrumbJsonArray = JSONArray(it.fields["breadcrumb"])
//            Assert.assertEquals(0, resultBreadCrumbJsonArray.length())
//        }
    }

    @Test
    fun testReport_NativeCrash_unableToReadFile() = runBlocking {
        val logger = mock<Logger>()
        val date = Date()

        // corrupt whole file should not report crash
        storeNativeCrashFile(
            context,
            formatter.formatDateMilliseconds(date),
            corruptDataJson,
        )

        // corrupt data object should not report crash
        storeNativeCrashFile(
            context,
            formatter.formatDateMilliseconds(date),
            corruptJson,
        )

        // corrupt breadcrumb should not report crash
        storeNativeCrashFile(
            context,
            formatter.formatDateMilliseconds(date),
            corruptBreadCrumbJson,
        )

        // incomplete data object should not report crash
        storeNativeCrashFile(
            context,
            formatter.formatDateMilliseconds(date),
            incompleteDataJson,
        )

        // incomplete breadcrumb should report crash
        storeNativeCrashFile(
            context,
            formatter.formatDateMilliseconds(date),
            incompleteBreadCrumbJson,
        )
        val fileAccessor = FileAccessorImpl(context)
        val crashReporter = JtCrashReporter(context, fileAccessor, logger, Formatter, AtomicBoolean(true))
        crashReporter.report().join()

        val dimensionCaptor = argumentCaptor<LoggerFieldsBuilder>()

        verify(logger, times(1)).error(
            eq("Application shutdown due to crash"),
            dimensionCaptor.capture(),
        )

        Assert.assertEquals(CrashType.NATIVE_CRASH.toString(), dimensionCaptor.firstValue.fields["type"])
    }

    @Test
    fun testReport_NativeCrash_null_timestamp() = runBlocking {
        val logger = mock<Logger>()
        storeNativeCrashFile(
            context,
            null,
            generateNativeCrashMock(timeStamp = null, formatter = formatter).toString(),
        )
        val fileAccessor = FileAccessorImpl(context)
        val crashReporter = JtCrashReporter(context, fileAccessor, logger, Formatter, AtomicBoolean(true))
        crashReporter.report().join()

        val dimensionCaptor = argumentCaptor<LoggerFieldsBuilder>()
        verify(logger, times(1)).error(
            eq("Application shutdown due to crash"),
            dimensionCaptor.capture(),
        )

        Assert.assertEquals(CrashType.NATIVE_CRASH.toString(), dimensionCaptor.firstValue.fields["type"])
    }

    @Test
    fun testReport_NoCrash() = runBlocking {
        val logger = mock<Logger>()
        val fileAccessor = FileAccessorImpl(context)
        val crashReporter = JtCrashReporter(context, fileAccessor, logger, Formatter, AtomicBoolean(true))

        crashReporter.report().join()

        verify(logger, never()).error(
            any(),
        )
    }

    @Test
    fun testReport_PreviouslyReportedCrash_NotReportAgain() = runBlocking {
        val logger = mock<Logger>()
        val nativeCrashDate = Date()
        val normalCrashDate = Calendar.getInstance().apply {
            time = nativeCrashDate
            add(Calendar.SECOND, 10)
        }

        storeNativeCrashFile(
            context,
            formatter.formatDateMilliseconds(nativeCrashDate),
            generateNativeCrashMock(timeStamp = formatter.formatDateMilliseconds(nativeCrashDate), formatter = formatter).toString(),
        )
        val fileAccessor = FileAccessorImpl(context)
        val crashReporter = JtCrashReporter(context, fileAccessor, logger, Formatter, AtomicBoolean(true))
        val throwable = Throwable("crash")
        throwable.stackTrace.plus(StackTraceElement("declareClass", "method", "file", 100))

        crashReporter.storeUncaughtException(
            crashType = CrashType.NORMAL_CRASH.id,
            message = "crash",
            stacktrace = "",
            date = normalCrashDate.time,
        )
        crashReporter.report().join()
        crashReporter.report().join()

        val dimensionCaptor = argumentCaptor<LoggerFieldsBuilder>()
        verify(logger, times(2)).error(
            eq("Application shutdown due to crash"),
            dimensionCaptor.capture(),
        )

        Assert.assertEquals(CrashType.NATIVE_CRASH.toString(), dimensionCaptor.firstValue.fields["type"])
        Assert.assertEquals(formatter.formatDateMilliseconds(nativeCrashDate), dimensionCaptor.firstValue.fields["timestamp"])

        Assert.assertEquals(CrashType.NORMAL_CRASH.toString(), dimensionCaptor.secondValue.fields["type"])
        Assert.assertEquals(formatter.formatDateMilliseconds(normalCrashDate.time), dimensionCaptor.secondValue.fields["timestamp"])

        val storeError = crashReporter.getCacheFileNames()
        Assert.assertEquals(0, storeError.size)
    }

    private suspend fun storeNativeCrashFile(context: Context, timeStamp: String?, jsonObject: String) = withContext(Dispatchers.IO) {
        val fileOutputStream = context.openFileOutput(JtCrashReporter.STACKTRACE_FILE_PREFIX + timeStamp, Context.MODE_PRIVATE)
        val outputStreamWriter = OutputStreamWriter(fileOutputStream)
        outputStreamWriter.use { it.write(jsonObject) }
    }

    companion object {
        private val defaultBreadCrumb = BreadCrumb("breadcrumb1", "logs", LogLevel.ERROR, Date())
        internal fun generateNativeCrashMock(
            signal: NativeSignal? = NativeSignal.SIGFPE,
            timeStamp: String?,
            formatter: Formatter,
            breadCrumbList: List<BreadCrumb>? = null,
        ): JSONObject {
            return JSONObject().apply {
                val dataJson = JSONObject()
                dataJson.put("timestamp", timeStamp)
                dataJson.put("stacktrace", "io_justtrack")
                dataJson.put("signal", signal?.id)

                put("data", dataJson)

                if (breadCrumbList == null) {
                    val breadCrumbArray = JSONArray()
                    breadCrumbArray.put(defaultBreadCrumb.toJSON(formatter).put("message", "breadcrumb1"))
                    breadCrumbArray.put(defaultBreadCrumb.toJSON(formatter).put("message", "breadcrumb2"))
                    put("breadcrumbs", breadCrumbArray)
                } else {
                    val breadCrumbArray = JSONArray()
                    breadCrumbList.forEach {
                        breadCrumbArray.put(it.toJSON(formatter))
                    }
                    put("breadcrumbs", breadCrumbArray)
                }
            }
        }
    }
}
