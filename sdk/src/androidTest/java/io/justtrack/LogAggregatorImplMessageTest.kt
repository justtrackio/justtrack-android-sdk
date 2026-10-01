package io.justtrack

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.justtrack.database.Database
import io.justtrack.dtos.DTOLogMessage
import io.justtrack.dtos.LogLevel
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
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class LogAggregatorImplMessageTest {
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
        db.openMessages().use { db ->
            val repo = MessageRepositoryImpl(Formatter, db, LoggerImpl())
            coroutineScope.launch {
                logAgg.addLogMessageSuspend(
                    DTOLogMessage(
                        LogLevel.DEBUG,
                        "msg1",
                        JSONObject(),
                        Calendar.getInstance().time,
                    ),
                )
            }.join()

            Assert.assertEquals(1, repo.getAll().size)

            logAgg.close()

            coroutineScope.launch {
                logAgg.addLogMessageSuspend(
                    DTOLogMessage(
                        LogLevel.DEBUG,
                        "msg2",
                        JSONObject(),
                        Calendar.getInstance().time,
                    ),
                )
                logAgg.addLogMessageSuspend(
                    DTOLogMessage(
                        LogLevel.DEBUG,
                        "msg3",
                        JSONObject(),
                        Calendar.getInstance().time,
                    ),
                )
            }.join()

            Assert.assertEquals(1, repo.getAll().size)
            Assert.assertEquals("msg1", repo.getAll()[0].message)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testNoDuplicateSent() = runBlocking {
        db.openMessages().use { db ->
            val repo = MessageRepositoryImpl(Formatter, db, LoggerImpl())
            val logSender = CapturingLogSender()
            val amount = 200
            CoroutineScope(Dispatchers.Default).launch {
                logAgg.addLogMessagesSuspend(populateList(amount))
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
        db.openMessages().use { db ->
            val repo = MessageRepositoryImpl(Formatter, db, LoggerImpl())
            val logSender = CapturingLogSender()

            val firstInsertAmount = 100
            val secondInsertAmount = 200
            val thirdInsertAmount = 310

            awaitAll(
                async { logAgg.sendLogsAndMetricsSuspend(logSender) },
                async { logAgg.addLogMessagesSuspend(populateList(firstInsertAmount)) },
                async { logAgg.addLogMessagesSuspend(populateList(secondInsertAmount)) },
                async { logAgg.addLogMessagesSuspend(populateList(thirdInsertAmount)) },
            )

            logAgg.sendLogsAndMetricsSuspend(logSender)

            Assert.assertEquals(0, repo.getAllUnMark().size)
            var totalSent = 0
            for (index in 0 until logSender.sentMessages.size) {
                totalSent += logSender.sentMessages[index].size
            }
            Assert.assertEquals(
                firstInsertAmount + secondInsertAmount + thirdInsertAmount,
                totalSent,
            )
        }
    }

    private fun populateList(amount: Int): List<DTOLogMessage> {
        val list = ArrayList<DTOLogMessage>()
        for (index in 0 until amount) {
            list.add(
                DTOLogMessage(
                    LogLevel.DEBUG,
                    "msg $index",
                    JSONObject(),
                    Calendar.getInstance().time,
                ),
            )
        }
        return list
    }
}
