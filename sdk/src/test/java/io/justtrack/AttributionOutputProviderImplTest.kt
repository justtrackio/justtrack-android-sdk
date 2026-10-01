package io.justtrack

import io.justtrack.AttributionImpl.CampaignImpl
import io.justtrack.AttributionImpl.ChannelImpl
import io.justtrack.AttributionImpl.PartnerImpl
import io.justtrack.api.AttributionApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.executor.TaskExecutor
import io.justtrack.installreferrer.api.ReferrerDetails
import io.justtrack.log.LoggerFields
import io.justtrack.providers.AdvertiserIdProvider
import io.justtrack.retargeting.RetargetingParametersListener
import io.justtrack.util.InstallerSourceIdProvider
import io.justtrack.versions.SdkVersion
import io.justtrack.versions.VersionBundle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.UUID
import java.util.concurrent.RejectedExecutionException

internal class AttributionOutputProviderImplTest {
    private val networkOutput = AttributionOutput(attributionResponse(installId = "network-install-id"), null, false)
    private val taskExecutor = RecordingTaskExecutor(networkOutput)
    private val databaseInterface = mock<DatabaseInterface>()
    private val attributionInterface = RecordingDatabaseAttributionInterface()
    private val attributionApi = mock<AttributionApi>()
    private val attributionSubscriptionHandler = mock<AttributionSubscriptionHandler>()
    private val retargetingParametersSubscriptions = SubscriptionManager<RetargetingParametersListener>()
    private val httpLogger = RecordingHttpLogger()
    private val networkErrorLogger = NetworkErrorLogger()
    private val attributionIdManager = mock<AttributionIdManager>()
    private val reAttributionDecider = RecordingReAttributionDecider(AttributionDecision.FETCH_FIRST_ATTRIBUTION)
    private val installReferrerTask = object : Task<ReferrerDetails?> {
        override suspend fun execute(): ReferrerDetails? = null
    }
    private val installReferrerProvider = InstallReferrerProvider { installReferrerTask }

    @Test
    fun setOutputStoresCurrentAndLatestOutput() {
        val provider = createProvider()
        val output = ValueFuture(networkOutput)

        provider.setOutput(output)

        assertSame(output, provider.getOutput())
        assertSame(output, provider.getLatestApiAttributionOutput())
    }

    @Test
    fun configurationGettersReturnConfiguredDelays() {
        val provider = createProvider(
            reFetchReAttributionDelaySeconds = 7L,
            attributionRetryDelaySeconds = 11L,
        )

        assertEquals(7L, provider.getReFetchReAttributionDelaySeconds())
        assertEquals(11L, provider.getAttributionRetryDelaySeconds())
    }

    @Test
    fun provideAttributionOutputWithoutForcedDecisionReturnsCachedOutput() {
        val provider = createProvider()
        val cachedOutput = ValueFuture(networkOutput)
        provider.setOutput(cachedOutput)

        val result = provider.provideAttributionOutput()

        assertSame(cachedOutput, result)
        assertEquals(0, taskExecutor.executeFutureCount)
    }

    @Test
    fun provideAttributionOutputWithForcedDecisionSchedulesNewTask() {
        val provider = createProvider()
        provider.setOutput(ValueFuture(networkOutput))

        val result = provider.provideAttributionOutput(AttributionDecision.FETCH_FIRST_ATTRIBUTION)

        assertSame(taskExecutor.scheduledOutput, result)
        assertSame(result, provider.getOutput())
        assertEquals(1, taskExecutor.executeFutureCount)
    }

    @Test
    fun attributionIsOldFailOnlyReturnsTrueForExpiredErrorFuture() {
        val provider = createProvider()
        val errorFuture = ErrorFuture<AttributionOutput>(RuntimeException("failed"))

        assertFalse(provider.attributionIsOldFail(null))
        assertFalse(provider.attributionIsOldFail(ValueFuture(networkOutput)))
        assertFalse(provider.attributionIsOldFail(errorFuture))

        provider.setAttributionCanRetryAt(System.currentTimeMillis() + 60_000L)
        assertFalse(provider.attributionIsOldFail(errorFuture))

        provider.setAttributionCanRetryAt(System.currentTimeMillis() - 1L)
        assertTrue(provider.attributionIsOldFail(errorFuture))
    }

