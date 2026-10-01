package io.justtrack

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.justtrack.api.DefaultEventApi
import io.justtrack.api.DefaultLogApi
import io.justtrack.database.Database
import io.justtrack.dtos.DTOAppEvent
import io.justtrack.events.JtSessionTrackingEvent
import io.justtrack.log.Logger
import io.justtrack.versions.ApplicationVersionImpl
import io.justtrack.versions.SdkVersionImpl
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.validateMockitoUsage
import java.util.Date
import java.util.UUID

@RunWith(AndroidJUnit4::class)
internal class BackgroundWorkerTest {
    private lateinit var context: Context
    private lateinit var db: DatabaseInterface
    private val formatter = Formatter

    private val defaultMessage = LogMessageEntity(
        level = "level",
        message = "message1",
        fields = JSONObject().toString(),
        timestamp = formatter.formatDateMilliseconds(Date()),
        timestampInMS = Date().time,
    )

    private val defaultMetric = LogMetricEntity(
        name = "name1",
        value = 100.0,
        dimensions = JSONObject().toString(),
        unit = "",
        timestamp = formatter.formatDateMilliseconds(Date()),
        timestampInMS = Date().time,
    )

    private val defaultEvent = UserEventEntity(
        eventId = UUID(0, 0).toString(),
        eventName = "name",
        dimensions = JSONObject().toString(),
        value = 100.0,
        unit = null,
        currency = "currency",
        timestamp = formatter.formatDateMilliseconds(Date()),
        timestampInMS = Date().time,
        sessionId = UUID.randomUUID().toString(),
        sdkVersionMajor = 5,
        sdkVersionMinor = 0,
        sdkVersionPatch = 0,
        sdkVersionName = "5.0.0",
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        db = DatabaseInterface(context, LoggerImpl())
    }

    @After
    fun validate() {
        validateMockitoUsage()
    }

    @Test
    fun testSimpleWork() = runBlocking {
        db.openEvents().use { db ->
            db.insertEvent(defaultEvent)
        }
        db.openMessages().use { db ->
            db.insertMessage(defaultMessage)
        }
        db.openMetrics().use { db ->
            db.insertMetric(defaultMetric)
        }
        val receiver = BackgroundSenderTaskReceiver()
        receiver.setEventApi(TestEventApi("VALID_ADVERTISER_ID"))
        receiver.setLogApi(TestLogApi("VALID_ADVERTISER_ID"))
        receiver.onReceive(
            context,
            Intent().apply {
                getDefaultInput().appendDataToIntent(this)
            },
        )
        val result = receiver.resultForTest.await()
        Assert.assertTrue(result)
        Assert.assertTrue(checkLocalDBEmpty(db))
    }

    @Test
    fun testFailWork() = runBlocking {
        db.openEvents().use { db ->
            db.insertEvent(defaultEvent)
        }
        db.openMessages().use { db ->
            db.insertMessage(defaultMessage)
        }
        db.openMetrics().use { db ->
            db.insertMetric(defaultMetric)
        }

        val receiver = BackgroundSenderTaskReceiver()
        receiver.setEventApi(TestEventApi("VALID_ADVERTISER_ID"))
        receiver.setLogApi(TestLogApi("VALID_ADVERTISER_ID"))
        receiver.onReceive(
            context,
            Intent().apply {
                getDefaultInput().copy(advertiseId = "WRONG_ID").appendDataToIntent(this)
            },
        )

        val result = receiver.resultForTest.await()
        Assert.assertFalse(result)
    }

    @Test
    fun testWithManyData() = runBlocking {
        db.openMessages().use { db ->
            db.insertMessages(populateList(defaultMessage, 200))
        }
        db.openMetrics().use { db ->
            db.insertMetrics(populateList(defaultMetric, 200))
        }
        db.openEvents().use { db ->
            db.insertEvents(populateList(defaultEvent, 200))
        }

        val receiver = BackgroundSenderTaskReceiver()
        receiver.setEventApi(TestEventApi("VALID_ADVERTISER_ID"))
        receiver.setLogApi(TestLogApi("VALID_ADVERTISER_ID"))
        receiver.onReceive(
            context,
            Intent().apply {
                getDefaultInput().appendDataToIntent(this)
            },
        )

        val result = receiver.resultForTest.await()
        Assert.assertTrue(result)
        Assert.assertTrue(checkLocalDBEmpty(db))
    }

    @Test
    fun testUpdateEndSession() = runBlocking {
        val currentTime = System.currentTimeMillis() - 10000
        val sessionStart = currentTime - 200000
        val sessionAge: Long = currentTime - sessionStart

        val store = context.getSharedPreferences("justtrack-session-manager", Context.MODE_PRIVATE)

        store.putString(
            "justtrack-session",
            UUID.randomUUID().toString() + ":" + sessionAge + ":" + currentTime,
        )
        db.openEvents().use { db ->
            db.insertEvent(
                defaultEvent.copy(
                    timestamp = formatter.formatDateMilliseconds(Date(currentTime)),
                    timestampInMS = currentTime,
                ),
            )
        }

        val receiver = BackgroundSenderTaskReceiver()
        receiver.setEventApi(object : DefaultEventApi() {
            override suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?> {
                body.events.forEach { event ->
                    if (event.name == JtSessionTrackingEvent.NAME && event.happenedAt == Date(currentTime)) {
                        return Result.success(null)
                    }
                }
                return Result.failure(Exception("No end session event or date is not latest"))
            }
        })
        receiver.setLogApi(DefaultLogApi())
        receiver.onReceive(
            context,
            Intent().apply {
                getDefaultInput().appendDataToIntent(this)
            },
        )

        val result = receiver.resultForTest.await()
        Assert.assertTrue(result)
    }

