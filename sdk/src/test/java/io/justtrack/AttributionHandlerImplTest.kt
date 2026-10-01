package io.justtrack

import io.justtrack.AttributionImpl.CampaignImpl
import io.justtrack.AttributionImpl.ChannelImpl
import io.justtrack.AttributionImpl.PartnerImpl
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.executor.TaskExecutor
import io.justtrack.log.LoggerFields
import io.justtrack.providers.AdvertiserIdProvider
import io.justtrack.retargeting.RetargetingParameters
import io.justtrack.retargeting.RetargetingParametersListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import java.util.Date
import java.util.UUID
import java.util.concurrent.RejectedExecutionException

@OptIn(ExperimentalCoroutinesApi::class)
internal class AttributionHandlerImplTest {
    private val taskExecutor = ImmediateSyncTaskExecutor()
    private val attributionOutputProvider = RecordingAttributionOutputProvider()
    private val attributionIdManager = mock<AttributionIdManager>()
    private val userIdProvider = UserIdProvider { ValueFuture(USER_ID) }
    private val advertiserIdProvider = AdvertiserIdProvider { ValueFuture(TestAdvertiserIdInfo()) }
    private val httpLogger = TestHttpLogger()
    private val networkErrorLogger = NetworkErrorLogger()
    private val attributionSubscriptionHandler = mock<AttributionSubscriptionHandler>()
    private val retargetingParametersSubscriptions = SubscriptionManager<RetargetingParametersListener>()

    private val handler = AttributionHandlerImpl(
        taskExecutor,
        AttributionHandlerImpl.AttributionParams(
            attributionOutputProvider,
            attributionIdManager,
            userIdProvider,
            advertiserIdProvider,
        ),
        AttributionHandlerImpl.Loggers(httpLogger, networkErrorLogger),
        AttributionHandlerImpl.Subscribers(
            attributionSubscriptionHandler,
            retargetingParametersSubscriptions,
        ),
        preliminaryRetargetingParametersImpl = null,
    )

    @Test
    fun handleAttribution_successChecksInstallIdAndNotifiesAttributionSubscribers() {
        val response = attributionResponse(installId = INSTALL_ID)
        val output = AttributionOutput(response, retargetingParameters = null, claimsTimedOut = false)

        handler.handleAttribution(
            ValueFuture(output),
            storedResponse = null,
            attributionTimestamps = null,
            forcedDecision = null,
            attributionDecision = AttributionDecision.FETCH_FIRST_ATTRIBUTION,
        )

        verify(attributionIdManager).checkInstallIdChange(
            eq(INSTALL_ID),
            any(),
            same(advertiserIdProvider),
        )
        verify(attributionSubscriptionHandler).callAttributionSubscriptions(same(response))
        assertEquals(listOf("Fetched first attribution"), httpLogger.debugMessages)
    }

    @Test
    fun handleAttribution_withRetargetingParametersNotifiesRetargetingSubscribers() {
        val retargetingParameters = mock<RetargetingParameters>()
        val listener = mock<RetargetingParametersListener>()
        retargetingParametersSubscriptions.subscribe(listener)
        val output = AttributionOutput(
            attributionResponse(),
            retargetingParameters = retargetingParameters,
            claimsTimedOut = false,
        )

        handler.handleAttribution(
            ValueFuture(output),
            storedResponse = null,
            attributionTimestamps = null,
            forcedDecision = null,
            attributionDecision = AttributionDecision.FETCH_FIRST_ATTRIBUTION,
        )

        verify(listener).onRetargetingParametersReceived(same(retargetingParameters))
    }

    @Test
    fun handleAttribution_withoutRetargetingParametersDoesNotNotifyRetargetingSubscribers() {
        val listener = mock<RetargetingParametersListener>()
        retargetingParametersSubscriptions.subscribe(listener)
        val output = AttributionOutput(
            attributionResponse(),
            retargetingParameters = null,
            claimsTimedOut = false,
        )

        handler.handleAttribution(
            ValueFuture(output),
            storedResponse = null,
            attributionTimestamps = null,
            forcedDecision = null,
            attributionDecision = AttributionDecision.FETCH_FIRST_ATTRIBUTION,
        )

        verify(listener, never()).onRetargetingParametersReceived(any())
    }

    @Test
    fun handleAttribution_whenLatestFutureFailsStoresErrorFutureAndRetryTime() {
        val exception = RuntimeException("network down")
        val attributionFuture = ErrorFuture<AttributionOutput>(exception)
        attributionOutputProvider.latestFuture = attributionFuture

        handler.handleAttribution(
            attributionFuture,
            storedResponse = null,
            attributionTimestamps = null,
            forcedDecision = null,
            attributionDecision = AttributionDecision.FETCH_FIRST_ATTRIBUTION,
        )

        assertTrue(attributionOutputProvider.currentOutput is ErrorFuture<AttributionOutput>)
        assertTrue(attributionOutputProvider.retryAt > System.currentTimeMillis())
        verify(attributionSubscriptionHandler, never()).callAttributionSubscriptions(any())
    }