    @Test
    fun getAttributionOutputTaskWhenStoredAttributionIsValidUsesCachedOutput() = runBlocking {
        val storedResponse = attributionResponse(installId = "stored-install-id")
        val storedOutput = AttributionOutput(storedResponse, null, false)
        attributionInterface.storedOutput = storedOutput
        reAttributionDecider.decision = AttributionDecision.USE_STORED_ATTRIBUTION
        val provider = createProvider()

        val result = provider.getAttributionOutputTask(
            attributionOutput = null,
            forcedDecision = null,
            advertiserIdProvider = advertiserIdProvider(),
            appSetIdFuture = ValueFuture(null),
            trackingId = null,
            trackingProvider = TRACKING_PROVIDER,
            integritySecretFuture = ValueFuture(INTEGRITY_SECRET),
        ).execute()

        assertSame(storedOutput, result)
        assertTrue(attributionInterface.setLastOpenCalled)
        assertEquals(storedResponse.getUserId(), httpLogger.userId)
        assertEquals(storedResponse.getInstallId(), httpLogger.installId)
        assertEquals(listOf("Using cached attribution"), httpLogger.debugMessages)
        verify(attributionSubscriptionHandler).callAttributionSubscriptions(same(storedResponse))
        assertEquals(0, taskExecutor.executeFutureCount)
    }

    @Test
    fun getAttributionOutputTaskWhenMigrationInvalidatedCacheFetchesBackend() = runBlocking {
        attributionInterface.storedOutput = null
        attributionInterface.timestamps = AttributionTimestamps(100L, 200L, 300L)
        reAttributionDecider.decision = AttributionDecision.USE_STORED_ATTRIBUTION
        val provider = createProvider()

        val result = provider.getAttributionOutputTask(
            attributionOutput = null,
            forcedDecision = null,
            advertiserIdProvider = advertiserIdProvider(),
            appSetIdFuture = ValueFuture(null),
            trackingId = null,
            trackingProvider = TRACKING_PROVIDER,
            integritySecretFuture = ValueFuture(INTEGRITY_SECRET),
        ).execute()

        assertSame(networkOutput, result)
        assertTrue(taskExecutor.executeFutureCount > 0)
    }

    @Test
    fun getAttributionOutputTaskWhenCurrentOutputExistsReturnsCurrentOutput() = runBlocking {
        val currentFuture = ValueFuture(networkOutput)
        val provider = createProvider()

        val result = provider.getAttributionOutputTask(
            attributionOutput = currentFuture,
            forcedDecision = null,
            advertiserIdProvider = advertiserIdProvider(),
            appSetIdFuture = ValueFuture(null),
            trackingId = null,
            trackingProvider = TRACKING_PROVIDER,
            integritySecretFuture = ValueFuture(INTEGRITY_SECRET),
        ).execute()

        assertSame(networkOutput, result)
        assertTrue(attributionInterface.setLastOpenCalled)
        assertEquals(0, taskExecutor.executeFutureCount)
    }

    @Test
    fun getAttributionOutputTaskWhenCurrentOutputIsExpiredFailureRetriesAttribution() = runBlocking {
        val failedFuture = ErrorFuture<AttributionOutput>(RuntimeException("old failure"))
        val provider = createProvider()
        provider.setAttributionCanRetryAt(System.currentTimeMillis() - 1L)

        val result = provider.getAttributionOutputTask(
            attributionOutput = failedFuture,
            forcedDecision = null,
            advertiserIdProvider = advertiserIdProvider(),
            appSetIdFuture = ValueFuture(null),
            trackingId = null,
            trackingProvider = TRACKING_PROVIDER,
            integritySecretFuture = ValueFuture(INTEGRITY_SECRET),
        ).execute()

        assertSame(networkOutput, result)
        assertEquals(listOf("Retrying old failed attribution", "Fetched first attribution"), httpLogger.debugMessages)
        assertTrue(taskExecutor.executeFutureCount >= 2)
    }

    @Test
    fun getAttributionOutputTaskWhenStoredOutputParsingFailsLogsWarningAndFetches() = runBlocking {
        attributionInterface.storedOutputException = IllegalStateException("bad stored data")
        val provider = createProvider()

        val result = provider.getAttributionOutputTask(
            attributionOutput = null,
            forcedDecision = null,
            advertiserIdProvider = advertiserIdProvider(),
            appSetIdFuture = ValueFuture(null),
            trackingId = null,
            trackingProvider = TRACKING_PROVIDER,
            integritySecretFuture = ValueFuture(INTEGRITY_SECRET),
        ).execute()

        assertSame(networkOutput, result)
        assertEquals(listOf("Failed to parse stored response data"), httpLogger.warnMessages)
    }

