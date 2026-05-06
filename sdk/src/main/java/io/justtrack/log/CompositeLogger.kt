package io.justtrack.log

import io.justtrack.Metric

internal class CompositeLogger(
    private val defaultLogger: Logger?,
    private val customLogger: Logger?,
) : Logger {
    override fun debug(message: String, vararg fields: LoggerFields) {
        defaultLogger?.debug(message, *fields)
        customLogger?.debug(message, *fields)
    }

    override fun info(message: String, vararg fields: LoggerFields) {
        defaultLogger?.info(message, *fields)
        customLogger?.info(message, *fields)
    }

    override fun warn(message: String, vararg fields: LoggerFields) {
        defaultLogger?.warn(message, *fields)
        customLogger?.warn(message, *fields)
    }

    override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) {
        defaultLogger?.warn(message, exception, *fields)
        customLogger?.warn(message, exception, *fields)
    }

    override fun error(message: String, vararg fields: LoggerFields) {
        defaultLogger?.error(message, *fields)
        customLogger?.error(message, *fields)
    }

    override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) {
        defaultLogger?.error(message, exception, *fields)
        customLogger?.error(message, exception, *fields)
    }

    override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) {
        defaultLogger?.publishMetric(metric, value, *dimensions)
        customLogger?.publishMetric(metric, value, *dimensions)
    }

    override val fallback: Logger
        get() = this
}