    @Test
    fun testSomeBatchFail() = runBlocking {
        val failBatch = "3"

        val batchOne = ArrayList<UserEventEntity>()
        val batchTwo = ArrayList<UserEventEntity>()
        val batchThree = ArrayList<UserEventEntity>()

        for (i in 0 until 100) {
            batchOne.add(defaultEvent.copy(eventName = "1"))
        }
        for (i in 0 until 100) {
            batchTwo.add(defaultEvent.copy(eventName = "2"))
        }
        for (i in 0 until 100) {
            batchThree.add(defaultEvent.copy(eventName = "3"))
        }

        db.openEvents().use { db ->
            db.insertEvents(batchOne + batchTwo + batchThree)
        }

        val receiver = BackgroundSenderTaskReceiver()
        receiver.setEventApi(object : DefaultEventApi() {
            override suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?> {
                return if (body.events[0].name.contains(failBatch)) {
                    Result.failure(Exception("Fail batch"))
                } else {
                    Result.success(null)
                }
            }
        })
        receiver.setLogApi(DefaultLogApi())
        receiver.onReceive(
            context,
            Intent().apply {
                getDefaultInput().appendDataToIntent(this)
            },
        )

        val result = receiver.resultForTest.await()
        Assert.assertFalse(result)

        val remainingEvents = ArrayList<UserEventEntity>()

        db.openEvents().use {
            remainingEvents.addAll(it.getAllEvent())
        }

        remainingEvents.forEach {
            Assert.assertTrue(it.eventName == failBatch)
        }
    }

    private fun getDefaultInput(): WorkerInputData {
        return WorkerInputData(
            "VALID_TOKEN",
            Environment(),
            "VALID_ADVERTISER_ID",
            "VALID_TRACKING_ID",
            "",
            UUID(0, 0),
            UUID(0, 0).toString(),
            UUID(0, 0).toString(),
            SdkVersionImpl(7, 1, 3, "7.1.3"),
            isLogEnabled = true,
            applicationPackageName = "io.justtrack.test",
            applicationVersion = ApplicationVersionImpl("1.0.0", "1"),
        )
    }

    @Test
    fun testWorkerInputDataSdkVersionRoundTrip() {
        val input = getDefaultInput()
        val intent = Intent()
        input.appendDataToIntent(intent)

        val restored = WorkerInputData(intent)

        Assert.assertEquals(input.sdkVersion.major, restored.sdkVersion.major)
        Assert.assertEquals(input.sdkVersion.minor, restored.sdkVersion.minor)
        Assert.assertEquals(input.sdkVersion.patch, restored.sdkVersion.patch)
        Assert.assertEquals(input.sdkVersion.name, restored.sdkVersion.name)
        Assert.assertEquals(input.sdkVersion.platformType, restored.sdkVersion.platformType)
    }

    @Test
    fun testWorkerInputDataRoundTrip() {
        val input = getDefaultInput()
        val intent = Intent()
        input.appendDataToIntent(intent)

        val restored = WorkerInputData(intent)

        Assert.assertEquals(input.apiToken, restored.apiToken)
        Assert.assertEquals(input.advertiseId, restored.advertiseId)
        Assert.assertEquals(input.trackingId, restored.trackingId)
        Assert.assertEquals(input.trackingProvider, restored.trackingProvider)
        Assert.assertEquals(input.userId, restored.userId)
        Assert.assertEquals(input.installId, restored.installId)
        Assert.assertEquals(input.installInstanceId, restored.installInstanceId)
        Assert.assertEquals(input.isLogEnabled, restored.isLogEnabled)
        Assert.assertEquals(input.applicationPackageName, restored.applicationPackageName)
        Assert.assertEquals(input.applicationVersion.getVersionName(), restored.applicationVersion.getVersionName())
        Assert.assertEquals(input.applicationVersion.getVersionCode(), restored.applicationVersion.getVersionCode())
    }

    private fun <T> populateList(data: T, size: Int): List<T> {
        val list = ArrayList<T>()
        for (i in 0 until size) {
            list.add(data)
        }
        return list
    }

    private class TestEventApi(private val validAdvertiserId: String) : DefaultEventApi() {
        override suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?> {
            return if (advertiserId == validAdvertiserId) {
                Result.success(null)
            } else {
                Result.failure(Exception("wrong advertiserId"))
            }
        }
    }

    private class TestLogApi(private val validAdvertiserId: String) : DefaultLogApi() {
        override suspend fun sendLogs(logger: Logger, body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<Unit> {
            return if (advertiserId == validAdvertiserId) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("wrong advertiserId"))
            }
        }
    }

    private suspend fun checkLocalDBEmpty(sql: DatabaseInterface): Boolean {
        var isEmpty = true
        sql.openMetrics().use {
            if (it.getAllMetric().isNotEmpty()) {
                isEmpty = false
            }
        }

        if (!isMessageEmpty(db)) {
            isEmpty = false
        }

        sql.openEvents().use {
            if (it.getAllEvent().isNotEmpty()) {
                isEmpty = false
            }
        }

        return isEmpty
    }

    /**
     * This is needed because after event is published we are sending a `successfully published` message as well.
     */
    private suspend fun isMessageEmpty(db: DatabaseInterface): Boolean {
        var onlySuccessfullyMessageLeft = true
        db.openMessages().use {
            val messages = it.getAllMessage()
            messages.forEach { message ->
                if (!message.message.contains("Successfully published")) {
                    onlySuccessfullyMessageLeft = false
                }
            }
        }
        return onlySuccessfullyMessageLeft
    }
}
