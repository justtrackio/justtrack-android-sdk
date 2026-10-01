package io.justtrack

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.api.DefaultAttributionApi
import io.justtrack.api.DefaultEventApi
import io.justtrack.database.Database
import io.justtrack.dtos.DTOAppEvent
import io.justtrack.dtos.DTOAppEventEvent
import io.justtrack.util.ExecutorServiceFactory
import kotlinx.coroutines.CompletableDeferred
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

    @Test(timeout = 40_000)
    fun testComplexScenario() = runBlocking {
        val firstAmount = 300
        val secondAmount = 200
        val thirdAmount = 150
        val publishedEvents = ArrayList<DTOAppEventEvent>()
        val appCrashOnAmountPublished = 200
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val successAttributionApi = SuccessAttributionApi()

        val executorBuilder = ExecutorServiceFactory {
            val executor = ThreadPoolExecutor(50, 50, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
            executor.allowCoreThreadTimeOut(true)
            executor
        }

        // WorkingEventApi signals when appCrashOnAmountPublished events have been sent to the server
        val crashSignal = CompletableDeferred<Unit>()
        val publishedAmountReachSignal = CompletableDeferred<Unit>()
        val workingEventApi = WorkingEventApi(
            publishedEvents,
            appCrashOnAmountPublished,
            crashSignal,
            publishedAmountReachSignal,
            firstAmount + secondAmount + thirdAmount,
        )

        var sdk = TestSdk(
            context,
            executorBuilder,
            false,
            attributionApi = successAttributionApi,
            eventApi = workingEventApi,
        )
        sdk.start()
        sdk.publishEventsQueue.maxBatchSize = 50

        // Publish all events concurrently on separate coroutines to stress-test thread safety
        awaitAll(
            async {
                for (index in 0..firstAmount) {
                    sdk.publishEvent(AppEvent("first $index"))
                }
            },
            async {
                for (index in 0..secondAmount) {
                    sdk.publishEvent(AppEvent("second $index"))
                }
            },
            async {
                for (index in 0..thirdAmount) {
                    sdk.publishEvent(AppEvent("third $index"))
                }
            },
        )

        // Wait until appCrashOnAmountPublished events have reached the server, then simulate a crash
        crashSignal.await()
        sdk.shutdown()
        sdk.publishEventsQueue.waitClosed()
        delay(100)
        // Restart SDK — picks up stored-but-unsent events from the database
        sdk = TestSdk(
            context,
            executorBuilder,
            false,
            attributionApi = successAttributionApi,
            eventApi = workingEventApi,
        )
        sdk.start()

        // Give the new SDK time to drain the stored events from the database
        publishedAmountReachSignal.await()
//        delay(15_000)
        // Assert no duplicate sequence numbers and no inconsistent timestamps.
        // Log every event pair for diagnostics.

        Assert.assertTrue(publishedEvents.size >= firstAmount + secondAmount + thirdAmount)
        val sequenceNumbers = HashSet<Long>()
        var duplicateSequenceNumber = false
        var inconsistentTimeStamp = false
        for (i in 1 until publishedEvents.size) {
            val currentEvent = publishedEvents[i]
            val previousEvent = publishedEvents[i - 1]

            if (!sequenceNumbers.add(currentEvent.sequenceNumber)) {
                duplicateSequenceNumber = true
            }

            // A lower sequence number must have an earlier (strictly less) happenedAt.
            // Equal timestamps are allowed for events published in the same millisecond.
            if (currentEvent.sequenceNumber < previousEvent.sequenceNumber &&
                currentEvent.happenedAt > previousEvent.happenedAt
            ) {
                inconsistentTimeStamp = true
            }
        }

        Assert.assertFalse(duplicateSequenceNumber)
        Assert.assertFalse(inconsistentTimeStamp)
    }

    internal class NoConnectionEventApi : DefaultEventApi() {
        override suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?> {
            return Result.failure(Throwable("No Network"))
        }
    }

    internal class SuccessAttributionApi() : DefaultAttributionApi() {
        override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            return Result.success(AttributionTest.testAttribution)
        }
    }

    internal class WorkingEventApi(
        val publishedEvents: ArrayList<DTOAppEventEvent>,
        private val crashOnCount: Int = Int.MAX_VALUE,
        private val crashSignal: CompletableDeferred<Unit>? = null,
        private val publishedAmountReachSignal: CompletableDeferred<Unit>? = null,
        private val totalAmount: Int,
    ) : DefaultEventApi() {
        private val sentCount = AtomicInteger(0)

        override suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?> {
            publishedEvents.addAll(body.events)
            val total = sentCount.addAndGet(body.events.size)
            if (total >= crashOnCount) {
                crashSignal?.complete(Unit)
            }

            if (total >= totalAmount) {
                publishedAmountReachSignal?.complete(Unit)
            }
            return Result.success(JSONObject())
        }
    }
}
