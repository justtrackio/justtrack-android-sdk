package io.justtrack

import io.justtrack.AttributionImpl.CampaignImpl
import io.justtrack.AttributionImpl.ChannelImpl
import io.justtrack.AttributionImpl.PartnerImpl
import io.justtrack.attribution.Attribution
import io.justtrack.executor.TaskExecutor
import io.justtrack.log.LoggerFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.UUID
import java.util.concurrent.RejectedExecutionException

internal class AttributionSubscriptionHandlerImplTest {
    @Test
    fun callAttributionSubscriptionsCallsAllSubscribersWithAttribution() {
        val subscriptions = SubscriptionManager<AttributionListener>()
        val first = RecordingAttributionListener()
        val second = RecordingAttributionListener()
        subscriptions.subscribe(first)
        subscriptions.subscribe(second)
        val handler = AttributionSubscriptionHandlerImpl(
            subscriptions,
            RecordingHttpLogger(),
            NetworkErrorLogger(),
            ImmediateSyncTaskExecutor(),
        )

        handler.callAttributionSubscriptions(attributionResponse())

        assertEquals("acquisition", first.attribution?.userType)
        assertEquals("campaign", first.attribution?.campaign?.name)
        assertEquals("acquisition", second.attribution?.userType)
        assertEquals("campaign", second.attribution?.campaign?.name)
    }

    @Test
    fun callAttributionSubscriptionsLogsWhenExecutorRejectsCallback() {
        val subscriptions = SubscriptionManager<AttributionListener>()
        val listener = RecordingAttributionListener()
        subscriptions.subscribe(listener)
        val logger = RecordingHttpLogger()
        val handler = AttributionSubscriptionHandlerImpl(
            subscriptions,
            logger,
            NetworkErrorLogger(),
            RejectingTaskExecutor(),
        )

        handler.callAttributionSubscriptions(attributionResponse())

        assertTrue(listener.attribution == null)
        assertEquals(listOf("Could not call attribution subscription, SDK is shutting"), logger.warnMessages)
    }

    private class RecordingAttributionListener : AttributionListener {
        var attribution: Attribution? = null

        override fun onAttributionReceived(attribution: Attribution) {
            this.attribution = attribution
        }
    }

    private class RejectingTaskExecutor : TaskExecutor {
        override fun <V> executeFuture(task: Task<V>): AsyncFuture<V> = ErrorFuture(RejectedExecutionException("rejected"))

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
            rejectedHandler.handleRejectedExecution(RejectedExecutionException("rejected"))
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean) {
            rejectedHandler.handleRejectedExecution(RejectedExecutionException("rejected"))
        }

        override fun <V> wrap(callback: Callback<V>): Callback<V> = callback
    }

    private class RecordingHttpLogger : HttpLogger {
        val warnMessages = mutableListOf<String>()
        override val fallback = this
        override fun setAdvertiserId(advertiserId: String) = Unit
        override fun setUser(userId: AsyncFuture<UUID?>, installInstanceId: AsyncFuture<String?>) = Unit
        override fun setUser(userId: UUID?, installId: String) = Unit
        override fun sendToServer() = Unit
        override fun setBreadCrumbReporter(reporter: BreadCrumbReporter?) = Unit
        override fun close() = Unit
        override fun debug(message: String, vararg fields: LoggerFields) = Unit
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

    private fun attributionResponse(): AttributionResponse {
        return object : AttributionResponse {
            override fun getUserId(): UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
            override fun getInstallId(): String = "install-id"
            override fun getUserType(): String = "acquisition"
            override fun getCampaign() = CampaignImpl("1", "campaign", "acquisition", false)
            override fun getChannel() = ChannelImpl(2, "channel", false)
            override fun getPartner() = PartnerImpl(3, "partner")
            override fun getSourceId(): String? = null
            override fun getSourceBundleId(): String? = null
            override fun getSourcePlacement(): String? = null
            override fun getAdsetId(): String? = null
            override fun getCreatedAt(): Date = Date(0L)
            override fun getRedownload(): Boolean = false
        }
    }
}
