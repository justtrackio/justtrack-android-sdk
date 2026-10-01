package io.justtrack

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.justtrack.database.Database
import io.justtrack.events.JtLoginEvent
import io.justtrack.events.JtProgressionEvent
import io.justtrack.events.JtPurchaseInternalEvent
import io.justtrack.events.Money
import io.justtrack.events.Unit
import io.justtrack.versions.SdkVersionImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.exceptions.verification.WantedButNotInvoked
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class DatabasePublishEventsQueueTest {
    private lateinit var db: DatabaseInterface
    private val formatter = Formatter
    private lateinit var publishEvent: PublishEventsQueue
    private val publishedEvents = ArrayList<StorableEvent>()
    private lateinit var repo: EventRepositoryImpl

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        db = DatabaseInterface(context, LoggerImpl())
        repo = spy(EventRepositoryImpl(formatter, PlatformType.ANDROID, db.openEvents(), LoggerImpl()))
        publishEvent = PublishEventsQueue(
            { events, _ ->
                publishedEvents.addAll(events)
                ValueFuture(events)
            },
            LoggerImpl(),
            NetworkErrorLogger(),
            repo,
            50L,
            AtomicBoolean(true),
            SdkVersionImpl(5, 0, 0, "5.0.0"),
            GlobalDimensionsRepo(
                context.getSharedPreferences(GlobalDimensionsRepo.STORE_NAME, Context.MODE_PRIVATE),
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
    }

    @After
    fun closeDb() {
        publishEvent.close()
    }

    @Test(timeout = 4_000L)
    fun testSendPendingEvents() = runBlocking {
        db.openEvents().use { _ ->
            repo.storeEntities(populateTestData(100))
            Assert.assertEquals(100, repo.getAll().size)
            publishEvent.start(null)

            while (true) {
                try {
                    verify(repo, atLeast(1)).fetchNextBatchAndMark(any())
                    break
                } catch (exception: WantedButNotInvoked) {
                    // Ignore the exception and continue waiting
                }
                delay(100L)
            }

            publishEvent.close()
            publishEvent.waitClosed()

            val unMarkList = repo.getAllUnMark()
            Assert.assertEquals(0, unMarkList.size)
            Assert.assertEquals(100, publishedEvents.size)
        }
    }

    @Test
    fun testShouldNotSentMarkedEvents() = runBlocking {
        db.openEvents().use { _ ->
            repo.storeEntities(populateTestData(100))

            repo.fetchNextBatchAndMark()

            publishEvent.start(null)
            publishEvent.close()
            publishEvent.waitClosed()

            Assert.assertEquals(0, publishedEvents.size)
        }
    }

    @Test
    fun testPublishConcurrently() = runBlocking {
        db.openEvents().use { db ->
            val repo = EventRepositoryImpl(Formatter, PlatformType.ANDROID, db, LoggerImpl())
            val firstAmount = 100
            val secondAmount = 90
            val thirdAmount = 210
            val firstFutures = ArrayList<AsyncFuture<*>>()
            val secondFutures = ArrayList<AsyncFuture<*>>()
            val thirdFutures = ArrayList<AsyncFuture<*>>()

            publishEvent.start(null)

            awaitAll(
                async {
                    for (event in populateTestData(firstAmount)) {
                        firstFutures.add(publishEvent.publishEvent(event.event))
                    }
                },
                async {
                    for (event in populateTestData(secondAmount)) {
                        secondFutures.add(publishEvent.publishEvent(event.event))
                    }
                },
                async {
                    for (event in populateTestData(thirdAmount)) {
                        thirdFutures.add(publishEvent.publishEvent(event.event))
                    }
                },
            )

            publishEvent.close()
            publishEvent.waitClosed()

            Assert.assertEquals(0, repo.getAll().size)
            Assert.assertEquals(firstAmount + secondAmount + thirdAmount, publishedEvents.size)

            for (future in firstFutures) {
                future.await()
            }
            for (future in secondFutures) {
                future.await()
            }
            for (future in thirdFutures) {
                future.await()
            }
        }
    }

    @Test
    fun testDifferentTypeOfEventType() = runBlocking {
        val date = Date()
        db.openEvents().use { db ->
            val events = populateWithMultipleDifferentTypeOfEvent(date)
            val repo = EventRepositoryImpl(Formatter, PlatformType.ANDROID, db, LoggerImpl())
            CoroutineScope(Dispatchers.IO).launch {
                repo.storeEntities(events)
            }.join()

            publishEvent.start(null)
            publishEvent.close()
            publishEvent.waitClosed()

            publishedEvents.sortBy { it.sequenceNumber }

            // Unable to use .equal() directly, events.event.happenedAt is null.
            Assert.assertTrue(publishedEvents[0].event.equalWithoutDate(events[0].event))
            Assert.assertTrue(publishedEvents[1].event.equalWithoutDate(events[1].event))
            Assert.assertTrue(publishedEvents[2].event.equalWithoutDate(events[2].event))

            Assert.assertTrue(publishedEvents[0].eventId == events[0].eventId)
            Assert.assertTrue(publishedEvents[1].eventId == events[1].eventId)
            Assert.assertTrue(publishedEvents[2].eventId == events[2].eventId)

            Assert.assertTrue(publishedEvents[0].event.dimensions["custom_1"].equals("dim1"))
            Assert.assertTrue(publishedEvents[0].event.dimensions["custom_2"].equals("dim2"))
            Assert.assertTrue(publishedEvents[0].event.value.equals(2.0))
            Assert.assertTrue(publishedEvents[0].event.unit!! == Unit.COUNT)

            Assert.assertTrue(publishedEvents[2].event.currency.equals("USD"))
            Assert.assertTrue(publishedEvents[2].event.value.equals(10.0))
        }
    }

    /**
     * publishEvent before publishEvent.start() should not create duplicate events, and the event
     * should be published correctly.
     */
    @Test
    fun publishEventBeforeStart() = runBlocking {
        val testDataCount = 10

        val testEvent = populateTestData(testDataCount).map { it.event }

        testEvent.forEach {
            publishEvent.publishEvent(it)
        }

        publishEvent.start(null)
        publishEvent.close()
        publishEvent.waitClosed()

        for (i in 0 until testDataCount) {
            Assert.assertEquals(testEvent[i].name, publishedEvents[i].event.name)
        }

        Assert.assertEquals(testDataCount, publishedEvents.size)
    }

    private fun populateTestData(amount: Int): List<StorableEvent> {
        val dataList = ArrayList<StorableEvent>()

        for (count in 0L until amount) {
            dataList.add(
                StorableEvent(
                    eventId = UUID.randomUUID(),
                    event = PublishableAppEvent(
                        "name $count",
                        mapOf(),
                        100.0,
                        null,
                        null,
                        "sessionId",
                        SdkVersionImpl(5, 0, 0, "5.0.0"),
                        Date(),
                    ),
                    sequenceNumber = count,
                ),
            )
        }
        return dataList
    }

    private fun populateWithMultipleDifferentTypeOfEvent(date: Date): List<StorableEvent> {
        val dataList = ArrayList<StorableEvent>()
        dataList.add(
            StorableEvent(
                id = 0,
                eventId = UUID.randomUUID(),
                event = JtLoginEvent("success", "fb")
                    .addDimension("custom_1", "dim1")
                    .addDimension("custom_2", "dim2")
                    .setCount(2.0)
                    .build("sessionId", SdkVersionImpl(5, 0, 1, "5.0.1-test1")),
                sequenceNumber = 0,
            ),
        )
        dataList.add(
            StorableEvent(
                id = 1,
                eventId = UUID.randomUUID(),
                event = JtProgressionEvent("fail", "level_42", null, null)
                    .build("sessionId", SdkVersionImpl(5, 1, 0, "5.1.0")),
                sequenceNumber = 1,
            ),
        )
        dataList.add(
            StorableEvent(
                id = 2,
                eventId = UUID.randomUUID(),
                event =
                JtPurchaseInternalEvent(
                    "success",
                    "itemId2",
                    "token2",
                    "inapp",
                    Money(10.0, "USD"),
                    Date(),
                ).build("sessionId", SdkVersionImpl(7, 14, 2, "7.14.2-rc1")),
                sequenceNumber = 2,
            ),
        )
        return dataList
    }
}
