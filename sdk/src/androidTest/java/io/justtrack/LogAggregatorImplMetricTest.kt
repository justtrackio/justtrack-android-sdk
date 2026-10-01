package io.justtrack

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.justtrack.database.Database
import io.justtrack.dtos.DTOLogMetric
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
            val logSender = CapturingLogSender()
            val amount = 200
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

            Assert.assertEquals(2, logSender.sendCount.get())
            Assert.assertEquals(0, repo.getAllUnMark().size)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testConcurrently() = runBlocking {
        db.openMetrics().use { db ->
            val repo = MetricRepositoryImpl(Formatter, db, LoggerImpl())
            val logSender = CapturingLogSender()

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
            for (index in 0 until logSender.sentMetrics.size) {
                totalSent += logSender.sentMetrics[index].size
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
