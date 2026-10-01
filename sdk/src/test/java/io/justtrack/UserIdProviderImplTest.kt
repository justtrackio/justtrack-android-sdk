package io.justtrack

import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.executor.TaskExecutor
import io.justtrack.log.LoggerFields
import io.justtrack.providers.AdvertiserIdProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.UUID

internal class UserIdProviderImplTest {
    @Test
    fun provideUserIdFutureCreatesAndCachesFuture() {
        val executor = RecordingUserIdTaskExecutor()
        val provider = UserIdProviderImpl(
            executor,
            TestDeviceInfoImpl(),
            "io.justtrack.test",
            UserIdProviderImpl.AttributionParams(
                attributionIdManager = mock(),
                advertiserIdProvider = AdvertiserIdProvider { ValueFuture(TestAdvertiserIdInfo()) },
                trackingId = null,
            ),
            TestHttpLogger(),
        )

        val first = provider.provideUserIdFuture()
        val second = provider.provideUserIdFuture()

        assertSame(first, second)
        assertEquals(1, executor.executeFutureCount)
        assertEquals(USER_ID, first.get())
    }

    private class RecordingUserIdTaskExecutor : TaskExecutor {
        var executeFutureCount = 0

        @Suppress("UNCHECKED_CAST")
        override fun <V> executeFuture(task: Task<V>): AsyncFuture<V> {
            executeFutureCount++
            return ValueFuture(USER_ID) as AsyncFuture<V>
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
            task.run()
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean) {
            task.run()
        }

        override fun <V> wrap(callback: Callback<V>): Callback<V> = callback
    }

    private class TestHttpLogger : HttpLogger {
        override val fallback = this
        override fun setAdvertiserId(advertiserId: String) = Unit
        override fun setUser(userId: AsyncFuture<UUID?>, installInstanceId: AsyncFuture<String?>) = Unit
        override fun setUser(userId: UUID?, installId: String) = Unit
        override fun sendToServer() = Unit
        override fun setBreadCrumbReporter(reporter: BreadCrumbReporter?) = Unit
        override fun close() = Unit
        override fun debug(message: String, vararg fields: LoggerFields) = Unit
        override fun info(message: String, vararg fields: LoggerFields) = Unit
        override fun warn(message: String, vararg fields: LoggerFields) = Unit
        override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit
        override fun error(message: String, vararg fields: LoggerFields) = Unit
        override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit
        override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) = Unit
    }

    private class TestAdvertiserIdInfo : AdvertiserIdInfo {
        override val advertiserId: String = "advertiser-id"
        override val isLimitedAdTracking: Boolean = false
    }

    private companion object {
        const val USER_ID = "00000000-0000-0000-0000-000000000001"
    }
}
