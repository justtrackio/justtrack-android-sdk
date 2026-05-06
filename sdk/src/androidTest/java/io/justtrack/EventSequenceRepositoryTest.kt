package io.justtrack

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.database.Database
import io.justtrack.log.Logger
import io.justtrack.util.ExecutorServiceFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class EventSequenceRepositoryTest {

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
    }

    @Test(timeout = 30_000)
    fun testComplexScenario() = runBlocking {
        val firstAmount = 300
        val secondAmount = 200
        val thirdAmount = 150
        val publishedEvents = ArrayList<DTOAppEventEvent>()
        val appCrashOnAmountPublished = 200
        var currentPublished = 0
        val publishingEventFuture: ArrayList<AsyncFuture<Void>> = ArrayList()
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val httpClient: HttpClient = WorkingHttpClient(publishedEvents)
        val executorBuilder = ExecutorServiceFactory {
            val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
            executor.allowCoreThreadTimeOut(true)
            executor
        }

        var sdk = TestSdk(context, executorBuilder, httpClient, false)
        sdk.start()

        sdk.publishEventsQueue.maxBatchSize = 50
        // publish events in parallel
        awaitAll(
            async {
                for (index in 0..firstAmount) {
                    publishingEventFuture.add(sdk.publishEvent(AppEvent("first $index")))
                }
            },
            async {
                for (index in 0..secondAmount) {
                    publishingEventFuture.add(sdk.publishEvent(AppEvent("second $index")))
                }
            },
            async {
                for (index in 0..thirdAmount) {
                    publishingEventFuture.add(sdk.publishEvent(AppEvent("third $index")))
                }
            },
        )

        publishingEventFuture.forEach {
            it.await()
            currentPublished++
            if (currentPublished == appCrashOnAmountPublished) {
                // shutdown and restart sdk
                sdk.shutdown()
                sdk.publishEventsQueue.waitClosed()
                delay(100)
                sdk = TestSdk(context, executorBuilder, httpClient, false)
            }
        }

        // check if all events published have no duplicate sequenceNumber
        val sequenceNumbers = HashSet<Long>()
        var duplicateSequenceNumber = false
        var inconsistentTimeStamp = false
        for (i in 1 until publishedEvents.size) {
            val currentEvent = publishedEvents[i]
            val previousEvent = publishedEvents[i - 1]

            if (!sequenceNumbers.add(currentEvent.sequenceNumber)) {
                duplicateSequenceNumber = true
                println("Error: Duplicate sequence number found: ${currentEvent.sequenceNumber}")
            }

            if (currentEvent.sequenceNumber < previousEvent.sequenceNumber &&
                currentEvent.happenedAt >= previousEvent.happenedAt
            ) {
                inconsistentTimeStamp = true
                println("Error: Inconsistent timestamp for sequence numbers: ${currentEvent.sequenceNumber} and ${previousEvent.sequenceNumber}")
            }
        }

        Assert.assertFalse(duplicateSequenceNumber)
        Assert.assertFalse(inconsistentTimeStamp)
    }

    internal class NoConnectionHttpClient() : BaseTestHttpClient() {
        override suspend fun sendAttributionRequest(logger: Logger, body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            return Result.failure(Exception("not implemented"))
        }

        override suspend fun sendUserEvents(
            logger: Logger,
            body: DTOAppEvent,
            advertiserId: String?,
            uuid: String,
            installId: String,
        ): Result<JSONObject?> {
            return Result.failure(Throwable("No Network"))
        }
    }

    internal class WorkingHttpClient(val publishedEvents: ArrayList<DTOAppEventEvent>) : BaseTestHttpClient() {
        private val countToFail = AtomicInteger(0)
        override suspend fun sendAttributionRequest(logger: Logger, body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            return Result.failure(Exception("not implemented"))
        }

        override suspend fun sendUserEvents(
            logger: Logger,
            body: DTOAppEvent,
            advertiserId: String?,
            uuid: String,
            installId: String,
        ): Result<JSONObject?> {
            if (countToFail.getAndIncrement() % 3 == 0) {
                return Result.failure(Throwable("No Network"))
            } else {
                publishedEvents.addAll(body.events)
                return Result.success(JSONObject())
            }
        }
    }
}
