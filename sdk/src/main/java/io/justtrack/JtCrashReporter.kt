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
import io.justtrack.util.FileAccessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

internal open class JtCrashReporter internal constructor(
    context: Context,
    private val fileAccessor: FileAccessor,
    private val logger: Logger,
    private val formatter: Formatter = Formatter,
    private val isTracking: AtomicBoolean,
) : BreadCrumbReporter, CrashReporter {

    private val breadCrumbRingBuffer = RingBuffer<BreadCrumb>(MAX_BREADCRUMBS)

    init {
        context.getSharePrefWithIO(NAME, Context.MODE_PRIVATE) {
            remove(KEY_UNCAUGHT_EXCEPTION_LIST)
        }
    }

    override fun captureException(throwable: Throwable) {
        val crashType = if (throwable is ANRException) {
            CrashType.ANR
        } else {
            CrashType.NORMAL_CRASH
        }
        storeUncaughtException(crashType.id, throwable.message, throwable.stackTrace, date = Date())
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
        val fileList: List<String> = fileAccessor.listFileNames()
            .filter { it.startsWith(STACKTRACE_FILE_PREFIX) || it.startsWith(CRASH_FILE_PREFIX) }

        for (crashFile in fileList) {
            val crashData = loadFromCache(crashFile)

            crashData?.report(logger)

            fileAccessor.deleteFile(crashFile)
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

            writeCrashIntoFile(crashType, message, stacktrace, timeStamp, getBreadCrumbs())
        } catch (e: Exception) {
            logger.warn("Unable to store UncaughtException", e)
        }
    }

    private fun writeCrashIntoFile(crashType: Int, message: String?, stacktrace: String?, timestamp: String, breadCrumbs: List<BreadCrumb>) {
        val fileName = "$CRASH_FILE_PREFIX$timestamp.json"
        val file = fileAccessor.getFile(fileName)

        if (!file.exists()) {
            fileAccessor.createNewFile(file)
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

        fileAccessor.editFile(file, root.toString())
    }

    @VisibleForTesting
    internal fun getCacheFileNames(): List<String> {
        return fileAccessor.listFileNames()
            .filter { it.startsWith(STACKTRACE_FILE_PREFIX) || it.startsWith(CRASH_FILE_PREFIX) }
    }

    private fun loadFromCache(fileName: String): ReportableIssue? {
        val fileContent = fileAccessor.loadFile(fileName)

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
            fileAccessor.deleteFile(fileName)
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
