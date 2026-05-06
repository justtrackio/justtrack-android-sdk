package io.justtrack

import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields

internal class TestLogger : Logger {
    override val fallback: Logger
        get() = this

    override fun debug(message: String, vararg fields: LoggerFields) = Unit

    override fun info(message: String, vararg fields: LoggerFields) = Unit

    override fun warn(message: String, vararg fields: LoggerFields) = Unit

    override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit

    override fun error(message: String, vararg fields: LoggerFields) = Unit

    override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit

    override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) = Unit
}
