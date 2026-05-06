package io.justtrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build

@Suppress("DEPRECATION")
internal class ConnectivityProviderLegacyImpl(
    private val context: Context,
) : BroadcastReceiver(), ConnectivityProvider {
    private val reconnectSubscriptions = SubscriptionManager<ConnectivityProvider.ConnectivityCallback>()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                this,
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
                RECEIVER_NOT_EXPORTED,
            )
        } else {
            context.registerReceiver(
                this,
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
            )
        }
    }

    override fun registerOnReconnected(callback: ConnectivityProvider.ConnectivityCallback): Subscription {
        return reconnectSubscriptions.subscribe(callback)
    }

    override fun shutdown() {
        context.unregisterReceiver(this)
    }

    override fun onReceive(c: Context, intent: Intent) {
        val connMgr =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val connected = connMgr.activeNetworkInfo != null
        reconnectSubscriptions.call {
            it.onConnectivityChange(connected)
        }
    }
}