    @Test
    fun getAttributionOutputTaskReusesInstallReferrerTask() = runBlocking {
        val provider = createProvider()
        reAttributionDecider.decision = AttributionDecision.FETCH_FIRST_ATTRIBUTION

        provider.getAttributionOutputTask(
            attributionOutput = null,
            forcedDecision = null,
            advertiserIdProvider = advertiserIdProvider(),
            appSetIdFuture = ValueFuture(null),
            trackingId = null,
            trackingProvider = TRACKING_PROVIDER,
            integritySecretFuture = ValueFuture(INTEGRITY_SECRET),
        ).execute()
        provider.getAttributionOutputTask(
            attributionOutput = null,
            forcedDecision = null,
            advertiserIdProvider = advertiserIdProvider(),
            appSetIdFuture = ValueFuture(null),
            trackingId = null,
            trackingProvider = TRACKING_PROVIDER,
            integritySecretFuture = ValueFuture(INTEGRITY_SECRET),
        ).execute()

        assertEquals(1, taskExecutor.referrerTaskExecutionCount)
    }

    @Test
    fun getAttributionOutputTaskWithForcedRetargetingAndStoredOutputSkipsReferrerDetails() = runBlocking {
        attributionInterface.storedOutput = AttributionOutput(attributionResponse(), null, false)
        val provider = createProvider()

        provider.getAttributionOutputTask(
            attributionOutput = null,
            forcedDecision = AttributionDecision.FETCH_RETARGETING_ATTRIBUTION,
            advertiserIdProvider = advertiserIdProvider(),
            appSetIdFuture = ValueFuture(null),
            trackingId = null,
            trackingProvider = TRACKING_PROVIDER,
            integritySecretFuture = ValueFuture(INTEGRITY_SECRET),
        ).execute()

        assertEquals(0, taskExecutor.referrerTaskExecutionCount)
    }

    @Test
    fun getAttributionOutputTaskWhenNestedAttributionFutureFailsLogsAndRethrowsCause() = runBlocking {
        val throwingProvider = createProvider(taskExecutor = ThrowingNestedTaskExecutor())

        try {
            throwingProvider.getAttributionOutputTask(
                attributionOutput = null,
                forcedDecision = null,
                advertiserIdProvider = advertiserIdProvider(),
                appSetIdFuture = ValueFuture(null),
                trackingId = null,
                trackingProvider = TRACKING_PROVIDER,
                integritySecretFuture = ValueFuture(INTEGRITY_SECRET),
            ).execute()
            fail("Expected RejectedExecutionException")
        } catch (exception: RejectedExecutionException) {
            // expected from the final attributionFuture.await() after the handler failure is logged
        }

        assertEquals(listOf("Failed to wait for attribution"), httpLogger.warnMessages)
    }

    private fun createProvider(
        taskExecutor: TaskExecutor = this.taskExecutor,
        reFetchReAttributionDelaySeconds: Long = 0L,
        attributionRetryDelaySeconds: Long = 1L,
    ): AttributionOutputProviderImpl {
        whenever(databaseInterface.openAttribution()).thenReturn(attributionInterface)
        return AttributionOutputProviderImpl(
            attributionApi,
            taskExecutor,
            databaseInterface,
            AttributionOutputProviderImpl.AttributionParams(
                idManager = attributionIdManager,
                userIdProvider = UserIdProvider { ValueFuture(USER_ID.toString()) },
                reAttributionDecider = reAttributionDecider,
                installReferrerProvider = installReferrerProvider,
                claimProvider = mock(),
                retryConfig = RetryConfig(0, 0, 0, RetryConfig.TEST_INTEGRITY_CONFIG),
                trackingId = null,
                trackingProvider = TRACKING_PROVIDER,
                advertiserIdProvider = advertiserIdProvider(),
                appSetIdFuture = { ValueFuture(null) },
                integritySecretFuture = { ValueFuture(INTEGRITY_SECRET) },
                reFetchReAttributionDelaySeconds = reFetchReAttributionDelaySeconds,
                attributionRetryDelaySeconds = attributionRetryDelaySeconds,
                sdkConfig = JustTrackSdkConfig(null, null, TRACKING_PROVIDER, null, false),
                deviceInfo = TestDeviceInfoImpl(),
                intent = null,
                attributionIdManager = attributionIdManager,
                installerSourceIdProvider = InstallerSourceIdProvider { "installer" },
                preliminaryRetargetingParametersImpl = null,
            ),
            AttributionOutputProviderImpl.Subscribers(
                attributionSubscriptionHandler,
                retargetingParametersSubscriptions,
            ),
            AttributionOutputProviderImpl.Loggers(httpLogger, networkErrorLogger),
            VersionBundle(testSdkVersion(), TestDeviceInfoImpl().getAppVersion()),
        )
    }

