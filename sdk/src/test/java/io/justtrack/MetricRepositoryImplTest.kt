package io.justtrack

import io.justtrack.dtos.DTOLogMetric
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class MetricRepositoryImplTest {
    private lateinit var database: FakeDatabaseMetricInterface
    private lateinit var logger: TestLogger
    private lateinit var repo: MetricRepositoryImpl
    private val formatter = Formatter

    @Before
    fun setUp() {
        database = FakeDatabaseMetricInterface()
        logger = TestLogger()
        repo = MetricRepositoryImpl(formatter, database, logger)
    }

    private fun newStoreMetric(id: Long = -1, name: String = "m"): LogStoreMetric =
        LogStoreMetric(id, DTOLogMetric(name, JSONObject().put("k", "v"), 1.0, "ms", Date(1_700_000_000_000L)))

    @Test
    fun `close delegates to database`() {
        repo.close()
        assertTrue(database.closed)
    }

    @Test
    fun `storeEntity delegates and returns id`() = runBlocking {
        database.insertResult = 99L
        val id = repo.storeEntity(newStoreMetric(name = "hi"))
        assertEquals(99L, id)
        assertEquals("hi", database.inserted.single().name)
    }

    @Test
    fun `storeEntity returns null when database returns null`() = runBlocking {
        database.insertResult = null
        assertNull(repo.storeEntity(newStoreMetric()))
    }

    @Test
    fun `storeEntities maps and delegates`() = runBlocking {
        repo.storeEntities(listOf(newStoreMetric(name = "a"), newStoreMetric(name = "b")))
        assertEquals(listOf("a", "b"), database.insertedBatch.single().map { it.name })
    }

    @Test
    fun `removeEntitiesByDate delegates`() = runBlocking {
        repo.removeEntitiesByDate(123L)
        assertEquals(listOf(123L), database.deletedByDate)
    }

    @Test
    fun `fetchNextBatchAndMark delegates and transforms`() = runBlocking {
        database.nextBatch = listOf(LogMetricEntity(newStoreMetric(id = 7, name = "x"), formatter))
        val result = repo.fetchNextBatchAndMark(25)
        assertEquals(listOf(25), database.batchSizes)
        assertEquals("x", result.single().metric)
    }

    @Test
    fun `deleteEntities with non-empty list delegates ids`() = runBlocking {
        repo.deleteEntities(listOf(newStoreMetric(id = 1), newStoreMetric(id = 2)))
        assertEquals(listOf(listOf(1L, 2L)), database.deletedByIds)
    }

    @Test
    fun `deleteEntities with empty list does not call database`() = runBlocking {
        repo.deleteEntities(emptyList())
        assertTrue(database.deletedByIds.isEmpty())
    }

    @Test
    fun `markEntitiesById delegates`() = runBlocking {
        repo.markEntitiesById(listOf(3L, 4L))
        assertEquals(listOf(listOf(3L, 4L)), database.marked)
    }

    @Test
    fun `unMarkEntitiesById delegates`() = runBlocking {
        repo.unMarkEntitiesById(listOf(5L))
        assertEquals(listOf(listOf(5L)), database.unmarked)
    }

    @Test
    fun `getAll delegates and transforms`() = runBlocking {
        database.allList = listOf(LogMetricEntity(newStoreMetric(id = 1, name = "x"), formatter))
        assertEquals("x", repo.getAll().single().metric)
    }

    @Test
    fun `getAllUnMark delegates and transforms`() = runBlocking {
        database.allUnMarkList = listOf(LogMetricEntity(newStoreMetric(id = 1, name = "y"), formatter))
        assertEquals("y", repo.getAllUnMark().single().metric)
    }

    private class FakeDatabaseMetricInterface : DatabaseMetricInterface {
        var closed = false
        var insertResult: Long? = 1L
        val inserted = mutableListOf<LogMetricEntity>()
        val insertedBatch = mutableListOf<List<LogMetricEntity>>()
        val deletedByIds = mutableListOf<List<Long>>()
        val deletedByDate = mutableListOf<Long>()
        val marked = mutableListOf<List<Long>>()
        val unmarked = mutableListOf<List<Long>>()
        val batchSizes = mutableListOf<Int>()
        var nextBatch: List<LogMetricEntity> = emptyList()
        var allList: List<LogMetricEntity> = emptyList()
        var allUnMarkList: List<LogMetricEntity> = emptyList()

        override fun close() {
            closed = true
        }
        override suspend fun insertMetric(metric: LogMetricEntity): Long? {
            inserted.add(metric)
            return insertResult
        }
        override suspend fun insertMetrics(metrics: List<LogMetricEntity>): List<Long?> {
            insertedBatch.add(metrics)
            return metrics.map { 1L }
        }
        override suspend fun deleteByIdMetric(idList: List<Long>): Boolean {
            deletedByIds.add(idList)
            return true
        }
        override suspend fun deleteByDateMetric(cutoffMS: Long): Boolean {
            deletedByDate.add(cutoffMS)
            return true
        }
        override suspend fun markMetric(idList: List<Long>): Boolean {
            marked.add(idList)
            return true
        }
        override suspend fun unMarkMetric(idList: List<Long>): Boolean {
            unmarked.add(idList)
            return true
        }
        override suspend fun getNextBatchAndMarkTransactionMetric(batchSize: Int): List<LogMetricEntity> {
            batchSizes.add(batchSize)
            return nextBatch
        }
        override suspend fun getAllMetric(): List<LogMetricEntity> = allList
        override suspend fun getAllUnMarkMetric(): List<LogMetricEntity> = allUnMarkList
        override suspend fun nukeTableMetric(): Boolean = true
    }
}
