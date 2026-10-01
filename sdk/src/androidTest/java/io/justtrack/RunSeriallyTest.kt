package io.justtrack

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.api.AttributionApi
import io.justtrack.api.DefaultAttributionApi
import io.justtrack.util.ExecutorServiceFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RunSeriallyTest {
    private lateinit var context: Context
    private lateinit var attributionApi: AttributionApi
    private lateinit var executorBuilder: ExecutorServiceFactory

    @Before
    fun createSdk() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        attributionApi = object : DefaultAttributionApi() {
            override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
                return Result.success(AttributionTest.testAttribution)
            }
        }
        executorBuilder = ExecutorServiceFactory {
            val executor =
                ThreadPoolExecutor(
                    10,
                    10,
                    60L,
                    TimeUnit.SECONDS,
                    LinkedBlockingDeque(),
                )
            executor.allowCoreThreadTimeOut(true)
            executor
        }
    }

    /**
     * Verifies that enabling serial callbacks prevents concurrent callback execution.
     * Callback invocation order is not guaranteed because tasks may complete in parallel.
     */
    @Test
    fun runSeriallyTest(): Unit = runBlocking {
        val sdk = TestSdk(context, executorBuilder, true, attributionApi = attributionApi)

        val deferred = CompletableDeferred<Boolean?>()
        val runningCallbacks = AtomicInteger(0)
        val completedCallbacks = AtomicInteger(0)
        val concurrentCallbackDetected = AtomicBoolean(false)

        val taskExecutor = sdk.taskExecutor

        val delayedTask = taskExecutor.executeFuture(
            object : Task<Boolean> {
                override suspend fun execute(): Boolean {
                    return false
                }
            },
        )

        val task = taskExecutor.executeFuture(
            object : Task<Boolean> {
                override suspend fun execute(): Boolean {
                    return true
                }
            },
        )

        val callback = object : Callback<Boolean> {
            override fun resolve(response: Boolean) {
                if (runningCallbacks.incrementAndGet() > 1) {
                    concurrentCallbackDetected.set(true)
                }

                Thread.sleep(10)

                runningCallbacks.decrementAndGet()
                if (completedCallbacks.incrementAndGet() == 2) {
                    deferred.complete(!concurrentCallbackDetected.get())
                }
            }

            override fun reject(exception: Throwable) {
                Assert.fail()
                deferred.complete(false)
            }
        }

        sdk.runTask(
            delayedTask,
            0,
            callback,
        )
        sdk.runTask(task, 0, callback)

        Assert.assertTrue(deferred.await()!!)
    }

    @Test
    fun runParallellyTest(): Unit = runBlocking {
        val sdk = TestSdk(context, executorBuilder, false, attributionApi = attributionApi)
        var isFirstTaskComplete = false

        val deferred = CompletableDeferred<Boolean?>()

        val taskExecutor = sdk.taskExecutor

        val delayedTask = taskExecutor.executeFuture(
            object : Task<Boolean> {
                override suspend fun execute(): Boolean {
                    return false
                }
            },
        )

        val task = taskExecutor.executeFuture(
            object : Task<Boolean> {
                override suspend fun execute(): Boolean {
                    return true
                }
            },
        )

        sdk.runTask(
            delayedTask,
            10,
            object : Callback<Boolean> {
                override fun resolve(response: Boolean) {
                    isFirstTaskComplete = true
                }

                override fun reject(exception: Throwable) {
                    isFirstTaskComplete = true
                }
            },
        )
        sdk.runTask(
            task,
            0,
            object : Callback<Boolean> {
                override fun resolve(response: Boolean) {
                    Assert.assertFalse(isFirstTaskComplete)
                    deferred.complete(true)
                }

                override fun reject(exception: Throwable) {
                    Assert.fail()
                    deferred.complete(false)
                }
            },
        )

        deferred.await()
    }
}
