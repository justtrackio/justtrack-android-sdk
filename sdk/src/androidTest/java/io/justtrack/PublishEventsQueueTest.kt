package io.justtrack

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.api.EventApi
import io.justtrack.database.Database
import io.justtrack.dtos.DTOAppEvent
import io.justtrack.dtos.DTOAppEventEvent
import io.justtrack.events.JtAppInstallEvent
import io.justtrack.events.JtAppOpenEvent
import io.justtrack.events.JtProgressionEvent
import io.justtrack.events.TimeUnitGroup
import io.justtrack.util.ExecutorServiceFactory
import io.justtrack.versions.SdkVersionImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
class PublishEventsQueueTest {
    private lateinit var databaseInterface: DatabaseInterface
    private val logger = LoggerImpl()
    private val formatter = Formatter

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        databaseInterface = DatabaseInterface(context, LoggerImpl())
    }

    @Test
    @Throws(Exception::class)
    fun publishEventsSimple() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val errorList: MutableList<AssertionError> = ArrayList()
        val apis = UserEventValidatingApis(databaseInterface, errorList)
        val executorBuilder = ExecutorServiceFactory {
            val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
            executor.allowCoreThreadTimeOut(true)
            executor
        }

        val sdk = TestSdk(
            context,
            executorBuilder,
            false,
            eventApi = apis,
        )
        sdk.start()

        try {
            val f = sdk.publishEvent(JtAppOpenEvent("sessionId", 1.0, TimeUnitGroup.MILLISECONDS, Date()))
            f.get()
            for (error in errorList) {
                throw error
            }
        } finally {
            sdk.shutdown()
            sdk.publishEventsQueue.waitClosed()
        }
    }

    @Test(timeout = 300_000L)
    @Throws(Exception::class)
    fun publishHugeAmountOfEvents() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val publishedUserEvent = ArrayList<DTOAppEventEvent>()
        val apis = SpamEventApis(publishedUserEvent)
        val executorBuilder = ExecutorServiceFactory {
            val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
            executor.allowCoreThreadTimeOut(true)
            executor
        }
        val totalSpamEvents = 1000

        val sdk = TestSdk(
            context,
            executorBuilder,
            false,
            eventApi = apis,
        )
        sdk.start()
        sdk.publishEventsQueue.maxBatchSize = 50
        val spamEvents = generateSpamEvents(totalSpamEvents)
        try {
            var lastEventPublishingFuture: AsyncFuture<Void?>? = null
            spamEvents.forEach {
                lastEventPublishingFuture = sdk.publishEvent(it)
            }
            while (true) {
                try {
                    lastEventPublishingFuture!![10, TimeUnit.SECONDS]
                    break
                } catch (e: TimeoutException) {
                    Assert.assertNotNull(e)
                    sdk.callReconnectSubscriptions()
                }
            }
            val sortedPublishedEvents = publishedUserEvent.sortedBy { it.sequenceNumber }
            // note: first event is autonomously published when calling sdk.start()
            Assert.assertEquals(totalSpamEvents + 1, publishedUserEvent.size)
            for (index in 0 until spamEvents.size) {
                Assert.assertEquals(spamEvents[index].name, sortedPublishedEvents[index + 1].name)
                Assert.assertEquals(index.toLong(), sortedPublishedEvents[index].sequenceNumber)
            }
        } finally {
            sdk.shutdown()
            sdk.publishEventsQueue.waitClosed()
        }
    }

    @Test(timeout = 300_000L)
    @Throws(Exception::class)
    fun publishEventsWithReallyBadNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val invocations = AtomicInteger()
        // just used as a reference to an integer, could also be a 1-element int array
        val maxBatchSize = AtomicInteger()
        val publishedUserEvent = ArrayList<DTOAppEventEvent>()
        val apis = EventCountingApis(maxBatchSize, invocations, publishedUserEvent)
        val executorBuilder = ExecutorServiceFactory {
            val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
            executor.allowCoreThreadTimeOut(true)
            executor
        }

        val sdk = TestSdk(context, executorBuilder, false, eventApi = apis)
        sdk.start()
        sdk.publishEventsQueue.maxBatchSize = 5
        try {
            // these should be roughly the events we can expect to be published during the first app open
            sdk.publishEvent(JtAppInstallEvent("sessionId", 1.0, TimeUnitGroup.MILLISECONDS, Date()))
            sdk.publishEvent(JtAppOpenEvent("sessionId", 1.0, TimeUnitGroup.MILLISECONDS, Date()))
            sdk.publishEvent(JtProgressionEvent("finish", "level_41", null, null))
            val f = sdk.publishEvent(JtProgressionEvent("start", "level_42", null, null))
            while (true) {
                try {
                    f[10, TimeUnit.SECONDS]
                    break
                } catch (e: TimeoutException) {
                    Assert.assertNotNull(e)
                    sdk.callReconnectSubscriptions()
                }
            }
            Assert.assertEquals(5, maxBatchSize.get())

            Assert.assertEquals(1, publishedUserEvent[1].sequenceNumber)
            Assert.assertEquals("jt_app_install", publishedUserEvent[1].name)

            Assert.assertEquals(2, publishedUserEvent[2].sequenceNumber)
            Assert.assertEquals("jt_app_open", publishedUserEvent[2].name)

            Assert.assertEquals(3, publishedUserEvent[3].sequenceNumber)
            Assert.assertEquals("jt_progression", publishedUserEvent[3].name)

            Assert.assertEquals(4, publishedUserEvent[4].sequenceNumber)
            Assert.assertEquals("jt_progression", publishedUserEvent[4].name)
            // we should never try to publish a batch larger than the number of events we try to publish
        } finally {
            sdk.shutdown()
            sdk.publishEventsQueue.waitClosed()
        }
    }

    @Test
    fun splitVersionTest() = runBlocking {
        val eventBatchOneEventOne = UserEventEntity(
            1,
            eventId = UUID.randomUUID().toString(),
            "eventOne",
            "",
            "session",
            0.0,
            null,
            null,
            Date().time.toString(),
            Date().time,
            sdkVersionMajor = 5,
            sdkVersionMinor = 0,
            sdkVersionPatch = 0,
            sdkVersionName = "5.0.0-rc1",
        )

        val eventBatchOneEventTwo = eventBatchOneEventOne.copy(
            id = 2,
            eventId = UUID.randomUUID().toString(),
            eventName = "eventTwo",
        )

        val eventBatchTwoEventOne = eventBatchOneEventOne.copy(
            id = 3,
            eventId = UUID.randomUUID().toString(),
            eventName = "eventThree",
            sdkVersionMajor = 5,
            sdkVersionMinor = 0,
            sdkVersionPatch = 0,
            sdkVersionName = "5.0.0-rc2",
        )

        val eventBatchThreeEventOne = eventBatchOneEventOne.copy(
            id = 4,
            eventId = UUID.randomUUID().toString(),
            eventName = "eventFour",
            sdkVersionMajor = 5,
            sdkVersionMinor = 0,
            sdkVersionPatch = 1,
            sdkVersionName = "5.0.1",
        )

        val eventBatchTwoEventTwo = eventBatchTwoEventOne.copy(
            id = 5,
            eventId = UUID.randomUUID().toString(),
            eventName = "eventFive",
        )

        databaseInterface.openEvents().use {
            it.insertEvent(eventBatchOneEventOne)
            it.insertEvent(eventBatchOneEventTwo)
            it.insertEvent(eventBatchTwoEventOne)
            it.insertEvent(eventBatchThreeEventOne)
            it.insertEvent(eventBatchTwoEventTwo)
        }
        val publishingEvents = ArrayList<Pair<List<StorableEvent>, Version>>()

        val currentBatchCount = AtomicInteger(0)
        val allBatchSent = CompletableDeferred<Boolean>()

        val publishEventsQueue = PublishEventsQueue(
            { events, eventSdkVersion ->
                Log.e("splitVersionTest", "splitVersionTest: publishingTask with ${eventSdkVersion.name} ${events.size}")
                publishingEvents.add(Pair(events, eventSdkVersion))
                if (currentBatchCount.incrementAndGet() >= 3) {
                    allBatchSent.complete(true)
                }
                ValueFuture(events)
            },
            logger,
            NetworkErrorLogger(),
            EventRepositoryImpl(formatter, PlatformType.ANDROID, databaseInterface.openEvents(), logger),
            2_000L,
            AtomicBoolean(true),
            SdkVersionImpl(7, 0, 0, "7.0.0"),
            GlobalDimensionsRepo(
                ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(
                    GlobalDimensionsRepo.STORE_NAME,
                    Context.MODE_PRIVATE,
                ),
            ),
            connectivityProvider = object : ConnectivityProvider {
                override val connectionType: ConnectionType = ConnectionType.UNKNOWN
                override fun registerOnReconnected(callback: ConnectivityProvider.ConnectivityCallback): Subscription {
                    return object : Subscription {
                        override fun unsubscribe() {}
                    }
                }
                override fun shutdown() {}
            },
        )
        publishEventsQueue.start(null)

        allBatchSent.await()
        val sortedEvents = publishingEvents.sortedBy { it.second.name }

        Assert.assertTrue(sortedEvents.size == 3)
        Assert.assertEquals(2, sortedEvents[0].first.size)
        Assert.assertEquals("5.0.0-rc1", sortedEvents[0].second.name)
        Assert.assertEquals(5, sortedEvents[0].second.major)
        Assert.assertEquals(0, sortedEvents[0].second.minor)
        Assert.assertEquals(0, sortedEvents[0].second.patch)
        Assert.assertEquals("eventOne", sortedEvents[0].first[0].event.name)
        Assert.assertEquals("eventTwo", sortedEvents[0].first[1].event.name)

        Assert.assertEquals(2, sortedEvents[1].first.size)
        Assert.assertEquals("5.0.0-rc2", sortedEvents[1].second.name)
        Assert.assertEquals(5, sortedEvents[1].second.major)
        Assert.assertEquals(0, sortedEvents[1].second.minor)
        Assert.assertEquals(0, sortedEvents[1].second.patch)
        Assert.assertEquals("eventThree", sortedEvents[1].first[0].event.name)
        Assert.assertEquals("eventFive", sortedEvents[1].first[1].event.name)

        Assert.assertEquals(1, sortedEvents[2].first.size)
        Assert.assertEquals("5.0.1", sortedEvents[2].second.name)
        Assert.assertEquals(5, sortedEvents[2].second.major)
        Assert.assertEquals(0, sortedEvents[2].second.minor)
        Assert.assertEquals(1, sortedEvents[2].second.patch)
        Assert.assertEquals("eventFour", sortedEvents[2].first[0].event.name)
    }

    private fun generateSpamEvents(amount: Int): List<AppEvent> {
        val events = ArrayList<AppEvent>()

        for (index in 0 until amount) {
            events.add(AppEvent("test$index"))
        }
        return events
    }

    internal class EventCountingApis(
        private val maxBatchSize: AtomicInteger,
        private val invocations: AtomicInteger,
        private val publishedEvents: ArrayList<DTOAppEventEvent>,
    ) : EventApi {

        override suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?> {
            publishedEvents.addAll(body.events)
            var oldMaxBatchSize: Int
            var newMaxBatchSize: Int
            do {
                oldMaxBatchSize = maxBatchSize.get()
                newMaxBatchSize = max(body.events.size, oldMaxBatchSize)
            } while (!maxBatchSize.compareAndSet(oldMaxBatchSize, newMaxBatchSize))
            if (invocations.incrementAndGet() < 30) {
                return Result.failure(NetworkProblemException(Exception("there was a problem with the network (not telling you what though)")))
            }
            return Result.success(JSONObject())
        }
    }

    internal class SpamEventApis(
        private val publishedEvents: ArrayList<DTOAppEventEvent>,
    ) : EventApi {
        override suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?> {
            delay(100)
            publishedEvents.addAll(body.events)
            return Result.success(JSONObject())
        }
    }

    internal class UserEventValidatingApis constructor(
        val databaseInterface: DatabaseInterface,
        private val errorList: MutableList<AssertionError>,
    ) : EventApi {
        override suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?> {
            try {
                val serializedJson = body.toJSON(Formatter)
                val eventsArray = serializedJson.getJSONArray("events")
                var serializedEvent: JSONObject? = null
                for (index in 0 until eventsArray.length()) {
                    val currentEvent = eventsArray.getJSONObject(index)
                    if (currentEvent.getString("name") == "jt_app_open") {
                        serializedEvent = currentEvent
                        break
                    }
                }

                if (serializedEvent == null) {
                    return Result.success(JSONObject())
                }

                val expectedJson = JSONObject()
                expectedJson.put("id", serializedEvent.getString("id"))
                expectedJson.put("name", "jt_app_open")
                expectedJson.put("value", 1)
                expectedJson.put("unit", "milliseconds")
                expectedJson.put("sessionId", "sessionId")
                expectedJson.put("happenedAt", serializedEvent.getString("happenedAt"))
                expectedJson.put("sequenceNumber", serializedEvent.getLong("sequenceNumber"))
                Assert.assertEquals(expectedJson.toString(), serializedEvent.toString())
            } catch (error: JSONException) {
                // Deadlock-Safety: This is a test.
                synchronized(errorList) {
                    errorList.add(
                        AssertionError(
                            "Unexpected json error",
                            error,
                        ),
                    )
                }
            } catch (error: AssertionError) {
                // Deadlock-Safety: This is a test.
                synchronized(errorList) { errorList.add(error) }
            }
            return Result.success(JSONObject())
        }
    }
}
