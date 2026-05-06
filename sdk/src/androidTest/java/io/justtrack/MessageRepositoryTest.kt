package io.justtrack

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.justtrack.database.Database
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.Date

@RunWith(AndroidJUnit4::class)
class MessageRepositoryTest {
    private lateinit var repo: MessageRepositoryImpl
    private lateinit var db: DatabaseInterface
    private val formatter = Formatter

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        db = DatabaseInterface(context, LoggerImpl())
        repo = MessageRepositoryImpl(formatter, db.openMessages(), LoggerImpl())
    }

    @After
    fun closeDb() {
        repo.close()
    }

    @Test
    fun test_storeMessage() = runBlocking {
        val time = Date()
        val datum = LogStoreMessage(
            DTOLogMessage(
                LogLevel.DEBUG,
                "dataName1",
                JSONObject().apply {
                    this.put("key1", "value1")
                },
                time,
            ),
        )

        repo.storeEntity(datum)
        repo.storeEntity(datum)
        val result = db.openMessages().use { it.getAllMessage() }
        Assert.assertEquals(2, result.size)
        Assert.assertEquals(1, result[0].id)
        Assert.assertEquals(2, result[1].id)
        for (entity in result) {
            Assert.assertEquals(LogLevel.DEBUG.name, entity.level)
            Assert.assertEquals(datum.message, entity.message)
            Assert.assertEquals(datum.fields.toString(), entity.fields)
            Assert.assertEquals(datum.timestamp, formatter.parseDate(entity.timestamp))
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_removeByDateMessage() = runBlocking {
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -3)
        }
        val (_, firstAvailableData, _) = prepareDatabase(cutoffDate)

        repo.removeEntitiesByDate((cutoffDate.time.time))
        val result = db.openMessages().use { it.getAllMessage() }
        Assert.assertEquals(1, result.size)
        Assert.assertEquals(firstAvailableData.message, result[0].message)
    }

    @Test
    fun test_fetchNextBatchAndMarkMessage() = runBlocking {
        val (_, firstAvailableData, secondAvailableData) = prepareDatabase(Calendar.getInstance())

        val result = db.openMessages().use { it.getNextBatchAndMarkTransactionMessage() }
            .map { it.transform(formatter, LoggerImpl()) }
        Assert.assertEquals(2, result.size)
        Assert.assertEquals(firstAvailableData.message, result[0].message)
        Assert.assertEquals(firstAvailableData.level, result[0].level)
        Assert.assertEquals(secondAvailableData.message, result[1].message)
        Assert.assertEquals(secondAvailableData.level, result[1].level)
    }

    @Test
    fun test_unmarkMessages() = runBlocking {
        val (processingData, firstAvailableData, secondAvailableData) = prepareDatabase(
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -10)
            },
        )

        repo.unMarkEntitiesById(
            listOf(
                processingData.id,
                secondAvailableData.id,
                firstAvailableData.id,
            ),
        )

        val result = db.openMessages().use { it.getAllMessage() }
        Assert.assertEquals(3, result.size)
        Assert.assertEquals(-1, result[0].processingTimeInMS)
        Assert.assertEquals(-1, result[1].processingTimeInMS)
        Assert.assertEquals(-1, result[2].processingTimeInMS)
    }

    @Test
    fun test_deleteMessages() = runBlocking {
        val (deleteMessage, deleteMessage2, firstAvailableData) = prepareDatabase(Calendar.getInstance())

        repo.deleteEntities(listOf(deleteMessage, deleteMessage2))

        val result = db.openMessages().use { it.getAllMessage() }
        Assert.assertEquals(1, result.size)
        Assert.assertEquals(firstAvailableData.message, result[0].message)
    }

    private suspend fun prepareDatabase(cutoffDate: Calendar): Triple<LogStoreMessage, LogStoreMessage, LogStoreMessage> {
        val processingData = LogStoreMessage(
            1,
            DTOLogMessage(
                LogLevel.DEBUG,
                "processing",
                JSONObject().apply {
                    this.put("test1", "value1")
                },
                Calendar.getInstance().apply {
                    time = cutoffDate.time
                    add(Calendar.DAY_OF_MONTH, -1)
                }.time,
            ),
        )
        repo.storeEntity(processingData)
        repo.fetchNextBatchAndMark()

        val firstAvailableData = LogStoreMessage(
            2,
            DTOLogMessage(
                LogLevel.DEBUG,
                "msg1",
                JSONObject(),
                Calendar.getInstance().apply {
                    time = cutoffDate.time
                    add(Calendar.DAY_OF_MONTH, 1)
                }.time,
            ),
        )

        val secondAvailableData = LogStoreMessage(
            3,
            DTOLogMessage(
                LogLevel.DEBUG,
                "msg2",
                JSONObject().apply {
                    this.put("test1", "value1")
                },
                cutoffDate.time,
            ),
        )

        repo.storeEntities(listOf(firstAvailableData, secondAvailableData))

        return Triple(processingData, firstAvailableData, secondAvailableData)
    }
}
