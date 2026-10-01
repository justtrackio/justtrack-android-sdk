package io.justtrack.executor

import android.os.Handler
import android.os.HandlerThread

internal interface SerializeHandlerThread {
    fun run(runnable: Runnable)
}

internal class SerializeHandlerThreadImpl : SerializeHandlerThread {
    private val handler: Handler = run {
        val handlerThread = HandlerThread("SerializedCallbackInvokerThread")
        handlerThread.start()
        Handler(handlerThread.looper)
    }

    override fun run(runnable: Runnable) {
        handler.post(runnable)
    }
}
