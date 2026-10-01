package io.justtrack.crashes

import io.justtrack.Formatter
import org.json.JSONArray
import org.json.JSONObject

internal class NonNativeIssue(
    dataJson: JSONObject,
    breadcrumbJson: JSONArray,
    val formatter: Formatter,
) : ReportableIssue(
    crashType = CrashType.fromId(dataJson.optInt("crashType", CrashType.NORMAL_CRASH.id)),
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
    name = dataJson.optString("name", ""),
    reason = dataJson.optString("reason", ""),
    breadcrumbs = breadcrumbJson.toString(),
)
