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

@RunWith(AndroidJUnit4::class)
class MetricRepositoryTest {
    private lateinit var repo: MetricRepositoryImpl
    private lateinit var db: DatabaseInterface
    private val formatter = Formatter

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        db = DatabaseInterface(context, LoggerImpl())
        repo = MetricRepositoryImpl(formatter, db.openMetrics(), LoggerImpl())
    }

    @After
    fun closeDb() {
        repo.close()
    }

    @Test
    fun test_storeMetric() = runBlocking {
        val time = Calendar.getInstance().time
        val datum = LogStoreMetric(
            DTOLogMetric(
                "metric1",
                JSONObject().apply {
                    this.put("key1", "value1")
                },
                100.0,
                "unit1",
                time,
            ),
        )

        repo.storeEntity(datum)
        repo.storeEntity(datum)
        val result = db.openMetrics().use { it.getAllMetric() }
        Assert.assertEquals(2, result.size)
        Assert.assertEquals(1, result[0].id)
        Assert.assertEquals(2, result[1].id)
        for (entity in result) {
            Assert.assertEquals(datum.metric, entity.name)
            Assert.assertEquals(datum.dimensions.toString(), entity.dimensions)
            Assert.assertEquals(datum.unit, entity.unit)
            Assert.assertEquals(datum.timestamp, formatter.parseDate(entity.timestamp))
        }
    }

    @Test
    @Throws(Exception::class)
    fun test_removeByDateMetric() = runBlocking {
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -3)
        }
        val (_, firstAvailableData, _) = prepareDatabase(cutoffDate)

        repo.removeEntitiesByDate((cutoffDate.time.time))
        val result = db.openMetrics().use { it.getAllMetric() }
        Assert.assertEquals(1, result.size)
        Assert.assertEquals(firstAvailableData.metric, result[0].name)
    }

    @Test
    fun test_fetchNextBatchAndMarkMetric() = runBlocking {
        val (_, firstAvailableData, secondAvailableData) = prepareDatabase(Calendar.getInstance())

        val result = db.openMetrics().use { it.getNextBatchAndMarkTransactionMetric() }
        Assert.assertEquals(2, result.size)
        Assert.assertEquals(firstAvailableData.metric, result[0].name)
        Assert.assertEquals(secondAvailableData.metric, result[1].name)
    }

    @Test
    fun test_unmarkMetric() = runBlocking {
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

        val result = db.openMetrics().use { it.getAllMetric() }
        Assert.assertEquals(3, result.size)
        Assert.assertEquals(-1, result[0].processingTimeInMS)
        Assert.assertEquals(-1, result[1].processingTimeInMS)
        Assert.assertEquals(-1, result[2].processingTimeInMS)
    }

    @Test
    fun test_deleteMetric() = runBlocking {
        val (deleteMetric, deleteMetric2, firstAvailableData) = prepareDatabase(Calendar.getInstance())

        repo.deleteEntities(listOf(deleteMetric, deleteMetric2))

        val result = db.openMetrics().use { it.getAllMetric() }
        Assert.assertEquals(1, result.size)
        Assert.assertEquals(firstAvailableData.metric, result[0].name)
    }

    private suspend fun prepareDatabase(cutoffDate: Calendar): Triple<LogStoreMetric, LogStoreMetric, LogStoreMetric> {
        val processingData = LogStoreMetric(
            1,
            DTOLogMetric(
                "processing",
                JSONObject(),
                100.0,
                "unit",
                Calendar.getInstance().apply {
                    time = cutoffDate.time
                    add(Calendar.DAY_OF_MONTH, -1)
                }.time,
            ),
        )
        repo.storeEntity(processingData)
        repo.fetchNextBatchAndMark()

        val firstAvailableData = LogStoreMetric(
            2,
            DTOLogMetric(
                "metric1",
                JSONObject(),
                100.0,
                "unit",
                Calendar.getInstance().apply {
                    time = cutoffDate.time
                    add(Calendar.DAY_OF_MONTH, 1)
                }.time,
            ),
        )

        val secondAvailableData = LogStoreMetric(
            3,
            DTOLogMetric(
                "metric2",
                JSONObject(),
                100.0,
                "unit",
                cutoffDate.time,
            ),
        )

        repo.storeEntities(listOf(firstAvailableData, secondAvailableData))

        return Triple(processingData, firstAvailableData, secondAvailableData)
    }
}
