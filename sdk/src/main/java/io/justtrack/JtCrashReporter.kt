package io.justtrack

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.VisibleForTesting
import io.justtrack.crashes.CrashReporter
import io.justtrack.crashes.CrashType
import io.justtrack.crashes.NativeIssue
import io.justtrack.crashes.NonNativeIssue
import io.justtrack.crashes.ReportableIssue
import io.justtrack.exceptions.ANRException
import io.justtrack.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

internal open class JtCrashReporter internal constructor(
    val context: Context,
    val logger: Logger,
    val formatter: Formatter = Formatter,
    val isTracking: AtomicBoolean,
) : BreadCrumbReporter, CrashReporter {

    private val breadCrumbRingBuffer = RingBuffer<BreadCrumb>(MAX_BREADCRUMBS)

    override fun captureException(throwable: Throwable) {
        val crashType = if (throwable is ANRException) {
            CrashType.ANR
        } else {
            CrashType.NORMAL_CRASH
        }
        storeUncaughtException(crashType.id, throwable.message, throwable.stackTrace, date = Date())
    }

    init {
        context.getSharePrefWithIO(NAME, Context.MODE_PRIVATE) {
            removeIO(KEY_UNCAUGHT_EXCEPTION_LIST)
        }
    }

    override fun close() {
        /* no-op */
    }

    override fun addBreadCrumb(breadCrumb: BreadCrumb) {
        if (isTracking.get()) {
            breadCrumbRingBuffer.add(breadCrumb)
        }
    }

    @SuppressLint("InlinedApi")
    @JvmName("report")
    internal fun report() = CoroutineScope(Dispatchers.IO).launch {
        val fileList: List<String> = getCacheFileNames(context)

        for (crashFile in fileList) {
            val crashData = loadFromCache(context, crashFile)

            crashData?.report(logger)

            context.deleteFile(crashFile)
        }
    }

    private fun getBreadCrumbs(): List<BreadCrumb> = breadCrumbRingBuffer.getAllElements()

    @VisibleForTesting
    internal fun storeUncaughtException(
        crashType: Int = CrashType.NORMAL_CRASH.id,
        message: String?,
        stacktrace: Array<StackTraceElement>? = null,
        date: Date,
    ) {
        storeUncaughtException(crashType, message, stacktrace?.joinToString(separator = "\n") { it.toString() } ?: "", date)
    }

    @JvmName("storeUncaughtException")
    internal fun storeUncaughtException(crashType: Int = CrashType.NORMAL_CRASH.id, message: String?, stacktrace: String? = null, date: Date) {
        try {
            val timeStamp = Formatter.formatDateMilliseconds(date)

            writeCrashIntoFile(context, crashType, message, stacktrace, timeStamp, getBreadCrumbs())
        } catch (e: Exception) {
            logger.warn("Unable to store UncaughtException", e)
        }
    }

    private fun writeCrashIntoFile(
        context: Context,
        crashType: Int,
        message: String?,
        stacktrace: String?,
        timestamp: String,
        breadCrumbs: List<BreadCrumb>,
    ) {
        val fileName = "$CRASH_FILE_PREFIX$timestamp.json"
        val file = context.filesDir.resolve(fileName)

        if (!file.exists()) {
            file.createNewFile()
        }

        val data = JSONObject().apply {
            put("timestamp", timestamp)
            put("crashType", crashType)
            put("stacktrace", stacktrace)
            put("name", message)
        }

        val breadcrumbs = JSONArray(breadCrumbs.map { it.toJSON(formatter) })

        val root = JSONObject().apply {
            put("data", data)
            put("breadcrumbs", breadcrumbs)
        }

        file.writeText(root.toString())
    }

    @VisibleForTesting
    internal fun getCacheFileNames(context: Context): List<String> {
        return context.filesDir.list { _, name ->
            name.startsWith(STACKTRACE_FILE_PREFIX) || name.startsWith(CRASH_FILE_PREFIX)
        }?.toList() ?: emptyList()
    }

    private fun loadFromCache(context: Context, fileName: String): ReportableIssue? {
        val fileInputStream = context.openFileInput(fileName)
        val inputStreamReader = InputStreamReader(fileInputStream)
        val bufferedReader = BufferedReader(inputStreamReader)

        val stringBuilder = StringBuilder()

        bufferedReader.useLines { lines ->
            lines.forEach { line ->
                stringBuilder.append(line).append("\n")
            }
        }

        val fileContent = stringBuilder.toString()
        fileInputStream.close()

        val crashData = try {
            val fileContentJson = JSONObject(fileContent)
            val dataJson = fileContentJson.getJSONObject("data")
            // By default crash type should be native crash, since previous crash stored in a file is only native crash.
            val crashType = dataJson.optInt("crashType", CrashType.NATIVE_CRASH.id)

            if (crashType == CrashType.NATIVE_CRASH.id) {
                NativeIssue(dataJson, formatter)
            } else {
                val breadcrumbJson = fileContentJson.getJSONArray("breadcrumbs")
                NonNativeIssue(dataJson, breadcrumbJson, formatter)
            }
        } catch (exception: Exception) {
            logger.warn("Unable to read stored crash file", exception)
            null
        }

        // delete file if it is unreadable
        if (crashData == null) {
            context.deleteFile(fileName)
        }

        return crashData
    }

    internal companion object {
        private const val MAX_BREADCRUMBS = 20

        @VisibleForTesting
        internal const val STACKTRACE_FILE_PREFIX = "io_justtrack_native_stacktrace_"

        @VisibleForTesting
        internal const val CRASH_FILE_PREFIX = "io_justtrack_store_crash_"

        @VisibleForTesting
        const val ANR_TIMEOUT = 2000L

        // This is no longer in use, only for clearing out previous cache crash
        private const val NAME = "justtrack-crash-report"
        private const val KEY_UNCAUGHT_EXCEPTION_LIST = "KEY_UNCAUGHT_EXCEPTION_LIST"
    }
}
