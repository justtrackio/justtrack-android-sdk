package io.justtrack.log

import io.justtrack.Metric

/**
 * A logger which allows a user of this library to tap into the log stream produced by this library.
 */
interface Logger {
    /**
     * Log a debug message.
     *
     * @param message A message.
     * @param fields  Optional fields to attach to the log message.
     */
    fun debug(message: String, vararg fields: LoggerFields)

    /**
     * Log an info message.
     *
     * @param message A message.
     * @param fields  Optional fields to attach to the log message.
     */
    fun info(message: String, vararg fields: LoggerFields)

    /**
     * Log a warning message.
     *
     * @param message A message.
     * @param fields  Optional fields to attach to the log message.
     */
    fun warn(message: String, vararg fields: LoggerFields)

    /**
     * Log a warning message with an exception.
     *
     * @param message A message.
     * @param fields  Optional fields to attach to the log message.
     */
    fun warn(message: String, exception: Throwable, vararg fields: LoggerFields)

    /**
     * Log an error message.
     *
     * @param message A message.
     * @param fields  Optional fields to attach to the log message.
     */
    fun error(message: String, vararg fields: LoggerFields)

    /**
     * Log an error message with an exception.
     *
     * @param message   A message describing when the exception happened.
     * @param exception The exception.
     * @param fields    Optional fields to attach to the log message.
     */
    fun error(message: String, exception: Throwable, vararg fields: LoggerFields)

    /**
     * Publish a metric to the backend.
     *
     * @param metric     The metric to publish.
     * @param value      The value of that metric.
     * @param dimensions Additional dimensions to specify for that metric.
     */
    fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields)

    /**
     * A logger that only writes the log on the console.
     *
     * @return The fallback logger.
     */
    val fallback: Logger
}
