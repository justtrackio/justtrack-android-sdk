package io.justtrack

import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder
import kotlinx.coroutines.delay

internal class FixedRetryingTask<T>(
    private val task: Task<T>,
    private val deviceInfo: DeviceInfo,
    private val logger: Logger,
    private val errorClassifier: ErrorClassifier,
    private val requestName: String?,
    private val retryDelaySeconds: List<Int> = listOf(),
) : Task<T> {
    override suspend fun execute(): T = executeWithRetries(0)

    private suspend fun executeWithRetries(nextRetryIndex: Int): T {
        val connectionType = deviceInfo.getConnectionType()

        try {
            return task.execute()
        } catch (exception: Throwable) {
            if (nextRetryIndex >= retryDelaySeconds.size || errorClassifier.unrecoverable(exception)) {
                throw exception
            }

            delay(retryDelaySeconds[nextRetryIndex] * MILLIS_PER_SECOND)

            if (requestName != null) {
                val dimensions: LoggerFields =
                    LoggerFieldsBuilder()
                        .with("Request", requestName)
                        .with("Network", connectionType.toString())
                logger.publishMetric(RetryingTask.REQUEST_RETRIES_METRIC, 1.0, dimensions)
            }

            logger.debug(
                "Ignoring error and trying again",
                LoggerFieldsBuilder()
                    .with("exception", exception)
                    .with("task", task.javaClass.name),
            )

            return executeWithRetries(nextRetryIndex + 1)
        }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
        val DEFAULT_RETRY_DELAYS = listOf(10, 20, 30)
    }
}
