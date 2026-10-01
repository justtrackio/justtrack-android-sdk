package io.justtrack

import androidx.annotation.VisibleForTesting
import io.justtrack.FetchClaimErrorClassifier.Companion.instance
import io.justtrack.FetchClaimErrorClassifier.Companion.isUnreachableException
import io.justtrack.api.AttributionApi
import io.justtrack.executor.TaskExecutor
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.providers.AdvertiserIdProvider
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class ClaimProviderImpl(
    private val deviceInfo: DeviceInfo,
    private val advertiserIdProvider: AdvertiserIdProvider,
    private val attributionApi: AttributionApi,
    private val retryConfig: RetryConfig,
    private val taskExecutor: TaskExecutor,
    private val logger: Logger,
) : ClaimProvider {

    private var ipv4Claim: AsyncFuture<String>? = null
    private var ipv4ClaimExpiresAt: Long = 0
    private var ipv6Claim: AsyncFuture<String>? = null
    private var ipv6ClaimExpiresAt: Long = 0

    // Deadlock-Safety: This only updates some fields and spawns new tasks which don't take any additional locks
    @Synchronized
    override fun refreshClaims() {
        val now = System.currentTimeMillis()
        if (ipv4ClaimExpiresAt < now) {
            ipv4Claim = null
        }
        if (ipv6ClaimExpiresAt < now) {
            ipv6Claim = null
        }

        if (ipv4Claim == null) {
            ipv4Claim = spawnFetchClaimTask(IPProtocol.IPv4)
            ipv4ClaimExpiresAt = now + CLAIM_EXPIRE_DURATION_MS
        }
        if (ipv6Claim == null) {
            ipv6Claim = spawnFetchClaimTask(IPProtocol.IPv6)
            ipv6ClaimExpiresAt = now + CLAIM_EXPIRE_DURATION_MS
        }
    }

    override fun provideClaims(timeout: Long): ProvidedClaims {
        val result = ProvidedClaims()

        val tookIPv4 = provideClaim(result, ipv4Claim, "IPv4", timeout)
        provideClaim(result, ipv6Claim, "IPv6", timeout - tookIPv4)

        return result
    }

    @VisibleForTesting
    internal fun provideClaim(result: ProvidedClaims, claim: AsyncFuture<String>?, type: String, timeout: Long): Long {
        val start = System.currentTimeMillis()
        if (claim != null) {
            try {
                val claimValue = claim[timeout, TimeUnit.MILLISECONDS]
                if (claimValue != null) {
                    result.addClaim(claimValue)
                }
            } catch (exception: TimeoutException) {
                result.setTimedOut()
                logger.warn("Getting $type claim timed out", LoggerFieldsBuilder().with("exception", exception))
            } catch (exception: CancellationException) {
                result.setTimedOut()
                logger.warn("Getting $type claim timed out", LoggerFieldsBuilder().with("exception", exception))
            } catch (exception: InterruptedException) {
                result.setTimedOut()
                logger.warn("Getting $type claim timed out", LoggerFieldsBuilder().with("exception", exception))
            } catch (exception: Exception) {
                if (isUnreachableException(exception)) {
                    logger.debug(
                        "Getting $type claim failed, protocol is not supported",
                        LoggerFieldsBuilder().with("exception", exception),
                    )
                } else {
                    logger.warn("Getting $type claim failed", exception)
                }
            }
        }

        return System.currentTimeMillis() - start
    }

    // Deadlock-Safety: This must not take any new locks, it is called with a lock already taken
    @VisibleForTesting
    internal fun spawnFetchClaimTask(protocol: IPProtocol): AsyncFuture<String> {
        val task = FetchIpClaimTask(
            deviceInfo,
            advertiserIdProvider,
            this.attributionApi,
            logger,
            protocol,
        )
        val retryingTask: Task<String> = RetryingTask(
            task,
            deviceInfo,
            logger,
            retryConfig.fetchClaimRetries,
            instance,
            protocol.requestName,
        )
        return taskExecutor.executeFuture(retryingTask)
    }

    internal companion object {
        private const val CLAIM_EXPIRE_DURATION_MS: Long = (10 * 60 * 1000).toLong()
        internal const val CLAIM_TIMEOUT_FAST_MS: Long = 750
        internal const val CLAIM_TIMEOUT_SLOW_MS: Long = 60000
    }
}