    @Test
    fun handleAttribution_whenStaleFutureFailsDoesNotReplaceCurrentOutput() {
        val currentOutput = ValueFuture(AttributionOutput(attributionResponse(), null, false))
        attributionOutputProvider.currentOutput = currentOutput
        attributionOutputProvider.latestFuture = currentOutput
        val staleFuture = ErrorFuture<AttributionOutput>(RuntimeException("stale failure"))

        handler.handleAttribution(
            staleFuture,
            storedResponse = null,
            attributionTimestamps = null,
            forcedDecision = null,
            attributionDecision = AttributionDecision.FETCH_FIRST_ATTRIBUTION,
        )

        assertSame(currentOutput, attributionOutputProvider.currentOutput)
        assertEquals(0L, attributionOutputProvider.retryAt)
    }

    @Test
    fun handleAttribution_whenStoredAttributionMissingLogsNotNeededButNotCached() {
        val output = AttributionOutput(attributionResponse(), null, false)

        handler.handleAttribution(
            ValueFuture(output),
            storedResponse = null,
            attributionTimestamps = AttributionTimestamps(1L, 2L, 3L),
            forcedDecision = null,
            attributionDecision = AttributionDecision.USE_STORED_ATTRIBUTION,
        )

        assertEquals(listOf("Attribution was not needed, but was not cached"), httpLogger.infoMessages)
    }

    @Test
    fun handleAttribution_whenAttributionWasRefetchedLogsRefetchReason() {
        val output = AttributionOutput(attributionResponse(), null, false)

        handler.handleAttribution(
            ValueFuture(output),
            storedResponse = null,
            attributionTimestamps = AttributionTimestamps(1L, 2L, 3L),
            forcedDecision = AttributionDecision.FETCH_FIRST_ATTRIBUTION,
            attributionDecision = AttributionDecision.FETCH_FIRST_ATTRIBUTION,
        )

        assertEquals(
            listOf("Attribution was fetched again because re-attribution was needed"),
            httpLogger.debugMessages,
        )
    }

    @Test
    fun handleAttribution_whenNoRefetchIsNeededResolvesPreliminaryRetargetingParameters() {
        val preliminaryRetargetingParameters = mock<PreliminaryRetargetingParametersImpl>()
        val handler = createHandler(preliminaryRetargetingParametersImpl = preliminaryRetargetingParameters)
        val output = AttributionOutput(attributionResponse(), null, false)

        handler.handleAttribution(
            ValueFuture(output),
            storedResponse = null,
            attributionTimestamps = null,
            forcedDecision = null,
            attributionDecision = AttributionDecision.FETCH_FIRST_ATTRIBUTION,
        )

        verify(preliminaryRetargetingParameters).resolve(same(output))
    }

    @Test
    fun handleAttribution_whenOrganicClaimsTimedOutSchedulesSlowClaimsRefetch() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val provider = RecordingAttributionOutputProvider()
            val logger = TestHttpLogger()
            val handler = createHandler(attributionOutputProvider = provider, httpLogger = logger)
            val output = AttributionOutput(attributionResponse(organic = true), null, claimsTimedOut = true)

            handler.handleAttribution(
                ValueFuture(output),
                storedResponse = null,
                attributionTimestamps = null,
                forcedDecision = null,
                attributionDecision = AttributionDecision.FETCH_FIRST_ATTRIBUTION,
            )
            advanceTimeBy(DEFAULT_CLAIM_RE_FETCH_DELAY_MS)
            runCurrent()

            assertEquals(listOf(AttributionDecision.FETCH_FIRST_ATTRIBUTION.withSlowClaimTimeout()), provider.forcedDecisions)
            assertEquals(listOf("Fetching attribution again with longer timeout while waiting for claims"), logger.infoMessages)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun handleAttribution_whenRetargetingAttributionDidNotChangeSchedulesDelayedRefetch() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val provider = RecordingAttributionOutputProvider(reFetchReAttributionDelaySeconds = 1L)
            val logger = TestHttpLogger()
            val handler = createHandler(attributionOutputProvider = provider, httpLogger = logger)
            val output = AttributionOutput(attributionResponse(installId = INSTALL_ID), null, claimsTimedOut = false)
            val storedResponse = attributionResponse(installId = INSTALL_ID)

