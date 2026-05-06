package io.justtrack

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.justtrack.database.Database
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class DatabaseMetricTest {
    private lateinit var databaseInterface: DatabaseInterface
    private val formatter = Formatter

    private val time = Date()
    private val defaultData = LogMetricEntity(
        name = "name1",
        value = 100.0,
        dimensions = "",
        unit = "",
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
        databaseInterface.openMetrics().use { db ->
            val data =
                defaultData.copy(
                    name = "name1",
                    timestamp = formatter.formatDateMilliseconds(time),
                    timestampInMS = time.time,
                )

            db.insertMetric(data)
            db.insertMetrics(listOf(data))

            val result = db.getAllMetric()

            Assert.assertEquals(2, result.size)

            Assert.assertEquals(data.copy(id = 1), result[0])
            Assert.assertEquals(data.copy(id = 2), result[1])
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_getNextBatch() = runBlocking {
        databaseInterface.openMetrics().use { db ->
            val (
                _,
                firstAvailableData,
                secondAvailableData,
                thirdAvailableData,
            ) = prepareDatabase(db)

            val result = db.getAllUnMarkMetric()
            Assert.assertEquals(3, result.size)
            Assert.assertEquals(firstAvailableData.name, result[0].name)
            Assert.assertEquals(secondAvailableData.name, result[1].name)
            Assert.assertEquals(thirdAvailableData.name, result[2].name)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_deleteById() = runBlocking {
        databaseInterface.openMetrics().use { db ->
            val (
                processingData,
                firstAvailableData,
                secondAvailableData,
                thirdAvailableData,
            ) = prepareDatabase(db)

            db.deleteByIdMetric(listOf(processingData.id, thirdAvailableData.id))
            val result = db.getAllMetric()
            Assert.assertEquals(2, result.size)
            Assert.assertEquals(firstAvailableData.name, result[0].name)
            Assert.assertEquals(secondAvailableData.name, result[1].name)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_deleteByDate() = runBlocking {
        databaseInterface.openMetrics().use { db ->
            val (
                _,
                _,
                _,
                thirdAvailableData,
            ) = prepareDatabase(db)

            db.deleteByDateMetric(time.time - 99)
            val result = db.getAllMetric()
            Assert.assertEquals(1, result.size)
            Assert.assertEquals(thirdAvailableData.name, result[0].name)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_getNextBatchAndMarkTransaction() = runBlocking {
        databaseInterface.openMetrics().use { db ->
            val (
                _,
                firstAvailableData,
                secondAvailableData,
                thirdAvailableData,
            ) = prepareDatabase(db)

            val result = db.getNextBatchAndMarkTransactionMetric()
            Assert.assertEquals(3, result.size)
            Assert.assertEquals(firstAvailableData.name, result[0].name)
            Assert.assertNotEquals(-1, result[0].processingTimeInMS)
            Assert.assertEquals(secondAvailableData.name, result[1].name)
            Assert.assertNotEquals(-1, result[1].processingTimeInMS)
            Assert.assertEquals(thirdAvailableData.name, result[2].name)
            Assert.assertNotEquals(-1, result[2].processingTimeInMS)
        }
    }

    private suspend fun prepareDatabase(db: DatabaseMetricInterface): DefaultEntities<LogMetricEntity> {
        val processingData = defaultData.copy(
            id = 1,
            name = "name1",
            timestamp = formatter.formatDateMilliseconds(time),
            timestampInMS = time.time - 101,
            processingTimeInMS = System.currentTimeMillis(),
        )

        val firstAvailableData = defaultData.copy(
            id = 2,
            name = "name2",
            timestamp = formatter.formatDateMilliseconds(time),
            timestampInMS = time.time - 100,
            processingTimeInMS = -1,
        )

        val secondAvailableData = defaultData.copy(
            id = 3,
            name = "name3",
            timestamp = formatter.formatDateMilliseconds(time),
            timestampInMS = time.time - 99,
            processingTimeInMS = -1,
        )

        val thirdAvailableData = defaultData.copy(
            id = 4,
            name = "name4",
            timestamp = formatter.formatDateMilliseconds(time),
            timestampInMS = time.time - 98,
            processingTimeInMS = -1,
        )

        db.insertMetrics(
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
