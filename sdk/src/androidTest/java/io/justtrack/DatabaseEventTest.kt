package io.justtrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.database.Database
import io.justtrack.events.Unit
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseEventTest {
    private lateinit var databaseInterface: DatabaseInterface
    private val formatter = Formatter

    private val time = Calendar.getInstance().time

    private val defaultData = UserEventEntity(
        eventId = "eventId1",
        eventName = "name",
        dimensions = "",
        value = 100.0,
        unit = Unit.COUNT.name,
        currency = "currency",
        timestamp = formatter.formatDateMilliseconds(time),
        timestampInMS = time.time,
        sessionId = UUID.randomUUID().toString(),
        sdkVersionMajor = 5,
        sdkVersionMinor = 0,
        sdkVersionPatch = 0,
        sdkVersionName = "5.0.0",
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
    fun test_insert() = runBlocking {
        val sessionId = UUID.randomUUID().toString()

        val data = defaultData.copy(
            sessionId = sessionId,
        )

        databaseInterface.openEvents().use { db ->
            db.insertEvent(data)
            db.insertEvents(listOf(data))

            val result = db.getAllEvent()
            Assert.assertEquals(2, result.size)

            Assert.assertEquals(data.copy(id = 1, sequenceNumber = 0), result[0])
            Assert.assertEquals(data.copy(id = 2, sequenceNumber = 1), result[1])
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_getNextBatch() = runBlocking {
        databaseInterface.openEvents().use { db ->
            val (
                _,
                firstAvailableData,
                secondAvailableData,
                thirdAvailableData,
            ) = prepareDatabase(db)
            val result = db.getAllUnMarkEvent()

            Assert.assertEquals(3, result.size)
            Assert.assertEquals(firstAvailableData.eventId, result[0].eventId)
            Assert.assertEquals(secondAvailableData.eventId, result[1].eventId)
            Assert.assertEquals(thirdAvailableData.eventId, result[2].eventId)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_deleteById() = runBlocking {
        databaseInterface.openEvents().use { db ->
            val (
                processingData,
                firstAvailableData,
                secondAvailableData,
                thirdAvailableData,
            ) = prepareDatabase(db)
            db.deleteByIdEvent(listOf(processingData.id, thirdAvailableData.id))
            val result = db.getAllEvent()
            Assert.assertEquals(2, result.size)
            Assert.assertEquals(firstAvailableData.eventId, result[0].eventId)
            Assert.assertEquals(secondAvailableData.eventId, result[1].eventId)
        }
    }

    //
    @Test
    @Throws(Exception::class)
    fun test_deleteByDate() = runBlocking {
        databaseInterface.openEvents().use { db ->
            val (
                _,
                _,
                _,
                thirdAvailableData,
            ) = prepareDatabase(db)
            db.deleteByDateEvent(time.time - 99)
            val result = db.getAllEvent()
            Assert.assertEquals(1, result.size)
            Assert.assertEquals(thirdAvailableData.eventId, result[0].eventId)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_getNextBatchAndMarkTransaction() = runBlocking {
        databaseInterface.openEvents().use { db ->
            val (
                _,
                firstAvailableData,
                secondAvailableData,
                thirdAvailableData,
            ) = prepareDatabase(db)
            val result = db.getNextBatchAndMarkTransactionEvent(100)
            Assert.assertEquals(3, result.size)
            Assert.assertEquals(firstAvailableData.eventId, result[0].eventId)
            Assert.assertNotEquals(-1, result[0].processingTimeInMS)
            Assert.assertEquals(secondAvailableData.eventId, result[1].eventId)
            Assert.assertNotEquals(-1, result[1].processingTimeInMS)
            Assert.assertEquals(thirdAvailableData.eventId, result[2].eventId)
            Assert.assertNotEquals(-1, result[2].processingTimeInMS)
        }
    }

    private suspend fun prepareDatabase(db: DatabaseEventInterface): DefaultEntities<UserEventEntity> {
        val processingData = defaultData.copy(
            id = 1,
            eventId = "eventId1",
            timestamp = formatter.formatDateMilliseconds(time),
            timestampInMS = time.time - 101,
            processingTimeInMS = System.currentTimeMillis(),
        )

        val firstAvailableData = defaultData.copy(
            id = 2,
            eventId = "eventId2",
            timestamp = formatter.formatDateMilliseconds(time),
            timestampInMS = time.time - 100,
            processingTimeInMS = -1,
        )

        val secondAvailableData = defaultData.copy(
            id = 3,
            eventId = "eventId3",
            timestamp = formatter.formatDateMilliseconds(time),
            timestampInMS = time.time - 99,
            processingTimeInMS = -1,
        )

        val thirdAvailableData = defaultData.copy(
            id = 4,
            eventId = "eventId4",
            timestamp = formatter.formatDateMilliseconds(time),
            timestampInMS = time.time - 98,
            processingTimeInMS = -1,
        )
        db.insertEvents(
            listOf(
                processingData,
                firstAvailableData,
                secondAvailableData,
                thirdAvailableData,
            ),
        )

        return DefaultEntities(
            processingData,
            firstAvailableData,
            secondAvailableData,
            thirdAvailableData,
        )
    }
}
