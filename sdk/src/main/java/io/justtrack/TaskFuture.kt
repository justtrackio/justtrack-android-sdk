package io.justtrack

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class TaskFuture<V> internal constructor(task: Task<V>) :
    FutureTask<V>(
        Callable {
            runBlocking {
                task.execute()
            }
        },
    ),
    Future<V> {
    @JvmName("execute")
    internal fun execute() {
        CoroutineScope(Dispatchers.IO).launch {
            run()
        }
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    override fun get(): V {
        checkNotMainThread()
        return super.get()
    }

    @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
    override fun get(timeout: Long, unit: TimeUnit): V {
        checkNotMainThread()
        return super.get(timeout, unit)
    }

    private fun checkNotMainThread() {
        // Reading the Google Advertiser Id does not resolve if we block the main thread.
        // Therefore we forbid awaiting a future on the main thread because such a future could
        // be waiting for the main thread.
        require(!ThreadUtils.isMainThread()) { "Must not be called on the main application thread" }
    }
}
