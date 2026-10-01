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

    @Volatile
    override var connectionType: ConnectionType = ConnectionType.UNKNOWN
        private set

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
            connectionType = mapCapabilities(capabilities)
            if (isConnected == null || isConnected != true) {
                isConnected = true
                reconnectSubscriptions.call { listener -> listener.onConnectivityChange(true) }
            }
        }
    }

    override fun onLost(network: Network) {
        isConnected = false
        connectionType = ConnectionType.OFFLINE
        reconnectSubscriptions.call { listener -> listener.onConnectivityChange(false) }
    }

    private fun mapCapabilities(capabilities: NetworkCapabilities): ConnectionType {
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR_UNKNOWN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> ConnectionType.BLUETOOTH
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
            else -> ConnectionType.UNKNOWN
        }
    }
}
