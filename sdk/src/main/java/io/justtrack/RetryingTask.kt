package io.justtrack

import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder
import kotlinx.coroutines.delay
import kotlin.math.min

internal class RetryingTask<T>(
    private val task: Task<T>,
    private val deviceInfo: DeviceInfo,
    private val logger: Logger,
    private val retries: Int,
    private val errorClassifier: ErrorClassifier,
    private val requestName: String?,
) : Task<T> {
    override suspend fun execute(): T = executeWithRetries(retries, INITIAL_BACKOFF)

    private suspend fun executeWithRetries(remainingRetries: Int, backoff: Double): T {
        val connectionType = deviceInfo.getConnectionType()

        try {
            return task.execute()
        } catch (exception: Throwable) {
            if (remainingRetries <= 0 || errorClassifier.unrecoverable(exception)) {
                throw exception
            }

            delay(backoff.toLong())

            if (requestName != null) {
                val dimensions: LoggerFields = LoggerFieldsBuilder()
                    .with("Request", requestName)
                    .with("Network", connectionType.toString())
                logger.publishMetric(REQUEST_RETRIES_METRIC, 1.0, dimensions)
            }

            logger.debug(
                "Ignoring error and trying again",
                LoggerFieldsBuilder()
                    .with("exception", exception)
                    .with("task", task.javaClass.name),
            )

            var newBackoff = min(backoff * 2.0 * (RANDOM_JITTER_MIN + Math.random()), MAXIMAL_BACKOFF)
            val backoffSuggestion = errorClassifier.waitTime(exception)
            if (backoffSuggestion > 0) {
                newBackoff = backoffSuggestion
            }

            return executeWithRetries(remainingRetries - 1, newBackoff)
        }
    }

    companion object {
        val REQUEST_RETRIES_METRIC = Metric("RequestRetries")

        // amount of time to wait between requests, in milliseconds
        private const val INITIAL_BACKOFF = 500.0

        // maximum amount of time to wait between requests, in milliseconds
        private const val MAXIMAL_BACKOFF = 15000.0

        // minimum random jitter factor for exponential backoff (range: RANDOM_JITTER_MIN to RANDOM_JITTER_MIN + 1.0)
        private const val RANDOM_JITTER_MIN = 0.5
    }
}
