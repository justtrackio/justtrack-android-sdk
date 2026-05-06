package io.justtrack

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import io.justtrack.exceptions.IntegrityException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Class to encapsulate getting an [String] [Future]. Allows us to easily lock
 * this class without having to fear that we deadlock anything (we used to lock the [BaseJustTrackSdk]
 * for this - this lead to a problem when you needed to lock the SDK to process events, but another
 * thread had the SDK locked while blocking on publishing a new event as the blocking queue was full).
 */
internal class IntegrityTokenProvider internal constructor(
    private val taskExecutor: TaskExecutor,
    private val context: Context,
    private val deviceInfo: DeviceInfo,
    injectedTokenProvider: StandardIntegrityTokenProvider? = null,
    private val retryConfig: List<Int> = RetryConfig.DEFAULT_INTEGRITY_CONFIG,
) {
    private var integrityTokenFuture: AsyncFuture<IntegrityTokenData>? = null

    @VisibleForTesting
    internal var tokenProvider: StandardIntegrityTokenProvider? = injectedTokenProvider

    /**
     * Generates a new future to obtain the integrity token.
     * If the current future is still valid, it returns the existing one.
     * A token cannot be used if it was created more than 10 minutes ago.
     */
    // Deadlock-Safety: executeAsFuture is not locking anything (besides the executor maybe).
    @Synchronized
    @JvmName("getOrRenewFuture")
    internal fun getOrRenewFuture(
        logger: HttpLogger,
        installInstanceIdFuture: AsyncFuture<String>,
        integritySecretFuture: AsyncFuture<String>,
        databaseInterface: DatabaseInterface,
        getterTask: IntegrityTokenGetterTask? = null,
    ): AsyncFuture<IntegrityTokenData> {
        var currentFuture = integrityTokenFuture

        if (currentFuture == null || (currentFuture.isDone && isTokenOutDated(currentFuture))) {
            val integrityTokenGetterTask = getterTask ?: IntegrityTokenGetterTask(
                context,
                logger,
                integritySecretFuture,
                installInstanceIdFuture,
                databaseInterface,
                this,
            )

            currentFuture = taskExecutor.executeAsFuture(
                IntegrityRetryGetterTask(
                    FixedRetryingTask(
                        integrityTokenGetterTask,
                        deviceInfo,
                        logger,
                        TrackingEventErrorClassifier.instance,
                        null,
                        retryConfig,
                    ),
                    logger,
                ),
            )
            integrityTokenFuture = currentFuture
        }

        return currentFuture
    }

    private fun isTokenOutDated(tokenFuture: AsyncFuture<IntegrityTokenData>): Boolean {
        try {
            // read the token if it already completed. We should guard this call with a check
            // to tokenFuture.isDone to avoid blocking the current thread
            val token = tokenFuture.get(0L, TimeUnit.MILLISECONDS)
            val currentTime = System.currentTimeMillis()
            return (currentTime - token.generatedTimestamp) >= VALID_TOKEN_DURATION
        } catch (exception: ExecutionException) {
            return true
        } catch (exception: TimeoutException) {
            return true
        } catch (exception: InterruptedException) {
            return true
        }
    }

    internal class IntegrityRetryGetterTask(
        private val retryingTask: Task<IntegrityTokenData>,
        private val logger: HttpLogger,
    ) : Task<IntegrityTokenData> {
        override suspend fun execute(): IntegrityTokenData {
            return try {
                retryingTask.execute()
            } catch (exception: IntegrityException) {
                // If it exceed retry amount and if the error is StandardIntegrityException, provide the errorCode back.
                IntegrityTokenData(integrityException = exception)
            } catch (exception: Exception) {
                logger.warn("Integrity provider throwing unknown exception", exception)
                IntegrityTokenData(
                    integrityException = IntegrityException(
                        errorCode = UNKNOWN_ERROR_CODE,
                        isRetryAbleErrorCode = true,
                        exception.message,
                        exception.cause,
                    ),
                )
            }
        }
    }

    internal open class IntegrityTokenGetterTask(
        private val context: Context,
        private val logger: HttpLogger,
        private val integritySecretFuture: AsyncFuture<String>,
        private val installInstanceIdFuture: AsyncFuture<String>,
        private val databaseInterface: DatabaseInterface,
        private val integrityTokenProvider: IntegrityTokenProvider,
    ) : Task<IntegrityTokenData> {
        @Throws(NoSuchAlgorithmException::class, RuntimeException::class)
        override suspend fun execute(): IntegrityTokenData {
            try {
                var isPreviouslySent: Boolean
                val installInstanceId = installInstanceIdFuture.await()
                databaseInterface.openAttribution().use {
                    isPreviouslySent = it.isIntegrityTokenSent()
                }

                if (isPreviouslySent) {
                    return IntegrityTokenData(previouslySent = true)
                }

                val tokenProvider: StandardIntegrityTokenProvider = getStandardIntegrityTokenProvider(
                    sGoogleIntegrityProvider,
                    integrityTokenProvider.tokenProvider,
                    context,
                )

                return getToken(tokenProvider, generateRequestHash(integritySecretFuture.await(), installInstanceId))
            } catch (exception: Exception) {
                logger.warn("Unable to get integrity token", exception)
                if (exception is IntegrityException) {
                    if (exception.isRetryAbleErrorCode) {
                        throw exception
                    } else {
                        // If the error is not retry-able, provide the errorCode back.
                        return IntegrityTokenData(integrityException = exception)
                    }
                } else {
                    throw IntegrityException(errorCode = UNKNOWN_ERROR_CODE, isRetryAbleErrorCode = true, exception.message, exception.cause)
                }
            }
        }

        @VisibleForTesting
        internal suspend fun getTokenProvider(context: Context): StandardIntegrityTokenProvider {
            return suspendCoroutine { continuation ->
                val continued = AtomicBoolean()
                val standardIntegrityManager = IntegrityManagerFactory.createStandard(context)
                standardIntegrityManager.prepareIntegrityToken(
                    StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                        .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                        .build(),
                )
                    .addOnSuccessListener { tokenProvider: StandardIntegrityTokenProvider ->
                        if (!continued.getAndSet(true)) {
                            continuation.resume(tokenProvider)
                        }
                    }
                    .addOnFailureListener { exception: Exception ->
                        val integrityException = if (exception is StandardIntegrityException) {
                            IntegrityException(exception.errorCode, isErrorCodeRetryAble(exception.errorCode), exception.message, exception.cause)
                        } else {
                            IntegrityException(errorCode = UNKNOWN_ERROR_CODE, isRetryAbleErrorCode = true, exception.message, exception.cause)
                        }

                        if (!continued.getAndSet(true)) {
                            continuation.resumeWithException(integrityException)
                        }
                    }
            }
        }

        private suspend fun getToken(tokenProvider: StandardIntegrityTokenProvider, requestHash: String): IntegrityTokenData {
            return suspendCoroutine { continuation ->
                val continued = AtomicBoolean()
                tokenProvider.request(
                    StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                        .setRequestHash(requestHash)
                        .build(),
                ).addOnSuccessListener { standardIntegrityToken: StandardIntegrityToken ->
                    if (!continued.getAndSet(true)) {
                        val token = standardIntegrityToken.token()
                        if (!token.isNullOrEmpty()) {
                            continuation.resume(IntegrityTokenData(token = standardIntegrityToken.token()))
                        } else {
                            val errorMessage = if (token == null) {
                                "Received token with null token value"
                            } else {
                                "Received token with empty token value"
                            }
                            continuation.resume(
                                IntegrityTokenData(
                                    token = token,
                                    integrityException = IntegrityException(
                                        errorCode = UNKNOWN_ERROR_CODE,
                                        false,
                                        errorMessage,
                                        Throwable(errorMessage),
                                    ),
                                ),
                            )
                        }
                    }
                }
                    .addOnFailureListener { exception: Exception ->
                        val integrityException = if (exception is StandardIntegrityException) {
                            IntegrityException(exception.errorCode, isErrorCodeRetryAble(exception.errorCode), exception.message, exception.cause)
                        } else {
                            IntegrityException(errorCode = UNKNOWN_ERROR_CODE, isRetryAbleErrorCode = true, exception.message, exception.cause)
                        }

                        if (!continued.getAndSet(true)) {
                            continuation.resumeWithException(integrityException)
                        }
                    }
            }
        }

        // There are two ways of providing standardIntegrityTokenProvider for testing.
        // If possible try to inject the provider in the constructor.
        private suspend fun getStandardIntegrityTokenProvider(
            staticIntegrityTokenProvider: StandardIntegrityTokenProvider?,
            injectedIntegrityTokenProvider: StandardIntegrityTokenProvider?,
            context: Context,
        ): StandardIntegrityTokenProvider {
            return if (injectedIntegrityTokenProvider != null) {
                injectedIntegrityTokenProvider
            } else if (staticIntegrityTokenProvider != null) {
                staticIntegrityTokenProvider
            } else {
                val provider = getTokenProvider(context)
                integrityTokenProvider.tokenProvider = provider
                provider
            }
        }

        private fun generateRequestHash(securitySecret: String, installInstanceId: String): String {
            return "$installInstanceId:$securitySecret"
        }

        private fun isErrorCodeRetryAble(errorCode: Int): Boolean {
            return retryAbleErrorCode.contains(errorCode)
        }
    }

    internal companion object {
        internal const val CLOUD_PROJECT_NUMBER = 782357577593L
        internal val retryAbleErrorCode = listOf(-3, -8, -12, -18, -100, -102, -19)
        internal const val UNKNOWN_ERROR_CODE = 1000

        @VisibleForTesting
        internal var sGoogleIntegrityProvider: StandardIntegrityTokenProvider? = null

        // Half of token lifespan
        internal const val VALID_TOKEN_DURATION = 5 * 60 * 1000
    }
}
