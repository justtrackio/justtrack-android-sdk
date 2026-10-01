package io.justtrack.crashes

import io.justtrack.Metric
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import org.json.JSONObject

internal open class ReportableIssue(
    private val crashType: CrashType?,
    private val stackTrace: List<String>,
    private val timeStamp: String?,
    private val timeStampMS: Long?,
    private val name: String,
    private val breadcrumbs: String? = null,
    private val reason: String? = null,
) {
    private val combineStackTrace = stackTrace.joinToString(", ")

    private companion object {
        private const val MAX_BREADCRUMB_CHUNK_SIZE = 4000
    }

    internal fun report(logger: Logger) {
        if (!InternalCrashChecker.isInternalCrash(combineStackTrace) && crashType != CrashType.WRAPPER_CRASH) {
            // Native crash is store directly without CrashHandler so filtering there is not enough.
            val fields = LoggerFieldsBuilder()
            fields.with("stack_trace", combineStackTrace)
            logger.info("Application crash dropped at reportCrash", fields)
        } else {
            val crashTypeName = if (crashType != null) crashType.name else CrashType.NORMAL_CRASH.toString()
            val fields = LoggerFieldsBuilder().with("type", crashTypeName)
            logger.publishMetric(
                Metric("crash"),
                1.0,
                fields,
            )

            addFields(fields, logger)

            logger.error(
                "Application shutdown due to crash",
                fields,
            )

            // Split breadcrumb to multiple logs.
            breadcrumbs?.let { breadCrumbs ->
                if (breadCrumbs != "[]") {
                    breadCrumbs.chunked(MAX_BREADCRUMB_CHUNK_SIZE).forEach {
                        logger.error(
                            "Application shutdown with breadcrumb",
                            LoggerFieldsBuilder().with("breadcrumb", it),
                        )
                    }
                }
            }
        }
    }

    internal open fun addFields(fields: LoggerFieldsBuilder, logger: Logger): LoggerFieldsBuilder {
        if (timeStamp != null) {
            fields.with("timestamp", timeStamp)
        }

        fields.with("stack_trace", combineStackTrace)

        return fields
    }

    internal open fun toJson(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("crashType", crashType?.name ?: CrashType.NORMAL_CRASH.toString())
        jsonObject.put("stackTrace", stackTrace)
        jsonObject.put("timeStamp", timeStamp)
        jsonObject.put("timeStampMS", timeStampMS)
        jsonObject.put("name", name)
        jsonObject.put("breadcrumb", breadcrumbs)
        jsonObject.put("reason", reason)

        return jsonObject
    }
    override fun toString(): String {
        return toJson().toString()
    }
}

@Suppress("MagicNumber") // crash id is identified with enum value, so the number is not magic.
internal enum class CrashType(val id: Int) {
    NORMAL_CRASH(0),
    NATIVE_CRASH(1),
    ANR(2),
    WRAPPER_CRASH(3),
    ;

    companion object {
        private val map = entries.associateBy(CrashType::id)
        fun fromId(id: Int) = map[id]
    }
}
