package io.justtrack

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConnectivityProviderImplTest {

    private val connectivityManager: ConnectivityManager = mock(ConnectivityManager::class.java)
    private val network: Network = mock(Network::class.java)

    private fun capabilities(hasInternet: Boolean): NetworkCapabilities {
        val caps = mock(NetworkCapabilities::class.java)
        `when`(caps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(hasInternet)
        return caps
    }

    private fun capabilities(hasInternet: Boolean, transport: Int): NetworkCapabilities {
        val caps = mock(NetworkCapabilities::class.java)
        `when`(caps.hasCapability(NET_CAPABILITY_INTERNET)).thenReturn(hasInternet)
        `when`(caps.hasTransport(transport)).thenReturn(true)
        return caps
    }

    @Test
    fun `init registers default network callback`() {
        val provider = ConnectivityProviderImpl(connectivityManager)

        val captor = ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback::class.java)
        verify(connectivityManager).registerDefaultNetworkCallback(captor.capture())
        assertSame(provider, captor.value)
    }

    @Test
    fun `shutdown unregisters the same network callback`() {
        val provider = ConnectivityProviderImpl(connectivityManager)

        provider.shutdown()

        verify(connectivityManager).unregisterNetworkCallback(provider)
    }

    @Test
    fun `first internet-capable capabilities change notifies subscribers with connected true`() {
        val provider = ConnectivityProviderImpl(connectivityManager)
        val called = AtomicInteger(0)
        var lastValue = false
        provider.registerOnReconnected(
            object : ConnectivityProvider.ConnectivityCallback {
                override fun onConnectivityChange(connected: Boolean) {
                    called.incrementAndGet()
                    lastValue = connected
                }
            },
        )

        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true))

        assertEquals(1, called.get())
        assertTrue(lastValue)
    }

    @Test
    fun `subsequent identical internet-capable capabilities changes are not re-notified`() {
        val provider = ConnectivityProviderImpl(connectivityManager)
        val called = AtomicInteger(0)
        provider.registerOnReconnected(
            object : ConnectivityProvider.ConnectivityCallback {
                override fun onConnectivityChange(connected: Boolean) {
                    called.incrementAndGet()
                }
            },
        )

        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true))
        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true))
        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true))

        // Reconnect should be fired only on the transition into connected state.
        assertEquals(1, called.get())
    }

    @Test
    fun `capabilities change without internet capability does not notify`() {
        val provider = ConnectivityProviderImpl(connectivityManager)
        val called = AtomicInteger(0)
        provider.registerOnReconnected(
            object : ConnectivityProvider.ConnectivityCallback {
                override fun onConnectivityChange(connected: Boolean) {
                    called.incrementAndGet()
                }
            },
        )

        provider.onCapabilitiesChanged(network, capabilities(hasInternet = false))

        assertEquals(0, called.get())
    }

    @Test
    fun `onLost notifies subscribers with connected false`() {
        val provider = ConnectivityProviderImpl(connectivityManager)
        val called = AtomicInteger(0)
        var lastValue = true
        provider.registerOnReconnected(
            object : ConnectivityProvider.ConnectivityCallback {
                override fun onConnectivityChange(connected: Boolean) {
                    called.incrementAndGet()
                    lastValue = connected
                }
            },
        )

        provider.onLost(network)

        assertEquals(1, called.get())
        assertEquals(false, lastValue)
    }

    @Test
    fun `connect-disconnect-reconnect produces three notifications`() {
        val provider = ConnectivityProviderImpl(connectivityManager)
        val events = mutableListOf<Boolean>()
        provider.registerOnReconnected(
            object : ConnectivityProvider.ConnectivityCallback {
                override fun onConnectivityChange(connected: Boolean) {
                    events.add(connected)
                }
            },
        )

        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true))
        provider.onLost(network)
        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true))

        assertEquals(listOf(true, false, true), events)
    }

    @Test
    fun `multiple subscribers all receive notifications`() {
        val provider = ConnectivityProviderImpl(connectivityManager)
        val first = AtomicInteger(0)
        val second = AtomicInteger(0)
        provider.registerOnReconnected(
            object : ConnectivityProvider.ConnectivityCallback {
                override fun onConnectivityChange(connected: Boolean) {
                    first.incrementAndGet()
                }
            },
        )
        provider.registerOnReconnected(
            object : ConnectivityProvider.ConnectivityCallback {
                override fun onConnectivityChange(connected: Boolean) {
                    second.incrementAndGet()
                }
            },
        )

        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true))

        assertEquals(1, first.get())
        assertEquals(1, second.get())
    }

    @Test
    fun `unsubscribed callback does not receive notifications`() {
        val provider = ConnectivityProviderImpl(connectivityManager)
        val called = AtomicInteger(0)
        val subscription = provider.registerOnReconnected(
            object : ConnectivityProvider.ConnectivityCallback {
                override fun onConnectivityChange(connected: Boolean) {
                    called.incrementAndGet()
                }
            },
        )

        subscription.unsubscribe()
        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true))

        assertEquals(0, called.get())
    }

    @Test
    fun `onLost followed by capabilities change re-notifies connected true`() {
        val provider = ConnectivityProviderImpl(connectivityManager)
        val events = mutableListOf<Boolean>()
        provider.registerOnReconnected(
            object : ConnectivityProvider.ConnectivityCallback {
                override fun onConnectivityChange(connected: Boolean) {
                    events.add(connected)
                }
            },
        )

        // Start in lost state.
        provider.onLost(network)
        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true))

        assertEquals(listOf(false, true), events)
    }

    @Test
    fun `connectionType defaults to UNKNOWN`() {
        val provider = ConnectivityProviderImpl(connectivityManager)

        assertEquals(ConnectionType.UNKNOWN, provider.connectionType)
    }

    @Test
    fun `connectionType is WIFI when transport is wifi`() {
        val provider = ConnectivityProviderImpl(connectivityManager)

        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true, transport = NetworkCapabilities.TRANSPORT_WIFI))

        assertEquals(ConnectionType.WIFI, provider.connectionType)
    }

    @Test
    fun `connectionType is CELLULAR_UNKNOWN when transport is cellular`() {
        val provider = ConnectivityProviderImpl(connectivityManager)

        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true, transport = NetworkCapabilities.TRANSPORT_CELLULAR))

        assertEquals(ConnectionType.CELLULAR_UNKNOWN, provider.connectionType)
    }

    @Test
    fun `connectionType is ETHERNET when transport is ethernet`() {
        val provider = ConnectivityProviderImpl(connectivityManager)

        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true, transport = NetworkCapabilities.TRANSPORT_ETHERNET))

        assertEquals(ConnectionType.ETHERNET, provider.connectionType)
    }

    @Test
    fun `connectionType is BLUETOOTH when transport is bluetooth`() {
        val provider = ConnectivityProviderImpl(connectivityManager)

        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true, transport = NetworkCapabilities.TRANSPORT_BLUETOOTH))

        assertEquals(ConnectionType.BLUETOOTH, provider.connectionType)
    }

    @Test
    fun `connectionType is VPN when transport is vpn`() {
        val provider = ConnectivityProviderImpl(connectivityManager)

        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true, transport = NetworkCapabilities.TRANSPORT_VPN))

        assertEquals(ConnectionType.VPN, provider.connectionType)
    }

    @Test
    fun `connectionType is OFFLINE after onLost`() {
        val provider = ConnectivityProviderImpl(connectivityManager)

        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true, transport = NetworkCapabilities.TRANSPORT_WIFI))
        provider.onLost(network)

        assertEquals(ConnectionType.OFFLINE, provider.connectionType)
    }

    @Test
    fun `connectionType updates on reconnect after loss`() {
        val provider = ConnectivityProviderImpl(connectivityManager)

        provider.onLost(network)
        assertEquals(ConnectionType.OFFLINE, provider.connectionType)

        provider.onCapabilitiesChanged(network, capabilities(hasInternet = true, transport = NetworkCapabilities.TRANSPORT_CELLULAR))
        assertEquals(ConnectionType.CELLULAR_UNKNOWN, provider.connectionType)
    }
}
