package io.justtrack

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.justtrack.database.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.validateMockitoUsage
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

class LogAggregatorImplMetricTest {
    private lateinit var logAgg: LogAggregatorImpl
    private lateinit var db: DatabaseInterface
    private lateinit var context: Context
    private val formatter = Formatter
    private lateinit var coroutineScope: CoroutineScope

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        db = DatabaseInterface(context, LoggerImpl())
        val msgRepo = MessageRepositoryImpl(formatter, db.openMessages(), LoggerImpl())
        val metricRepo = MetricRepositoryImpl(formatter, db.openMetrics(), LoggerImpl())
        logAgg = LogAggregatorImpl(msgRepo, metricRepo, Dispatchers.IO, LoggerImpl(), NetworkErrorLogger(), AtomicBoolean(true))
        coroutineScope = logAgg.coroutineScope
    }

    @After
    fun closeDb() {
        logAgg.close()
        validateMockitoUsage()
    }

    @Test
    fun testStop() = runBlocking {
        db.openMetrics().use { db ->
            val repo = MetricRepositoryImpl(Formatter, db, LoggerImpl())
            coroutineScope.launch {
                logAgg.addLogMetricSuspend(
                    DTOLogMetric(
                        "msg1",
                        JSONObject(),
                        0.0,
                        "",
                        Calendar.getInstance().time,
                    ),
                )
            }.join()

            Assert.assertEquals(1, repo.getAll().size)

            logAgg.close()

            coroutineScope.launch {
                logAgg.addLogMetricSuspend(
                    DTOLogMetric(
                        "msg2",
                        JSONObject(),
                        0.0,
                        "",
                        Calendar.getInstance().time,
                    ),
                )
                logAgg.addLogMetricSuspend(
                    DTOLogMetric(
                        "msg3",
                        JSONObject(),
                        0.0,
                        "",
                        Calendar.getInstance().time,
                    ),
                )
            }.join()

            Assert.assertEquals(1, repo.getAll().size)
            Assert.assertEquals("msg1", repo.getAll()[0].metric)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testNoDuplicateSent() = runBlocking {
        db.openMetrics().use { db ->
            val repo = MetricRepositoryImpl(Formatter, db, LoggerImpl())
            val logSender = mock(LogAggregator.LogSender::class.java)
            val amount = 200
            argumentCaptor<Callback<Void>>()
            `when`(logSender.sendLogsAndMetrics(anyList(), anyList()))
                .then { Result.success(Unit) }
            CoroutineScope(Dispatchers.Default).launch {
                logAgg.addLogMetricSuspend(populateList(amount))
            }.join()

            Assert.assertEquals(amount, repo.getAll().size)

            awaitAll(
                async { logAgg.sendLogsAndMetricsSuspend(logSender) },
                async { logAgg.sendLogsAndMetricsSuspend(logSender) },
                async { logAgg.sendLogsAndMetricsSuspend(logSender) },
                async { logAgg.sendLogsAndMetricsSuspend(logSender) },
                async { logAgg.sendLogsAndMetricsSuspend(logSender) },
                async { logAgg.sendLogsAndMetricsSuspend(logSender) },
                async { logAgg.sendLogsAndMetricsSuspend(logSender) },
            )

            verify(logSender, times(2))
                .sendLogsAndMetrics(anyList(), anyList())

            Assert.assertEquals(0, repo.getAllUnMark().size)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testConcurrently() = runBlocking {
        db.openMetrics().use { db ->
            val repo = MetricRepositoryImpl(Formatter, db, LoggerImpl())
            val logSender = mock(LogAggregator.LogSender::class.java)
            val amountSentCaptor = argumentCaptor<List<DTOLogMetric>>()
            `when`(
                logSender.sendLogsAndMetrics(
                    anyList(),
                    amountSentCaptor.capture(),
                ),
            )
                .then { Result.success(Unit) }

            val firstInsertAmount = 100
            val secondInsertAmount = 200
            val thirdInsertAmount = 310

            awaitAll(
                async { logAgg.sendLogsAndMetricsSuspend(logSender) },
                async { logAgg.addLogMetricSuspend(populateList(firstInsertAmount)) },
                async { logAgg.addLogMetricSuspend(populateList(secondInsertAmount)) },
                async { logAgg.addLogMetricSuspend(populateList(thirdInsertAmount)) },
            )

            logAgg.sendLogsAndMetricsSuspend(logSender)

            Assert.assertEquals(0, repo.getAllUnMark().size)
            var totalSent = 0
            for (index in 0 until amountSentCaptor.allValues.size) {
                totalSent += amountSentCaptor.allValues[index].size
            }
            Assert.assertEquals(
                firstInsertAmount + secondInsertAmount + thirdInsertAmount,
                totalSent,
            )
        }
    }

    private fun populateList(amount: Int): List<DTOLogMetric> {
        val list = ArrayList<DTOLogMetric>()
        for (index in 0 until amount) {
            list.add(
                DTOLogMetric(
                    "msg $index",
                    JSONObject(),
                    0.0,
                    "",
                    Calendar.getInstance().time,
                ),
            )
        }
        return list
    }
}
