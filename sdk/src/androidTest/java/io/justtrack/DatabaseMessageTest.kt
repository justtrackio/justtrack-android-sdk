package io.justtrack

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.justtrack.database.Database
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class DatabaseMessageTest {
    private lateinit var databaseInterface: DatabaseInterface
    private val formatter = Formatter

    private val time = Date()

    private val defaultData = LogMessageEntity(
        level = "level",
        message = "message1",
        fields = JSONObject().toString(),
        timestamp = formatter.formatDateMilliseconds(time),
        timestampInMS = time.time,
    )

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        databaseInterface = DatabaseInterface(context, LoggerImpl())
    }

    @Test
    @Throws(Exception::class)
    fun test_insert() = runBlocking {
        val data = defaultData

        databaseInterface.openMessages().use { db ->
            db.insertMessage(data)
            db.insertMessages(listOf(data))

            val result = db.getAllMessage()

            Assert.assertEquals(2, result.size)

            Assert.assertEquals(defaultData.copy(id = 1), result[0])
            Assert.assertEquals(defaultData.copy(id = 2), result[1])
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_getNextBatch() = runBlocking {
        databaseInterface.openMessages().use { db ->
            val (
                _,
                firstAvailableData,
                secondAvailableData,
                thirdAvailableData,
            ) = prepareDatabase(db)

            val result = db.getAllUnMarkMessage()
            Assert.assertEquals(3, result.size)
            Assert.assertEquals(firstAvailableData.message, result[0].message)
            Assert.assertEquals(secondAvailableData.message, result[1].message)
            Assert.assertEquals(thirdAvailableData.message, result[2].message)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_deleteById() = runBlocking {
        databaseInterface.openMessages().use { db ->
            val (
                processingData,
                firstAvailableData,
                secondAvailableData,
                thirdAvailableData,
            ) = prepareDatabase(db)

            db.deleteByIdMessage(listOf(processingData.id, thirdAvailableData.id))
            val result = db.getAllMessage()
            Assert.assertEquals(2, result.size)
            Assert.assertEquals(firstAvailableData.message, result[0].message)
            Assert.assertEquals(secondAvailableData.message, result[1].message)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_deleteByDate() = runBlocking {
        databaseInterface.openMessages().use { db ->
            val (
                _,
                _,
                _,
                thirdAvailableData,
            ) = prepareDatabase(db)

            db.deleteByDateMessage(time.time - 99)
            val result = db.getAllMessage()
            Assert.assertEquals(1, result.size)
            Assert.assertEquals(thirdAvailableData.message, result[0].message)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_getNextBatchAndMarkTransaction() = runBlocking {
        databaseInterface.openMessages().use { db ->
            val (
                _,
                firstAvailableData,
                secondAvailableData,
                thirdAvailableData,
            ) = prepareDatabase(db)

            val result = db.getNextBatchAndMarkTransactionMessage()
            Assert.assertEquals(3, result.size)
            Assert.assertEquals(firstAvailableData.message, result[0].message)
            Assert.assertNotEquals(-1, result[0].processingTimeInMS)
            Assert.assertEquals(secondAvailableData.message, result[1].message)
            Assert.assertNotEquals(-1, result[1].processingTimeInMS)
            Assert.assertEquals(thirdAvailableData.message, result[2].message)
            Assert.assertNotEquals(-1, result[2].processingTimeInMS)
        }
    }

    private suspend fun prepareDatabase(db: DatabaseMessageInterface): DefaultEntities<LogMessageEntity> {
        val processingData = defaultData.copy(
            id = 1,
            message = "msg1",
            timestamp = formatter.formatDateMilliseconds(time),
            timestampInMS = time.time - 101,
            processingTimeInMS = System.currentTimeMillis(),
        )

        val firstAvailableData = defaultData.copy(
            id = 2,
            message = "available1",
            timestamp = formatter.formatDateMilliseconds(time),
            timestampInMS = time.time - 100,
            processingTimeInMS = -1,
        )

        val secondAvailableData = defaultData.copy(
            id = 3,
            message = "available2",
            timestamp = formatter.formatDateMilliseconds(time),
            timestampInMS = time.time - 99,
            processingTimeInMS = -1,
        )

        val thirdAvailableData = defaultData.copy(
            id = 4,
            message = "available3",
            timestamp = formatter.formatDateMilliseconds(time),
            timestampInMS = time.time - 98,
            processingTimeInMS = -1,
        )

        db.insertMessages(
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
