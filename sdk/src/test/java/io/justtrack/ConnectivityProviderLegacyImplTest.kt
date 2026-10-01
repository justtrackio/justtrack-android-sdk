package io.justtrack

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConnectivityProviderLegacyImplTest {

    private val application: Application = RuntimeEnvironment.getApplication()

    @Test
    fun `init registers broadcast receiver for connectivity changes`() {
        val provider = ConnectivityProviderLegacyImpl(application)
        try {
            val receivers = shadowOf(application).registeredReceivers
                .filter { it.broadcastReceiver === provider }
            Assert.assertEquals(1, receivers.size)
            val actions = receivers[0].intentFilter.actionsIterator().asSequence().toList()
            Assert.assertTrue(actions.contains(ConnectivityManager.CONNECTIVITY_ACTION))
        } finally {
            provider.shutdown()
        }
    }

    @Test
    fun `shutdown unregisters broadcast receiver`() {
        val provider = ConnectivityProviderLegacyImpl(application)
        provider.shutdown()

        val receivers = shadowOf(application).registeredReceivers
            .filter { it.broadcastReceiver === provider }
        Assert.assertTrue(receivers.isEmpty())
    }

    @Test
    fun `onReceive notifies subscribed callback with current connectivity state`() {
        val provider = ConnectivityProviderLegacyImpl(application)
        try {
            val called = AtomicInteger(0)
            val lastValue = AtomicBoolean(false)
            provider.registerOnReconnected(
                object : ConnectivityProvider.ConnectivityCallback {
                    override fun onConnectivityChange(connected: Boolean) {
                        called.incrementAndGet()
                        lastValue.set(connected)
                    }
                },
            )

            // Robolectric default network info exists -> connected == true
            provider.onReceive(application, Intent(ConnectivityManager.CONNECTIVITY_ACTION))

            Assert.assertEquals(1, called.get())
            Assert.assertTrue(lastValue.get())
        } finally {
            provider.shutdown()
        }
    }

    @Test
    fun `onReceive reports disconnected when there is no active network`() {
        val provider = ConnectivityProviderLegacyImpl(application)
        try {
            val connMgr = application.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
            shadowOf(connMgr).setActiveNetworkInfo(null)

            val lastValue = AtomicBoolean(true)
            val called = AtomicInteger(0)
            provider.registerOnReconnected(
                object : ConnectivityProvider.ConnectivityCallback {
                    override fun onConnectivityChange(connected: Boolean) {
                        called.incrementAndGet()
                        lastValue.set(connected)
                    }
                },
            )

            provider.onReceive(application, Intent(ConnectivityManager.CONNECTIVITY_ACTION))

            Assert.assertEquals(1, called.get())
            Assert.assertFalse(lastValue.get())
        } finally {
            provider.shutdown()
        }
    }

    @Test
    fun `onReceive notifies multiple subscribers`() {
        val provider = ConnectivityProviderLegacyImpl(application)
        try {
            val firstCalled = AtomicInteger(0)
            val secondCalled = AtomicInteger(0)
            provider.registerOnReconnected(
                object : ConnectivityProvider.ConnectivityCallback {
                    override fun onConnectivityChange(connected: Boolean) {
                        firstCalled.incrementAndGet()
                    }
                },
            )
            provider.registerOnReconnected(
                object : ConnectivityProvider.ConnectivityCallback {
                    override fun onConnectivityChange(connected: Boolean) {
                        secondCalled.incrementAndGet()
                    }
                },
            )

            provider.onReceive(application, Intent(ConnectivityManager.CONNECTIVITY_ACTION))

            Assert.assertEquals(1, firstCalled.get())
            Assert.assertEquals(1, secondCalled.get())
        } finally {
            provider.shutdown()
        }
    }

    @Test
    fun `unsubscribed callback does not get notified`() {
        val provider = ConnectivityProviderLegacyImpl(application)
        try {
            val called = AtomicInteger(0)
            val subscription = provider.registerOnReconnected(
                object : ConnectivityProvider.ConnectivityCallback {
                    override fun onConnectivityChange(connected: Boolean) {
                        called.incrementAndGet()
                    }
                },
            )
            subscription.unsubscribe()

            provider.onReceive(application, Intent(ConnectivityManager.CONNECTIVITY_ACTION))

            Assert.assertEquals(0, called.get())
        } finally {
            provider.shutdown()
        }
    }

    @Test
    fun `broadcast triggers onReceive and notifies callbacks`() {
        val provider = ConnectivityProviderLegacyImpl(application)
        try {
            val called = AtomicInteger(0)
            provider.registerOnReconnected(
                object : ConnectivityProvider.ConnectivityCallback {
                    override fun onConnectivityChange(connected: Boolean) {
                        called.incrementAndGet()
                    }
                },
            )

            application.sendBroadcast(Intent(ConnectivityManager.CONNECTIVITY_ACTION))
            shadowOf(application.mainLooper).idle()

            Assert.assertEquals(1, called.get())
        } finally {
            provider.shutdown()
        }
    }

    @Test
    fun `connectionType defaults to UNKNOWN`() {
        val provider = ConnectivityProviderLegacyImpl(application)
        try {
            Assert.assertEquals(ConnectionType.UNKNOWN, provider.connectionType)
        } finally {
            provider.shutdown()
        }
    }

    @Test
    fun `connectionType is OFFLINE when no active network`() {
        val provider = ConnectivityProviderLegacyImpl(application)
        try {
            // Shadow has no active network by default
            val shadowCM = shadowOf(
                application.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager,
            )
            shadowCM.setActiveNetworkInfo(null)

            application.sendBroadcast(Intent(ConnectivityManager.CONNECTIVITY_ACTION))
            shadowOf(application.mainLooper).idle()

            Assert.assertEquals(ConnectionType.OFFLINE, provider.connectionType)
        } finally {
            provider.shutdown()
        }
    }
}
