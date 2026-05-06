package io.justtrack.testapp

import android.util.Log
import io.justtrack.Metric
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields

class CustomLogger : Logger {
    override fun debug(
        message: String,
        vararg fields: LoggerFields,
    ) {
        Log.d(TAG, "debug: $message")
    }

    override fun info(
        message: String,
        vararg fields: LoggerFields,
    ) {
        Log.i(TAG, "info: $message")
    }

    override fun warn(
        message: String,
        vararg fields: LoggerFields,
    ) {
        Log.w(TAG, "warn: $message")
    }

    override fun warn(
        message: String,
        exception: Throwable,
        vararg fields: LoggerFields,
    ) {
        Log.w(TAG, "warn: $message ${exception.message}")
    }

    override fun error(
        message: String,
        vararg fields: LoggerFields,
    ) {
        Log.e(TAG, "error: $message")
    }

    override fun error(
        message: String,
        exception: Throwable,
        vararg fields: LoggerFields,
    ) {
        Log.e(TAG, "error: $message ${exception.message}")
    }

    override fun publishMetric(
        metric: Metric,
        value: Double,
        vararg dimensions: LoggerFields,
    ) {
        Log.e(TAG, "publishMetric: ")
    }

    override val fallback: Logger
        get() = this

    private companion object {
        private const val TAG = "CUSTOM_LOG"
    }
}
