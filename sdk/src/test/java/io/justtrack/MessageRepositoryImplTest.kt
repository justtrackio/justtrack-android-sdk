package io.justtrack

import io.justtrack.dtos.DTOLogMessage
import io.justtrack.dtos.LogLevel
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
class MessageRepositoryImplTest {
    private lateinit var database: FakeDatabaseMessageInterface
    private lateinit var logger: TestLogger
    private lateinit var repo: MessageRepositoryImpl
    private val formatter = Formatter

    @Before
    fun setUp() {
        database = FakeDatabaseMessageInterface()
        logger = TestLogger()
        repo = MessageRepositoryImpl(formatter, database, logger)
    }

    private fun newStoreMessage(id: Long = -1, message: String = "msg"): LogStoreMessage =
        LogStoreMessage(id, DTOLogMessage(LogLevel.INFO, message, JSONObject().put("k", "v"), Date(1_700_000_000_000L)))

    @Test
    fun `close delegates to database`() {
        repo.close()
        assertTrue(database.closed)
    }

    @Test
    fun `storeEntity delegates and returns id`() = runBlocking {
        database.insertResult = 42L
        val id = repo.storeEntity(newStoreMessage(message = "hi"))
        assertEquals(42L, id)
        assertEquals(1, database.inserted.size)
        assertEquals("hi", database.inserted.single().message)
    }

    @Test
    fun `storeEntity returns null when database returns null`() = runBlocking {
        database.insertResult = null
        assertNull(repo.storeEntity(newStoreMessage()))
    }

    @Test
    fun `storeEntities maps and delegates`() = runBlocking {
        repo.storeEntities(listOf(newStoreMessage(message = "a"), newStoreMessage(message = "b")))
        assertEquals(listOf("a", "b"), database.insertedBatch.single().map { it.message })
    }

    @Test
    fun `removeEntitiesByDate delegates`() = runBlocking {
        repo.removeEntitiesByDate(123L)
        assertEquals(listOf(123L), database.deletedByDate)
    }

    @Test
    fun `fetchNextBatchAndMark delegates and transforms`() = runBlocking {
        database.nextBatch = listOf(LogMessageEntity(newStoreMessage(id = 7, message = "x"), formatter))
        val result = repo.fetchNextBatchAndMark(50)
        assertEquals(listOf(50), database.batchSizes)
        assertEquals(1, result.size)
        assertEquals("x", result.single().message)
    }

    @Test
    fun `deleteEntities with non-empty list delegates ids`() = runBlocking {
        repo.deleteEntities(listOf(newStoreMessage(id = 1), newStoreMessage(id = 2)))
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
        database.allList = listOf(LogMessageEntity(newStoreMessage(id = 1, message = "x"), formatter))
        val result = repo.getAll()
        assertEquals(1, result.size)
        assertEquals("x", result.single().message)
    }

    @Test
    fun `getAllUnMark delegates and transforms`() = runBlocking {
        database.allUnMarkList = listOf(LogMessageEntity(newStoreMessage(id = 1, message = "y"), formatter))
        val result = repo.getAllUnMark()
        assertEquals("y", result.single().message)
    }

    private class FakeDatabaseMessageInterface : DatabaseMessageInterface {
        var closed = false
        var insertResult: Long? = 1L
        val inserted = mutableListOf<LogMessageEntity>()
        val insertedBatch = mutableListOf<List<LogMessageEntity>>()
        val deletedByIds = mutableListOf<List<Long>>()
        val deletedByDate = mutableListOf<Long>()
        val marked = mutableListOf<List<Long>>()
        val unmarked = mutableListOf<List<Long>>()
        val batchSizes = mutableListOf<Int>()
        var nextBatch: List<LogMessageEntity> = emptyList()
        var allList: List<LogMessageEntity> = emptyList()
        var allUnMarkList: List<LogMessageEntity> = emptyList()

        override fun close() {
            closed = true
        }
        override suspend fun insertMessage(message: LogMessageEntity): Long? {
            inserted.add(message)
            return insertResult
        }
        override suspend fun insertMessages(messages: List<LogMessageEntity>): List<Long?> {
            insertedBatch.add(messages)
            return messages.map { 1L }
        }
        override suspend fun deleteByIdMessage(idList: List<Long>): Boolean {
            deletedByIds.add(idList)
            return true
        }
        override suspend fun deleteByDateMessage(cutoffMS: Long): Boolean {
            deletedByDate.add(cutoffMS)
            return true
        }
        override suspend fun markMessage(idList: List<Long>): Boolean {
            marked.add(idList)
            return true
        }
        override suspend fun unMarkMessage(idList: List<Long>): Boolean {
            unmarked.add(idList)
            return true
        }
        override suspend fun getNextBatchAndMarkTransactionMessage(batchSize: Int): List<LogMessageEntity> {
            batchSizes.add(batchSize)
            return nextBatch
        }
        override suspend fun getAllMessage(): List<LogMessageEntity> = allList
        override suspend fun getAllUnMarkMessage(): List<LogMessageEntity> = allUnMarkList
        override suspend fun nukeTableMessage(): Boolean = true
    }
}
