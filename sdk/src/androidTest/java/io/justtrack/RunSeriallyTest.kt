package io.justtrack

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.log.Logger
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

class RunSeriallyTest {
    private lateinit var context: Context
    private lateinit var httpClient: HttpClient
    private lateinit var executorBuilder: ExecutorServiceFactory

    @Before
    fun createSdk() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        httpClient = object : BaseTestHttpClient() {
            override suspend fun sendAttributionRequest(logger: Logger, body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
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

    @Test
    fun runSeriallyTest(): Unit = runBlocking {
        val sdk = TestSdk(context, executorBuilder, httpClient, true)
        var isFirstTaskComplete = false

        val deferred = CompletableDeferred<Boolean?>()

        val delayedTask = sdk.taskExecutor.executeAsFuture(
            object : Task<Boolean> {
                override suspend fun execute(): Boolean {
                    return false
                }
            },
        )

        val task = sdk.taskExecutor.executeAsFuture(
            object : Task<Boolean> {
                override suspend fun execute(): Boolean {
                    return true
                }
            },
        )

        val callback = object : Callback<Boolean> {
            override fun resolve(response: Boolean) {
                Assert.assertTrue(isFirstTaskComplete)
                deferred.complete(true)
            }

            override fun reject(exception: Throwable) {
                Assert.fail()
                deferred.complete(false)
            }
        }

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
        sdk.runTask(task, 0, callback)

        deferred.await()
    }

    @Test
    fun runParallellyTest(): Unit = runBlocking {
        val sdk = TestSdk(context, executorBuilder, httpClient, false)
        var isFirstTaskComplete = false

        val deferred = CompletableDeferred<Boolean?>()

        val delayedTask = sdk.taskExecutor.executeAsFuture(
            object : Task<Boolean> {
                override suspend fun execute(): Boolean {
                    return false
                }
            },
        )

        val task = sdk.taskExecutor.executeAsFuture(
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
