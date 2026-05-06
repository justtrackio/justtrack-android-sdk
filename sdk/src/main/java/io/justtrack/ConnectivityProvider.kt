package io.justtrack

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.os.Build

internal interface ConnectivityProvider {
    fun registerOnReconnected(callback: ConnectivityCallback): Subscription

    fun shutdown()

    interface ConnectivityCallback {
        fun onConnectivityChange(connected: Boolean)
    }

    companion object {
        @JvmStatic
        fun createProvider(context: Context): ConnectivityProvider {
            val cm = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ConnectivityProviderImpl(cm)
            } else {
                ConnectivityProviderLegacyImpl(context)
            }
        }
    }
}
