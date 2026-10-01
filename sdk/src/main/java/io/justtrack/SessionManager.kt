package io.justtrack

internal interface SessionManager {
    fun start()

    fun shutdown()

    fun getLatestSessionId(): String

    fun onResume()

    fun onPause()

    fun updateSessionTimeStamp()
}
