package io.justtrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.database.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.Date
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseInterfaceTest {
    private lateinit var databaseInterface: DatabaseInterface
    private val formatter = Formatter

    private val time = Calendar.getInstance().time

    private val defaultEvent = UserEventEntity(
        eventId = "eventId1",
        eventName = "name",
        dimensions = "",
        value = 100.0,
        unit = "",
        currency = "currency",
        timestamp = formatter.formatDateMilliseconds(time),
        timestampInMS = time.time,
        sessionId = UUID.randomUUID().toString(),
        sdkVersionMajor = 5,
        sdkVersionMinor = 10,
        sdkVersionPatch = 18,
        sdkVersionName = "5.10.18",
    )

    private val defaultMessage = LogMessageEntity(
        level = "level",
        message = "message1",
        fields = JSONObject().toString(),
        timestamp = formatter.formatDateMilliseconds(time),
        timestampInMS = time.time,
    )

    private val defaultMetric = LogMetricEntity(
        name = "name1",
        value = 100.0,
        dimensions = "",
        unit = "",
        timestamp = formatter.formatDateMilliseconds(time),
        timestampInMS = time.time,
    )

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        databaseInterface = DatabaseInterface(context, LoggerImpl())
    }

    @Test
    @Throws(Exception::class)
    fun test_open() = runBlocking {
        val someEvent = UserEventEntity(
            eventId = UUID(0, 0).toString(),
            eventName = "name",
            dimensions = "",
            value = 100.0,
            unit = null,
            currency = "currency",
            timestamp = formatter.formatDateMilliseconds(Date()),
            timestampInMS = Date().time,
            sessionId = UUID.randomUUID().toString(),
            sdkVersionMajor = 12,
            sdkVersionMinor = 12,
            sdkVersionPatch = 12,
            sdkVersionName = "12.12.12",
        )
        var insertResult: Pair<Long, Long>?
        databaseInterface.openEvents().use {
            insertResult = it.insertEvent(someEvent)
        }
        // open and close the database many times - if we manage to write to
        // the wrong channel, a database operation should eventually fail
        for (i in 0..100) {
            databaseInterface.openEvents().use {
                // nop
            }
            databaseInterface.openMessages().use {
                // nop
            }
            databaseInterface.openMetrics().use {
                // nop
            }
            databaseInterface.openEvents().use {
                val events = it.getAllEvent()
                Assert.assertEquals(1, events.size)
                Assert.assertEquals(someEvent.copy(sequenceNumber = insertResult!!.second), events[0])
            }
        }
    }

    @Test
    fun insertAndRetrieveMessage() = runBlocking {
        databaseInterface.openMessages().use { databaseMessageInterface ->
            val message = defaultMessage
            val messageId = databaseMessageInterface.insertMessage(message)
            val retrievedMessage =
                databaseMessageInterface.getAllMessage().find { it.id == messageId }
            Assert.assertEquals(message, retrievedMessage)
        }
    }

    @Test
    fun insertAndRetrieveMetric() = runBlocking {
        databaseInterface.openMetrics().use { databaseMetricInterface ->
            val metric = defaultMetric
            val metricId = databaseMetricInterface.insertMetric(metric)
            val retrievedMetric = databaseMetricInterface.getAllMetric().find { it.id == metricId }
            Assert.assertEquals(metric, retrievedMetric)
        }
    }

    @Test
    fun insertAndRetrieveEvent() = runBlocking {
        databaseInterface.openEvents().use { databaseEventInterface ->
            val event = defaultEvent
            val insertResult = databaseEventInterface.insertEvent(event)
            val retrievedEvent = databaseEventInterface.getAllEvent().find { it.id == insertResult!!.first }
            Assert.assertEquals(event.copy(sequenceNumber = insertResult!!.second), retrievedEvent)
        }
    }

    @Test
    fun insertDifferentDataTypeConcurrently() = runBlocking {
        databaseInterface.openMessages().use { databaseMessageInterface ->
            databaseInterface.openMetrics().use { databaseMetricInterface ->
                databaseInterface.openEvents().use { databaseEventInterface ->
                    val messageCount = 100
                    val metricCount = 80
                    val eventCount = 100
                    awaitAll(
                        async {
                            for (message in populateMessage(messageCount)) {
                                databaseMessageInterface.insertMessage(message)
                            }
                        },
                        async {
                            for (message in populateMessage(messageCount)) {
                                databaseMessageInterface.insertMessage(message)
                            }
                            val idList =
                                databaseMessageInterface.getNextBatchAndMarkTransactionMessage(100)
                                    .map { it.id }
                            databaseMessageInterface.deleteByIdMessage(idList)
                        },
                        async {
                            for (metric in populateMetric(metricCount)) {
                                databaseMetricInterface.insertMetric(metric)
                            }
                        },
                        async {
                            for (event in populateEvent(eventCount)) {
                                databaseEventInterface.insertEvent(event)
                            }
                        },
                    )

                    Assert.assertEquals(messageCount, databaseMessageInterface.getAllMessage().size)
                    Assert.assertEquals(metricCount, databaseMetricInterface.getAllMetric().size)
                    Assert.assertEquals(eventCount, databaseEventInterface.getAllEvent().size)
                }
            }
        }
    }

    private fun populateMessage(amount: Int): List<LogMessageEntity> {
        val dataList = ArrayList<LogMessageEntity>()
        for (count in 0 until amount) {
            dataList.add(defaultMessage.copy(message = "message $amount"))
        }
        return dataList
    }

    private fun populateMetric(amount: Int): List<LogMetricEntity> {
        val dataList = ArrayList<LogMetricEntity>()
        for (count in 0 until amount) {
            dataList.add(defaultMetric.copy(name = "metric $amount"))
        }
        return dataList
    }

    private fun populateEvent(amount: Int): List<UserEventEntity> {
        val dataList = ArrayList<UserEventEntity>()
        for (count in 0 until amount) {
            dataList.add(defaultEvent.copy(eventName = "event $amount"))
        }
        return dataList
    }

    @Test
    fun singletonConcurrentInsertFromMultipleThreads() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val threadCount = 10
        val insertsPerThread = 50

        val exceptions = mutableListOf<Exception>()

        // Each coroutine runs on a different IO thread, gets the singleton instance, and inserts data
        val jobs = (0 until threadCount).map { threadIndex ->
            async(Dispatchers.IO) {
                try {
                    val db = DatabaseInterface.getInstance(context, LoggerImpl())
                    db.openMessages().use { messageDb ->
                        for (i in 0 until insertsPerThread) {
                            messageDb.insertMessage(
                                defaultMessage.copy(message = "thread-$threadIndex-msg-$i"),
                            )
                        }
                    }
                } catch (e: Exception) {
                    synchronized(exceptions) {
                        exceptions.add(e)
                    }
                }
            }
        }

        jobs.awaitAll()

        Assert.assertTrue(
            "Expected no exceptions but got: ${exceptions.map { it.message }}",
            exceptions.isEmpty(),
        )

        // Verify all inserts are present
        val expectedTotal = threadCount * insertsPerThread
        databaseInterface.openMessages().use { messageDb ->
            val allMessages = messageDb.getAllMessage()
            Assert.assertEquals(expectedTotal, allMessages.size)
        }
    }
}