    private fun advertiserIdProvider(): AdvertiserIdProvider = AdvertiserIdProvider { ValueFuture(TestAdvertiserIdInfo()) }

    private class RecordingDatabaseAttributionInterface : DatabaseAttributionInterface {
        var timestamps: AttributionTimestamps? = null
        var storedOutput: AttributionOutput? = null
        var storedOutputException: Throwable? = null
        var setLastOpenCalled = false

        override suspend fun setIntegrityTokenSent(isSent: Boolean): Boolean = true
        override suspend fun isIntegrityTokenSent(): Boolean = false
        override suspend fun setIntegritySecret(token: String): Boolean = true
        override suspend fun getIntegritySecret(): String? = null

        override suspend fun setLastOpen(currentMs: Long): Boolean {
            setLastOpenCalled = true
            return true
        }

        override suspend fun setAttributionFinished(response: AttributionResponse): Boolean = true
        override suspend fun getAttributionTimestamps(): AttributionTimestamps? = timestamps

        override suspend fun getStoredOutput(): AttributionOutput? {
            storedOutputException?.let { throw it }
            return storedOutput
        }

        override suspend fun getAppVersionUpdateInfo(currentApplicationVersion: ApplicationVersion): AppVersionUpdateInfo? = null
        override suspend fun getInstallId(): String? = null
        override suspend fun getUserId(): String? = null
        override suspend fun setInstallId(installId: String): Boolean = true
        override suspend fun setUserId(userId: String): Boolean = true
        override fun close() = Unit
    }

    private class RecordingTaskExecutor(private val networkOutput: AttributionOutput) : TaskExecutor {
        val scheduledOutput = ValueFuture(networkOutput)
        var executeFutureCount = 0
        var referrerTaskExecutionCount = 0

        @Suppress("UNCHECKED_CAST")
        override fun <V> executeFuture(task: Task<V>): AsyncFuture<V> {
            executeFutureCount++
            return if (task.javaClass.name.contains("AttributionOutputProviderImplTest")) {
                referrerTaskExecutionCount++
                ValueFuture(null) as AsyncFuture<V>
            } else {
                scheduledOutput as AsyncFuture<V>
            }
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
            task.run()
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean) {
            task.run()
        }

        override fun <V> wrap(callback: Callback<V>): Callback<V> = callback
    }

    private class ThrowingNestedTaskExecutor : TaskExecutor {
        @Suppress("UNCHECKED_CAST")
        override fun <V> executeFuture(task: Task<V>): AsyncFuture<V> {
            return ErrorFuture<AttributionOutput>(RejectedExecutionException("rejected")) as AsyncFuture<V>
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) = Unit
        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean) = Unit
        override fun <V> wrap(callback: Callback<V>): Callback<V> = callback
    }

    private class RecordingReAttributionDecider(var decision: AttributionDecision) : ReAttributionDecider {
        override fun needsReAttribution(attributionTimestamps: AttributionTimestamps?): AttributionDecision = decision
    }

    private class RecordingHttpLogger : HttpLogger {
        val debugMessages = mutableListOf<String>()
        val warnMessages = mutableListOf<String>()
        var userId: UUID? = null
        var installId: String? = null

        override val fallback = this

        override fun setAdvertiserId(advertiserId: String) = Unit

        override fun setUser(userId: AsyncFuture<UUID?>, installInstanceId: AsyncFuture<String?>) = Unit

        override fun setUser(userId: UUID?, installId: String) {
            this.userId = userId
            this.installId = installId
        }

        override fun sendToServer() = Unit
        override fun setBreadCrumbReporter(reporter: BreadCrumbReporter?) = Unit
        override fun close() = Unit

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

    private fun attributionResponse(installId: String = "install-id"): AttributionResponse {
        return object : AttributionResponse {
            override fun getUserId(): UUID = USER_ID
            override fun getInstallId(): String = installId
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

    private fun testSdkVersion(): SdkVersion {
        return object : SdkVersion {
            override val platformType: PlatformType = PlatformType.ANDROID
            override val major: Int = 1
            override val minor: Int = 0
            override val patch: Int = 0
            override val name: String = "1.0.0"
        }
    }

    private companion object {
        val USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        const val TRACKING_PROVIDER = "advertiserId"
        const val INTEGRITY_SECRET = "integrity-secret"
    }
}
