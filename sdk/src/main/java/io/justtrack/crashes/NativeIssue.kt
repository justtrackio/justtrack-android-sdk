package io.justtrack.crashes

import io.justtrack.Formatter
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import org.json.JSONArray
import org.json.JSONObject

internal class NativeIssue(
    dataJson: JSONObject,
    val formatter: Formatter,
) : ReportableIssue(
    crashType = CrashType.NATIVE_CRASH,
    stackTrace = when (val jsonStackTrace = dataJson.opt("stacktrace")) {
        is JSONArray -> 0.until(jsonStackTrace.length()).map { i -> jsonStackTrace.optString(i) }
        is String -> listOf(jsonStackTrace)
        else -> emptyList()
    },
    timeStamp = try {
        val temp = (dataJson.opt("timestamp") as? String)
        formatter.parseDate(temp).time
        temp
    } catch (e: Exception) {
        null
    },
    timeStampMS = try {
        (dataJson.opt("timestamp") as? String)?.let { formatter.parseDate(it).time }
    } catch (e: Exception) {
        null
    },
    name = NativeSignal.fromId(
        dataJson.optInt("signal"),
    )?.name ?: NativeSignal.UNKNOWN_SIGNAL.name,
) {
    private val signalInfo = SignalInfo(dataJson)

    override fun toJson(): JSONObject {
        val originalJson = super.toJson()
        originalJson.put("signal", signalInfo.toString())

        return originalJson
    }

    override fun addFields(fields: LoggerFieldsBuilder, logger: Logger): LoggerFieldsBuilder {
        val originalFields = super.addFields(fields, logger)
        originalFields.with("native_signal_info", signalInfo.toJson().toString())

        return originalFields
    }
}

internal data class SignalInfo(
    val signal: Int?,
    val error: Int?,
    val code: Int?,
    val pid: Int?,
    val uid: Int?,
    val status: Int?,
    val addr: Int?,
    val value: Int?,
    val band: Int?,
) {
    constructor(json: JSONObject) : this(
        signal = json.opt("signal") as? Int,
        error = json.opt("error") as? Int,
        code = json.opt("code") as? Int,
        pid = json.opt("pid") as? Int,
        uid = json.opt("uid") as? Int,
        status = json.opt("status") as? Int,
        addr = json.opt("addr") as? Int,
        value = json.opt("value") as? Int,
        band = json.opt("band") as? Int,
    )

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("signal", signal)
            put("error", error)
            put("code", code)
            put("pid", pid)
            put("uid", uid)
            put("status", status)
            put("addr", addr)
            put("value", value)
            put("band", band)
        }
    }
}

// POSIX signal numbers are inherently named by their enum entries
@Suppress("MagicNumber")
internal enum class NativeSignal(val id: Int) {
    SIGILL(4),
    SIGTRAP(5),
    SIGABRT(6),
    SIGBUS(7),
    SIGFPE(8),
    SIGSEGV(11),
    SIGPIPE(13),
    UNKNOWN_SIGNAL(-1),
    ;

    companion object {
        private val map = entries.associateBy(NativeSignal::id)
        fun fromId(id: Int) = map[id]
    }
}
