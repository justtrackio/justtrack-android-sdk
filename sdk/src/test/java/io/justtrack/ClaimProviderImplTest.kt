package io.justtrack

import io.justtrack.api.AttributionApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.executor.TaskExecutor
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.providers.AdvertiserIdProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class ClaimProviderImplTest {
    @Test
    fun refreshClaimsSpawnsIpv4AndIpv6TasksOnceWhileClaimsAreFresh() {
        val executor = ClaimTaskExecutor(ValueFuture("claim"))
        val provider = createProvider(executor)

        provider.refreshClaims()
        provider.refreshClaims()

        assertEquals(2, executor.executeFutureCount)
    }

    @Test
    fun provideClaimsReturnsAvailableClaims() {
        val provider = createProvider(ClaimTaskExecutor(ValueFuture("claim")))
        provider.refreshClaims()

        val claims = provider.provideClaims(100L).toList()

        assertEquals(listOf("claim", "claim"), claims)
    }

    @Test
    fun provideClaimsMarksTimedOutOnTimeoutException() {
        val logger = RecordingLogger()
        val provider = createProvider(ClaimTaskExecutor(ThrowingFuture(TimeoutException("timeout"))), logger)
        provider.refreshClaims()

        val claims = provider.provideClaims(100L)

        assertTrue(claims.isTimedOut())
        assertEquals(listOf("Getting IPv4 claim timed out", "Getting IPv6 claim timed out"), logger.warnMessages)
    }

    @Test
    fun provideClaimsMarksTimedOutOnCancellationException() {
        val logger = RecordingLogger()
        val provider = createProvider(ClaimTaskExecutor(ThrowingFuture(CancellationException("cancelled"))), logger)
        provider.refreshClaims()

        val claims = provider.provideClaims(100L)

        assertTrue(claims.isTimedOut())
        assertEquals(listOf("Getting IPv4 claim timed out", "Getting IPv6 claim timed out"), logger.warnMessages)
    }

    @Test
    fun provideClaimsMarksTimedOutOnInterruptedException() {
        val logger = RecordingLogger()
        val provider = createProvider(ClaimTaskExecutor(ThrowingFuture(InterruptedException("interrupted"))), logger)
        provider.refreshClaims()

        val claims = provider.provideClaims(100L)

        assertTrue(claims.isTimedOut())
        assertEquals(listOf("Getting IPv4 claim timed out", "Getting IPv6 claim timed out"), logger.warnMessages)
    }

    @Test
    fun provideClaimsLogsUnsupportedProtocolAsDebug() {
        val logger = RecordingLogger()
        val provider = createProvider(ClaimTaskExecutor(ThrowingFuture(UnknownHostException("unsupported"))), logger)
        provider.refreshClaims()

        val claims = provider.provideClaims(100L)

        assertFalse(claims.isTimedOut())
        assertEquals(
            listOf(
                "Getting IPv4 claim failed, protocol is not supported",
                "Getting IPv6 claim failed, protocol is not supported",
            ),
            logger.debugMessages,
        )
    }

    @Test
    fun provideClaimsLogsOtherFailuresAsWarnings() {
        val logger = RecordingLogger()
        val provider = createProvider(ClaimTaskExecutor(ThrowingFuture(IllegalStateException("boom"))), logger)
        provider.refreshClaims()

        val claims = provider.provideClaims(100L)

        assertFalse(claims.isTimedOut())
        assertEquals(listOf("Getting IPv4 claim failed", "Getting IPv6 claim failed"), logger.warnMessages)
    }

    @Test
    fun provideClaimWithNullFutureDoesNotAddClaimOrLog() {
        val logger = RecordingLogger()
        val provider = createProvider(ClaimTaskExecutor(ValueFuture("unused")), logger)
        val claims = ProvidedClaims()

        provider.provideClaim(claims, claim = null, type = "IPv4", timeout = 100L)

        assertEquals(emptyList<String>(), claims.toList())
        assertFalse(claims.isTimedOut())
        assertTrue(logger.debugMessages.isEmpty())
        assertTrue(logger.warnMessages.isEmpty())
    }

    @Test
    fun provideClaimAddsResolvedClaim() {
        val provider = createProvider(ClaimTaskExecutor(ValueFuture("unused")))
        val claims = ProvidedClaims()

        provider.provideClaim(claims, ValueFuture("direct-claim"), "IPv4", 100L)

        assertEquals(listOf("direct-claim"), claims.toList())
        assertFalse(claims.isTimedOut())
    }

    @Test
    fun spawnFetchClaimTaskDelegatesToTaskExecutor() {
        val executor = ClaimTaskExecutor(ValueFuture("claim"))
        val provider = createProvider(executor)

        val future = provider.spawnFetchClaimTask(IPProtocol.IPv4)

        assertEquals(1, executor.executeFutureCount)
        assertEquals("claim", future.get())
    }

    private fun createProvider(executor: TaskExecutor, logger: RecordingLogger = RecordingLogger()): ClaimProviderImpl {
        return ClaimProviderImpl(
            TestDeviceInfoImpl(),
            AdvertiserIdProvider { ValueFuture(TestAdvertiserIdInfo()) },
            mock<AttributionApi>(),
            RetryConfig(0, 0, 0, RetryConfig.TEST_INTEGRITY_CONFIG),
            executor,
            logger,
        )
    }

    private class ClaimTaskExecutor(private val future: AsyncFuture<String>) : TaskExecutor {
        var executeFutureCount = 0

        @Suppress("UNCHECKED_CAST")
        override fun <V> executeFuture(task: Task<V>): AsyncFuture<V> {
            executeFutureCount++
            return future as AsyncFuture<V>
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
            task.run()
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean) {
            task.run()
        }

        override fun <V> wrap(callback: Callback<V>): Callback<V> = callback
    }

    private class ThrowingFuture(private val exception: Exception) : AsyncFuture<String> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = true
        override fun get(): String = throw exception
        override fun get(timeout: Long, unit: TimeUnit): String = throw exception
        override suspend fun await(): String = throw exception
        override suspend fun awaitOrNull(timeout: Long, timeUnit: TimeUnit): String = throw exception
        override fun registerCallback(callback: Callback<String>) {
            callback.reject(exception)
        }
    }

    private class RecordingLogger : Logger {
        val debugMessages = mutableListOf<String>()
        val warnMessages = mutableListOf<String>()
        override val fallback = this

        override fun debug(message: String, vararg fields: LoggerFields) {
            debugMessages.add(message)
        }

        override fun info(message: String, vararg fields: LoggerFields) = Unit

        override fun warn(message: String, vararg fields: LoggerFields) {
            warnMessages.add(message)
        }

        override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) {
            warnMessages.add(message)
        }

        override fun error(message: String, vararg fields: LoggerFields) = Unit
        override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit
        override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) = Unit
    }

    private class TestAdvertiserIdInfo : AdvertiserIdInfo {
        override val advertiserId: String = "advertiser-id"
        override val isLimitedAdTracking: Boolean = false
    }
}
