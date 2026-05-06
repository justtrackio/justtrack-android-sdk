package io.justtrack

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
internal class ConnectivityProviderImpl(private val connectivityManager: ConnectivityManager) :
    ConnectivityManager.NetworkCallback(), ConnectivityProvider {
    private val reconnectSubscriptions = SubscriptionManager<ConnectivityProvider.ConnectivityCallback>()
    private var isConnected: Boolean? = null

    init {
        connectivityManager.registerDefaultNetworkCallback(this)
    }

    override fun registerOnReconnected(callback: ConnectivityProvider.ConnectivityCallback): Subscription {
        return reconnectSubscriptions.subscribe(callback)
    }

    override fun shutdown() {
        connectivityManager.unregisterNetworkCallback(this)
    }

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        if (capabilities.hasCapability(NET_CAPABILITY_INTERNET)) {
            if (isConnected == null || isConnected != true) {
                isConnected = true
                reconnectSubscriptions.call {
                    it.onConnectivityChange(true)
                }
            }
        }
    }

    override fun onLost(network: Network) {
        isConnected = false
        reconnectSubscriptions.call {
            it.onConnectivityChange(false)
        }
    }
}
