package io.justtrack

import io.justtrack.api.PrivacyApi
import io.justtrack.exceptions.SdkNotTrackingException
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.providers.AdvertiserIdProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean

class TrackingStateManagerTest {

    private lateinit var isTracking: AtomicBoolean
    private lateinit var mockSessionManager: SessionManager
    private lateinit var mockAttributionOutputProvider: AttributionOutputProvider
    private lateinit var mockCrashReporter: JtCrashReporter
    private lateinit var mockWorkerScheduler: WorkerScheduler
    private lateinit var mockSdkConfigDelegate: SdkConfigDelegate
    private lateinit var mockLogger: HttpLogger
    private lateinit var mockAttributionIdManager: AttributionIdManager
    private lateinit var pendingAdapters: MutableList<IntegrationAdapter>
    private lateinit var integratedAdapters: MutableList<IntegrationAdapter>
    private var integrityTokenPublished = false
    private var userIdSet: String? = null
    private lateinit var manager: TrackingStateManager

    @Before
    fun setup() {
        isTracking = AtomicBoolean(false)
        mockSessionManager = mock()
        mockAttributionOutputProvider = mock()
        mockCrashReporter = mock()
        mockWorkerScheduler = mock()
        mockSdkConfigDelegate = mock()
        mockLogger = mock()
        mockAttributionIdManager = mock {
            on { getStoredUserId() }.thenReturn(ValueFuture(null))
        }
        pendingAdapters = mutableListOf()
        integratedAdapters = mutableListOf()
        integrityTokenPublished = false
        userIdSet = null

        manager = createManager()
    }

    @Test
    fun isRunning_returnsFalse_whenNotStarted() {
        assertFalse(manager.isRunning())
    }

    @Test
    fun stop_setsIsRunningToFalse() {
        isTracking.set(true)
        manager.stop()
        assertFalse(manager.isRunning())
    }

    @Test
    fun startWithConfig_setsIsRunningToTrue() {
        val config = buildConfig()
        manager.startWithConfig(config, mock())
        assertTrue(manager.isRunning())
    }

    @Test
    fun startWithConfig_startsSessionManager() {
        manager.startWithConfig(buildConfig(), mock())
        verify(mockSessionManager).start()
    }

    @Test
    fun startWithConfig_publishesIntegrityToken() {
        manager.startWithConfig(buildConfig(), mock())
        assertTrue(integrityTokenPublished)
    }

    @Test
    fun startWithConfig_callsCrashReporterReport() {
        manager.startWithConfig(buildConfig(), mock())
        verify(mockCrashReporter).report()
    }

    @Test
    fun startWithConfig_callsWorkerSchedulerStartScheduling() {
        manager.startWithConfig(buildConfig(), mock())
        verify(mockWorkerScheduler).startScheduling()
    }

    @Test
    fun startWithConfig_callsProvideAttributionOutput() {
        manager.startWithConfig(buildConfig(), mock())
        verify(mockAttributionOutputProvider).provideAttributionOutput(null)
    }

    @Test
    fun startWithConfig_setsUserId_whenProvided() {
        val config = JustTrackSdkConfig("my-user", null, "advertiserId", null, true)
        manager.startWithConfig(config, mock())
        assertEquals("my-user", userIdSet)
    }

    @Test
    fun startWithConfig_doesNotSetUserId_whenNull() {
        val config = buildConfig()
        manager.startWithConfig(config, mock())
        assertEquals(null, userIdSet)
    }

    @Test
    fun startWithConfig_integratesPendingAdapters() {
        val adapter: IntegrationAdapter = mock()
        pendingAdapters.add(adapter)
        val sdk: JustTrackSdk = mock()

        manager.startWithConfig(buildConfig(), sdk)

        verify(adapter).integrate(any(), org.mockito.kotlin.same(sdk), org.mockito.kotlin.same(mockLogger))
        assertTrue(integratedAdapters.contains(adapter))
        assertTrue(pendingAdapters.isEmpty())
    }

    @Test
    fun startWithConfig_onlyStartsSessionOnce() {
        manager.startWithConfig(buildConfig(), mock())
        manager.startWithConfig(buildConfig(), mock())
        verify(mockSessionManager).start()
    }

    @Test
    fun anonymize_returnsErrorFuture_whenNotTracking() {
        val future = manager.anonymize()
        try {
            future.get()
            throw AssertionError("Expected ExecutionException")
        } catch (e: ExecutionException) {
            assertTrue(e.cause is SdkNotTrackingException)
        }
    }

    @Test
    fun anonymize_callsAnonymizeUserHandler_whenTracking() {
        isTracking.set(true)
        val future = manager.anonymize()
        // With the default mock PrivacyApi returning success, this should resolve
        assertTrue(future.get())
    }

    private fun createManager(): TrackingStateManager {
        val privacyApi = SuccessPrivacyApi()
        val userIdProvider = UserIdProvider { ValueFuture("user-id") }
        val advertiserIdProvider = AdvertiserIdProvider {
            ValueFuture(TestAdvertiserIdInfo(null))
        }
        val sdkFirstInitTimestampRepo: SdkFirstInitializationTimestampRepo = mock {
            on { getOrCreate() }.thenReturn(123L)
        }

        return TrackingStateManager(
            isTracking = isTracking,
            deps = TrackingStateManager.Dependencies(
                context = mock(),
                logger = mockLogger,
                taskExecutor = ImmediateSyncTaskExecutor(),
                deviceInfo = TestDeviceInfoImpl(),
                privacyApi = privacyApi,
                userIdProvider = userIdProvider,
                advertiserIdProvider = advertiserIdProvider,
                attributionIdManager = mockAttributionIdManager,
            ),
            components = TrackingStateManager.Components(
                sdkFirstInitializationTimestampRepo = sdkFirstInitTimestampRepo,
                sdkConfigDelegate = mockSdkConfigDelegate,
                sessionManager = mockSessionManager,
                attributionOutputProvider = mockAttributionOutputProvider,
                crashReporter = mockCrashReporter,
                workerScheduler = mockWorkerScheduler,
                billingTracker = null,
            ),
            integrations = TrackingStateManager.Integrations(
                pendingIntegrationAdapters = pendingAdapters,
                integratedAdapters = integratedAdapters,
            ),
            suppliers = TrackingStateManager.Suppliers(
                integrityTokenSupplier = {
                    integrityTokenPublished = true
                    ValueFuture(true)
                },
                installInstanceIdSupplier = { ValueFuture("install-id") },
                userIdSetter = { userId ->
                    userIdSet = userId
                    ValueFuture(true)
                },
            ),
        )
    }

    private fun buildConfig(): JustTrackSdkConfig = JustTrackSdkConfig(null, null, "advertiserId", null, true)

    private class SuccessPrivacyApi : PrivacyApi {
        override suspend fun anonymizeUser(
            advertiserId: String?,
            uuid: String?,
            installId: String?,
            body: JSONEncodable,
        ): Result<org.json.JSONObject> = Result.success(org.json.JSONObject())
    }

    private class TestAdvertiserIdInfo(
        override val advertiserId: String?,
    ) : io.justtrack.attribution.AdvertiserIdInfo {
        override val isLimitedAdTracking: Boolean = false
    }
}
