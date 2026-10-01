package io.justtrack

import io.justtrack.crashes.ANRDetector
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class SdkLifecycleManagerTest {

    private lateinit var mockSessionManager: SessionManager
    private lateinit var mockConnectivityProvider: ConnectivityProvider
    private lateinit var mockPublishEventsQueue: PublishEventsQueue
    private lateinit var mockPeriodicLogsPublisher: PeriodicLogsPublisher
    private lateinit var mockAnrDetector: ANRDetector
    private lateinit var mockWorkerScheduler: WorkerScheduler
    private lateinit var mockLogger: HttpLogger
    private lateinit var mockExecutor: ExecutorService
    private lateinit var isTracking: AtomicBoolean
    private var clearInstanceCalled = false
    private lateinit var manager: SdkLifecycleManager

    @Before
    fun setup() {
        mockSessionManager = mock()
        mockConnectivityProvider = mock()
        mockPublishEventsQueue = mock()
        mockPeriodicLogsPublisher = mock()
        mockAnrDetector = mock()
        mockWorkerScheduler = mock()
        mockLogger = mock()
        mockExecutor = mock()
        isTracking = AtomicBoolean(false)
        clearInstanceCalled = false
        manager = createManager()
    }

    @Test
    fun onPause_callsSessionManagerOnPause() {
        manager.onPause()
        verify(mockSessionManager).onPause()
    }

    @Test
    fun onPause_pausesPeriodicLogsPublisher() {
        manager.onPause()
        verify(mockPeriodicLogsPublisher).pause()
    }

    @Test
    fun onPause_callsWorkerSchedulerOnPause() {
        manager.onPause()
        verify(mockWorkerScheduler).onPause()
    }

    @Test
    fun onPause_stopsAnrDetector() {
        manager.onPause()
        verify(mockAnrDetector).stop()
    }

    @Test
    fun shutdown_callsClearInstance() {
        manager.shutdown()
        assertTrue(clearInstanceCalled)
    }

    @Test
    fun shutdown_shutsDownConnectivityProvider() {
        manager.shutdown()
        verify(mockConnectivityProvider).shutdown()
    }

    @Test
    fun shutdown_shutsDownSessionManager() {
        manager.shutdown()
        verify(mockSessionManager).shutdown()
    }

    @Test
    fun shutdown_closesPublishEventsQueue() {
        manager.shutdown()
        verify(mockPublishEventsQueue).close()
    }

    @Test
    fun shutdown_closesLogger() {
        manager.shutdown()
        verify(mockLogger).close()
    }

    @Test
    fun shutdown_stopsPeriodicLogsPublisher() {
        manager.shutdown()
        verify(mockPeriodicLogsPublisher).stop()
    }

    private fun createManager(): SdkLifecycleManager {
        return SdkLifecycleManager(
            deps = SdkLifecycleManager.Dependencies(
                context = mock(),
                logger = mockLogger,
                taskExecutor = ImmediateSyncTaskExecutor(),
                executor = mockExecutor,
                sessionManager = mockSessionManager,
                connectivityProvider = mockConnectivityProvider,
                publishEventsQueue = mockPublishEventsQueue,
                periodicLogsPublisher = mockPeriodicLogsPublisher,
                anrDetector = mockAnrDetector,
                workerScheduler = mockWorkerScheduler,
                networkErrorLogger = mock(),
                isTracking = isTracking,
                appVersionProvider = mock(),
            ),
            callbacks = SdkLifecycleManager.Callbacks(
                onReconnect = Runnable { },
                retrySendPersistId = { },
                handleNewIntent = { _, _ -> },
                clearInstance = Runnable { clearInstanceCalled = true },
            ),
        )
    }
}