            handler.handleAttribution(
                ValueFuture(output),
                storedResponse = storedResponse,
                attributionTimestamps = null,
                forcedDecision = null,
                attributionDecision = AttributionDecision.FETCH_RETARGETING_ATTRIBUTION,
            )
            advanceTimeBy(1_000L)
            runCurrent()

            assertEquals(listOf(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION_DELAYED), provider.forcedDecisions)
            assertEquals(
                listOf("Fetching attribution again because the retargeting delay expired and previously the attribution did not change"),
                logger.infoMessages,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun handleAttribution_whenRetargetingAttributionDidNotChangeAndClaimsTimedOutSchedulesDelayedSlowClaimsRefetch() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val provider = RecordingAttributionOutputProvider(reFetchReAttributionDelaySeconds = 1L)
            val logger = TestHttpLogger()
            val handler = createHandler(attributionOutputProvider = provider, httpLogger = logger)
            val output = AttributionOutput(attributionResponse(installId = INSTALL_ID, organic = true), null, claimsTimedOut = true)

            handler.handleAttribution(
                ValueFuture(output),
                storedResponse = null,
                attributionTimestamps = null,
                forcedDecision = null,
                attributionDecision = AttributionDecision.FETCH_RETARGETING_ATTRIBUTION,
            )
            advanceTimeBy(1_000L)
            runCurrent()

            assertEquals(
                listOf(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION_DELAYED.withSlowClaimTimeout()),
                provider.forcedDecisions,
            )
            assertEquals(
                listOf(
                    "Fetching attribution again (with longer claims timeout) " +
                        "because the retargeting delay expired and previously the attribution did not change",
                ),
                logger.infoMessages,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun handleAttribution_whenRetargetingAttributionChangedDoesNotScheduleDelayedRefetch() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val provider = RecordingAttributionOutputProvider(reFetchReAttributionDelaySeconds = 1L)
            val handler = createHandler(attributionOutputProvider = provider)
            val output = AttributionOutput(attributionResponse(installId = "new-install-id"), null, claimsTimedOut = false)
            val storedResponse = attributionResponse(installId = "old-install-id")

            handler.handleAttribution(
                ValueFuture(output),
                storedResponse = storedResponse,
                attributionTimestamps = null,
                forcedDecision = null,
                attributionDecision = AttributionDecision.FETCH_RETARGETING_ATTRIBUTION,
            )
            advanceTimeBy(1_000L)
            runCurrent()

            assertTrue(provider.forcedDecisions.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun handleException_whenLatestFailureIsUnrecoverableSetsOneHourRetryTime() {
        val exception = BadResponseException("bad request", 400)
        val attributionFuture = ErrorFuture<AttributionOutput>(exception)
        attributionOutputProvider.latestFuture = attributionFuture
        val before = System.currentTimeMillis()

        handler.handleException(exception, attributionFuture)

        assertTrue(attributionOutputProvider.currentOutput is ErrorFuture<AttributionOutput>)
        assertTrue(attributionOutputProvider.retryAt >= before + ONE_HOUR_MS)
    }

    @Test
    fun handleException_whenFailureIsNetworkProblemLogsInfo() {
        val exception = NetworkProblemException(java.net.UnknownHostException("offline"))
        val attributionFuture = ErrorFuture<AttributionOutput>(exception)
        attributionOutputProvider.latestFuture = attributionFuture

        handler.handleException(exception, attributionFuture)

        assertEquals(listOf("Failed to wait for attribution"), httpLogger.infoMessages)
    }

    @Test
    fun callRetargetingParametersSubscriptions_whenExecutorRejectsLogsShutdownWarning() {
        val logger = TestHttpLogger()
        val handler = createHandler(
            taskExecutor = RejectingTaskExecutor(),
            httpLogger = logger,
        )
        retargetingParametersSubscriptions.subscribe(mock())

        handler.callRetargetingParametersSubscriptions(mock())

        assertEquals(
            listOf("Could not call retargeting parameter subscription, SDK is shutting down"),
            logger.warnMessages,
        )
    }

    @Test
    fun onAttributionDoneChecksInstallIdChange() {
        val output = AttributionOutput(attributionResponse(installId = INSTALL_ID), null, false)

        handler.onAttributionDone(output)

        verify(attributionIdManager).checkInstallIdChange(
            eq(INSTALL_ID),
            any(),
            same(advertiserIdProvider),
        )
    }

    @Test
    fun fetchAttributionAgainAfterLogsMessageAndRequestsForcedAttribution() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val provider = RecordingAttributionOutputProvider()
            val logger = TestHttpLogger()
            val handler = createHandler(attributionOutputProvider = provider, httpLogger = logger)

            handler.fetchAttributionAgainAfter("refetch now", AttributionDecision.USE_STORED_ATTRIBUTION, delaySeconds = 0L)
            runCurrent()

            assertEquals(listOf("refetch now"), logger.infoMessages)
            assertEquals(listOf(AttributionDecision.USE_STORED_ATTRIBUTION), provider.forcedDecisions)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createHandler(
        taskExecutor: TaskExecutor = this.taskExecutor,
        attributionOutputProvider: RecordingAttributionOutputProvider = this.attributionOutputProvider,
        attributionIdManager: AttributionIdManager = this.attributionIdManager,
        httpLogger: TestHttpLogger = this.httpLogger,
        preliminaryRetargetingParametersImpl: PreliminaryRetargetingParametersImpl? = null,
    ): AttributionHandlerImpl {
        return AttributionHandlerImpl(
            taskExecutor,
            AttributionHandlerImpl.AttributionParams(
                attributionOutputProvider,
                attributionIdManager,
                userIdProvider,
                advertiserIdProvider,
            ),
            AttributionHandlerImpl.Loggers(httpLogger, networkErrorLogger),
            AttributionHandlerImpl.Subscribers(
                attributionSubscriptionHandler,
                retargetingParametersSubscriptions,
            ),
            preliminaryRetargetingParametersImpl,
        )
    }

    private fun attributionResponse(installId: String = "install-id", organic: Boolean = false): AttributionResponse {
        return object : AttributionResponse {
            override fun getUserId(): UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
            override fun getInstallId(): String = installId
            override fun getUserType(): String = "acquisition"
            override fun getCampaign() = CampaignImpl("1", "campaign", "acquisition", organic)
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

    private class RecordingAttributionOutputProvider(
        private val reFetchReAttributionDelaySeconds: Long = 0L,
        private val attributionRetryDelaySeconds: Long = 1L,
    ) : AttributionOutputProvider {
        var currentOutput: AsyncFuture<AttributionOutput>? = null
        var latestFuture: AsyncFuture<AttributionOutput>? = null
        var retryAt: Long = 0L
        val forcedDecisions = mutableListOf<AttributionDecision?>()

        override fun setOutput(output: AsyncFuture<AttributionOutput>?) {
            currentOutput = output
            latestFuture = output
        }

        override fun getOutput(): AsyncFuture<AttributionOutput>? = currentOutput

        override fun setAttributionCanRetryAt(value: Long) {
            retryAt = value
        }

        override fun getLatestApiAttributionOutput(): AsyncFuture<AttributionOutput>? = latestFuture

        override fun getReFetchReAttributionDelaySeconds(): Long = reFetchReAttributionDelaySeconds

        override fun getAttributionRetryDelaySeconds(): Long = attributionRetryDelaySeconds

        override fun provideAttributionOutput(forcedDecision: AttributionDecision?): AsyncFuture<AttributionOutput> {
            forcedDecisions.add(forcedDecision)
            return currentOutput ?: ErrorFuture(IllegalStateException("No attribution output configured"))
        }
    }

    private class TestHttpLogger : HttpLogger {
        val debugMessages = mutableListOf<String>()
        val infoMessages = mutableListOf<String>()
        val warnMessages = mutableListOf<String>()

        override val fallback = this

        override fun setAdvertiserId(advertiserId: String) = Unit

        override fun setUser(userId: AsyncFuture<UUID?>, installInstanceId: AsyncFuture<String?>) = Unit

        override fun setUser(userId: UUID?, installId: String) = Unit

        override fun sendToServer() = Unit

        override fun setBreadCrumbReporter(reporter: BreadCrumbReporter?) = Unit

        override fun close() = Unit

        override fun debug(message: String, vararg fields: LoggerFields) {
            debugMessages.add(message)
        }

        override fun info(message: String, vararg fields: LoggerFields) {
            infoMessages.add(message)
        }

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

    private class RejectingTaskExecutor : TaskExecutor {
        override fun <V> executeFuture(task: Task<V>): AsyncFuture<V> {
            return ErrorFuture(RejectedExecutionException("rejected"))
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
            rejectedHandler.handleRejectedExecution(RejectedExecutionException("rejected"))
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean) {
            rejectedHandler.handleRejectedExecution(RejectedExecutionException("rejected"))
        }

        override fun <V> wrap(callback: Callback<V>): Callback<V> = callback
    }

    private class TestAdvertiserIdInfo : AdvertiserIdInfo {
        override val advertiserId: String = "advertiser-id"
        override val isLimitedAdTracking: Boolean = false
    }

    private companion object {
        const val INSTALL_ID = "install-id"
        const val USER_ID = "user-id"
        const val DEFAULT_CLAIM_RE_FETCH_DELAY_MS = 3_000L
        const val ONE_HOUR_MS = 3_600_000L
    }
}
