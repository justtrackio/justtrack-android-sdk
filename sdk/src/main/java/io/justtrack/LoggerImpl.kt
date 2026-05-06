package io.justtrack

import android.util.Log
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder

/**
 * A default logger for logging to Logcat.
 */
internal open class LoggerImpl internal constructor(internal val isLogEnabled: Boolean = false) : Logger {

    override fun debug(message: String, vararg fields: LoggerFields) {
        if (isLogEnabled) {
            Log.d(TAG, encodeMessage(message, *fields))
        }
    }

    override fun info(message: String, vararg fields: LoggerFields) {
        if (isLogEnabled) {
            Log.i(TAG, encodeMessage(message, *fields))
        }
    }

    override fun warn(message: String, vararg fields: LoggerFields) {
        if (isLogEnabled) {
            Log.w(TAG, encodeMessage(message, *fields))
        }
    }

    override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) {
        if (isLogEnabled) {
            Log.w(TAG, encodeMessage(message, *fields), exception)
        }
    }

    override fun error(message: String, vararg fields: LoggerFields) {
        Log.e(TAG, encodeMessage(message, *fields))
    }

    override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) {
        Log.e(TAG, encodeMessage(message, *fields), exception)
    }

    @Suppress("SpreadOperator")
    override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) {
        val metricFields: LoggerFields = LoggerFieldsBuilder()
            .with("metricName", metric.metric)
            .with("metricValue", value)
            .with("metricUnit", metric.unit.unit)
        val allFields = listOf(metricFields) + dimensions

        info("Writing metric to console", *allFields.toTypedArray())
    }

    override val fallback: Logger
        get() = this

    protected fun encodeMessage(message: String, vararg fields: LoggerFields?): String {
        if (fields.isEmpty()) {
            return message
        }

        val sb = StringBuilder()
        sb.append(message)

        for (loggerFields in fields) {
            if (loggerFields != null) {
                for ((key, value) in loggerFields.fields) {
                    sb.append(", ").append(key).append(" = ").append(value)
                }
            }
        }

        return sb.toString()
    }

    companion object {
        const val TAG: String = "JustTrackSdk"
    }
}
