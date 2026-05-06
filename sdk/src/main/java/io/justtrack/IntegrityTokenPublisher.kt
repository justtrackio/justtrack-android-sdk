package io.justtrack

import androidx.annotation.VisibleForTesting

internal class IntegrityTokenPublisher(
    private val taskExecutor: TaskExecutor,
    private val deviceInfo: DeviceInfo,
    private val retryConfig: List<Int>,
) {
    private var publishingFuture: AsyncFuture<Boolean>? = null

    // Deadlock-Safety: we only read a variable
    @Synchronized
    @JvmName("getCurrentFuture")
    internal fun getCurrentFuture(): AsyncFuture<Boolean>? {
        return publishingFuture
    }

    // Deadlock-Safety: executeAsFuture is not locking anything (besides the executor maybe).
    @Synchronized
    @JvmName("publishIntegrityTokenIfNotAlreadyRunning")
    internal fun publishIntegrityTokenIfNotAlreadyRunning(
        logger: HttpLogger,
        networkErrorLogger: NetworkErrorLogger,
        httpClient: HttpClient,
        databaseInterface: DatabaseInterface,
        integrityTokenProvideMethod: () -> AsyncFuture<IntegrityTokenData>,
        installInstanceIdFuture: AsyncFuture<String>,
    ): AsyncFuture<Boolean> {
        val currentTask = publishingFuture

        if (currentTask == null || currentTask.isDone) {
            val newIntegrityTokenFuture = publishIntegrityToken(
                logger,
                networkErrorLogger,
                httpClient,
                databaseInterface,
                integrityTokenProvideMethod,
                installInstanceIdFuture,
            )
            publishingFuture = newIntegrityTokenFuture
            return newIntegrityTokenFuture
        }

        return currentTask
    }

    @VisibleForTesting
    @JvmName("publishIntegrityToken")
    internal fun publishIntegrityToken(
        logger: HttpLogger,
        networkErrorLogger: NetworkErrorLogger,
        httpClient: HttpClient,
        databaseInterface: DatabaseInterface,
        integrityTokenProvideMethod: () -> AsyncFuture<IntegrityTokenData>,
        installInstanceIdFuture: AsyncFuture<String>,
    ): AsyncFuture<Boolean> {
        return taskExecutor.executeAsFuture(
            PublishIntegrityRetryTask(
                FixedRetryingTask(
                    PublishIntegrityTokenTask(
                        logger,
                        httpClient,
                        databaseInterface,
                        integrityTokenProvideMethod,
                        installInstanceIdFuture,
                    ),
                    deviceInfo,
                    logger,
                    TrackingEventErrorClassifier.instance,
                    null,
                    retryConfig,
                ),
                logger,
                networkErrorLogger,
            ),
        )
    }

    internal class PublishIntegrityRetryTask(
        private val retryingTask: Task<Boolean>,
        private val logger: HttpLogger,
        private val networkErrorLogger: NetworkErrorLogger,
    ) : Task<Boolean> {
        override suspend fun execute(): Boolean {
            return try {
                retryingTask.execute()
            } catch (exception: Exception) {
                networkErrorLogger.logException(
                    logger,
                    exception,
                    "Failed to publish integrity token",
                )
                return false
            }
        }
    }

    internal open class PublishIntegrityTokenTask(
        private val logger: HttpLogger,
        private val httpClient: HttpClient,
        private val databaseInterface: DatabaseInterface,
        private val integrityTokenProvideMethod: () -> AsyncFuture<IntegrityTokenData>,
        private val installInstanceIdFuture: AsyncFuture<String>,
    ) : Task<Boolean> {
        override suspend fun execute(): Boolean {
            val integrityTokenData = integrityTokenProvideMethod.invoke().await()

            if (integrityTokenData.previouslySent) {
                // Ensure we only send the same token once
                return true
            }

            val installInstanceId = installInstanceIdFuture
                .await()

            val body: JSONEncodable = DTOIntegrityToken(
                integrityTokenData.token,
                installInstanceId,
                integrityTokenData.integrityException?.errorCode,
                integrityTokenData.integrityException?.errorMessage,
            )

            logger.debug(if (integrityTokenData.token != null) "Publishing integrity token" else "Publishing integrity token error")

            val result = httpClient.reportIntegrity(
                logger,
                body,
                installInstanceId,
            )

            if (result.isSuccess) {
                try {
                    if (integrityTokenData.integrityException == null ||
                        !integrityTokenData.integrityException.isRetryAbleErrorCode
                    ) {
                        // Only success result or Non-retry-able will be marked as sent and wont retry again.
                        databaseInterface.openAttribution().use {
                            it.setIntegrityTokenSent(true)
                        }
                    }

                    logger.debug(if (integrityTokenData.token != null) "Published integrity token" else "Published integrity token error")

                    return true
                } catch (e: AttributionTask.ParseAttributionException) {
                    throw IllegalStateException("Parsing server response failed", e)
                }
            } else {
                throw result.exceptionOrNull() ?: IllegalStateException("IntegrityTokenPublisher failed with unknown exception")
            }
        }
    }
}
