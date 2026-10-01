package io.justtrack

import io.justtrack.events.PublishEventTaskExecutor
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.versions.SdkVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.atomic.AtomicBoolean

internal class ConnectionTrackingTest {
    private val sdkVersion = object : SdkVersion {
        override val platformType: PlatformType = PlatformType.ANDROID
        override val major: Int = 1
        override val minor: Int = 2
        override val patch: Int = 3
        override val name: String = "1.2.3-test"
    }

    private val noOpLogger = object : Logger {
        override val fallback: Logger get() = this
        override fun debug(message: String, vararg fields: LoggerFields) = Unit
        override fun info(message: String, vararg fields: LoggerFields) = Unit
        override fun warn(message: String, vararg fields: LoggerFields) = Unit
        override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit
        override fun error(message: String, vararg fields: LoggerFields) = Unit
        override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit
        override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) = Unit
    }

    private val noOpSessionManager = object : SessionManager {
        override fun start() = Unit
        override fun shutdown() = Unit
        override fun getLatestSessionId(): String = "session-id"
        override fun onResume() = Unit
        override fun onPause() = Unit
        override fun updateSessionTimeStamp() = Unit
    }

    @Test
    fun track_addsOnlineDimensionWhenConnectionTrackingEnabled() {
        val connectivityProvider = FakeConnectivityProvider(ConnectionType.WIFI)
        val queue = newQueue(enableConnectionTracking = true, connectivityProvider = connectivityProvider)

        val event = AppEvent("test_event")
        queue.track(event, noOpSessionManager)

        assertEquals("online", event.dimensions["jt_connection_type"])
    }

    @Test
    fun track_addsOfflineDimensionWhenDeviceIsOffline() {
        val connectivityProvider = FakeConnectivityProvider(ConnectionType.OFFLINE)
        val queue = newQueue(enableConnectionTracking = true, connectivityProvider = connectivityProvider)

        val event = AppEvent("test_event")
        queue.track(event, noOpSessionManager)

        assertEquals("offline", event.dimensions["jt_connection_type"])
    }

    @Test
    fun track_doesNotAddConnectionTypeDimensionWhenTrackingDisabled() {
        val connectivityProvider = FakeConnectivityProvider(ConnectionType.WIFI)
        val queue = newQueue(enableConnectionTracking = false, connectivityProvider = connectivityProvider)

        val event = AppEvent("test_event")
        queue.track(event, noOpSessionManager)

        assertNull(event.dimensions["jt_connection_type"])
    }

    @Test
    fun track_doesNotOverrideExistingConnectionTypeDimension() {
        val connectivityProvider = FakeConnectivityProvider(ConnectionType.WIFI)
        val queue = newQueue(enableConnectionTracking = true, connectivityProvider = connectivityProvider)

        val event = AppEvent("test_event")
        event.dimensions["jt_connection_type"] = "custom_value"
        queue.track(event, noOpSessionManager)

        assertEquals("custom_value", event.dimensions["jt_connection_type"])
    }

    @Test
    fun track_reportsOnlineForCellularConnection() {
        val connectivityProvider = FakeConnectivityProvider(ConnectionType.CELLULAR_UNKNOWN)
        val queue = newQueue(enableConnectionTracking = true, connectivityProvider = connectivityProvider)

        val event = AppEvent("test_event")
        queue.track(event, noOpSessionManager)

        assertEquals("online", event.dimensions["jt_connection_type"])
    }

    @Test
    fun track_reportsOnlineForUnknownConnection() {
        val connectivityProvider = FakeConnectivityProvider(ConnectionType.UNKNOWN)
        val queue = newQueue(enableConnectionTracking = true, connectivityProvider = connectivityProvider)

        val event = AppEvent("test_event")
        queue.track(event, noOpSessionManager)

        assertEquals("online", event.dimensions["jt_connection_type"])
    }

    private fun newQueue(enableConnectionTracking: Boolean, connectivityProvider: ConnectivityProvider): PublishEventsQueue {
        return PublishEventsQueue(
            PublishEventTaskExecutor { events, _ -> ValueFuture(events) },
            noOpLogger,
            NetworkErrorLogger(),
            mock(EventRepository::class.java),
            5L,
            AtomicBoolean(true),
            sdkVersion,
            GlobalDimensionsRepo(EmptySharedPreferences()),
            enableConnectionTracking = enableConnectionTracking,
            connectivityProvider = connectivityProvider,
        )
    }

    private class FakeConnectivityProvider(
        override var connectionType: ConnectionType,
    ) : ConnectivityProvider {
        override fun registerOnReconnected(callback: ConnectivityProvider.ConnectivityCallback): Subscription {
            return object : Subscription {
                override fun unsubscribe() = Unit
            }
        }

        override fun shutdown() = Unit
    }
}
