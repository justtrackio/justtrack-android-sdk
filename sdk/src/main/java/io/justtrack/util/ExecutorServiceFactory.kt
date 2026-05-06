package io.justtrack.util

import io.justtrack.crashes.CrashHandler
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal fun interface ExecutorServiceFactory {
    fun create(crashHandler: CrashHandler): ExecutorService
}

internal class ExecutorServiceFactoryImpl : ExecutorServiceFactory {
    override fun create(crashHandler: CrashHandler): ExecutorService {
        // we want our executor to spawn new threads as needed (because if we depend in one future on another
        // we block that task and if we have no other thread executing the other task, we block forever)
        // up to an arbitrary limit of 50 (so you can chain up to 50 futures, should be enough).
        // workers shut down if there is no work for 30 seconds and we accept an unbounded number of tasks
        val executor = ThreadPoolExecutor(
            CORE_POOL_SIZE,
            CORE_POOL_SIZE,
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            LinkedBlockingDeque(),
        )
        executor.allowCoreThreadTimeOut(true)

        val threadFactory = executor.threadFactory
        executor.threadFactory = ThreadFactory { runnable: Runnable? ->
            val thread = threadFactory.newThread(runnable)
            thread.uncaughtExceptionHandler = crashHandler.getUncaughtExceptionHandler()
            thread
        }
        return executor
    }

    private companion object {
        private const val CORE_POOL_SIZE = 50
        private const val KEEP_ALIVE_SECONDS = 60L
    }
}
