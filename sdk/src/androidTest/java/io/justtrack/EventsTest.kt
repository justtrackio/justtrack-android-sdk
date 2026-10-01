package io.justtrack

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.api.DefaultAttributionApi
import io.justtrack.api.DefaultEventApi
import io.justtrack.database.Database
import io.justtrack.dtos.DTOAppEvent
import io.justtrack.events.JtAppInstallEvent
import io.justtrack.events.JtAppOpenEvent
import io.justtrack.events.JtSessionTrackingEvent
import io.justtrack.publicInterface.SdkTest
import io.justtrack.versions.ApplicationVersionImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventsTest {
    private lateinit var databaseInterface: DatabaseInterface

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SessionManagerImpl.Session.clearForTesting(context)
        val preferences = context.getSharedPreferences("justtrack-attribution", Context.MODE_PRIVATE)
        preferences.edit().clear().apply()
        val sessionPreferences = context.getSharedPreferences("justtrack-session-manager", Context.MODE_PRIVATE)
        sessionPreferences.edit().clear().apply()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        databaseInterface = DatabaseInterface(context, LoggerImpl())
    }

    @Test
    @Throws(Exception::class)
    fun testCorrectEventsPublished() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withAllStates(
            context,
            object : TestCaseCallback {
                override suspend fun runTest(expectedEventOrders: Array<Array<TestEvent>>) {
                    val builder = JustTrackSdkBuilder(
                        (context.applicationContext as Application),
                        SdkTest.API_TOKEN,
                    )
                    val events = ArrayList<TestEvent>()
                    val successAttributionApi = SuccessAttributionApi()
                    val eventApi = EventCollectingApis(events)
                    val sdk = createForTesting(
                        builder,
                        RetryConfig.DEFAULT_CONFIG,
                        null,
                        null,
                        attributionApi = successAttributionApi,
                        eventApi = eventApi,
                    )
                    val published = sdk.publishEvent(AppEvent("custom_event_name"))

                    checkStoreCompleted()

                    sdk.onPause()
                    published.await()
                    sdk.shutdown()
                    sdk.publishEventsQueue.waitClosed()

                    val actualEvents = events.toTypedArray()

                    var containSameOrderOfEvent = false

                    expectedEventOrders.forEach {
                        if (it.contentEquals(actualEvents)) {
                            Assert.assertArrayEquals(it, actualEvents)
                            containSameOrderOfEvent = true
                        }
                    }

                    if (!containSameOrderOfEvent) {
                        Assert.fail("Ordering of event is incorrect or missing event")
                    }
                }
            },
        )
    }

    private suspend fun checkStoreCompleted() {
        delay(1000)
    }

    @Throws(Exception::class)
    private suspend fun withAllStates(context: Context, callback: TestCaseCallback) {
        // reset and remove data for the first run
        Store.clearForTesting(context)
        SessionManagerImpl.Session.clearForTesting(context)
        CustomUserIdStore.getInstance().clearForTesting(context)
        FirebaseIdStore.getInstance().clearForTesting(context)
        JustTrack.resetForTesting()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        // execute on an empty store
        callback.runTest(
            arrayOf(
                arrayOf(
                    TestEvent(JtSessionTrackingEvent.NAME, "start"),
                    TestEvent(JtAppOpenEvent.NAME),
                    TestEvent(JtAppInstallEvent.NAME),
                    TestEvent("custom_event_name"),
                    TestEvent(JtSessionTrackingEvent.NAME, "end"),
                ),
                arrayOf(
                    TestEvent(JtSessionTrackingEvent.NAME, "start"),
                    TestEvent(JtAppOpenEvent.NAME),
                    TestEvent("custom_event_name"),
                    TestEvent(JtAppInstallEvent.NAME),
                    TestEvent(JtSessionTrackingEvent.NAME, "end"),
                ),
            ),
        )
        JustTrack.resetForTesting()
        callback.runTest(
            arrayOf(
                arrayOf(
                    TestEvent(JtSessionTrackingEvent.NAME, "start"),
                    TestEvent(JtAppOpenEvent.NAME),
                    TestEvent("custom_event_name"),
                    TestEvent(JtSessionTrackingEvent.NAME, "end"),
                ),
            ),
        )
        databaseInterface.openAttribution().use {
            it.getAppVersionUpdateInfo(ApplicationVersionImpl("4.2", "42"))
        }
        JustTrack.resetForTesting()

        callback.runTest(
            arrayOf(
                arrayOf(
                    TestEvent(JtSessionTrackingEvent.NAME, "start"),
                    TestEvent(JtAppOpenEvent.NAME),
                    // no app update event anymore (used to be here)
                    TestEvent("custom_event_name"),
                    TestEvent(JtSessionTrackingEvent.NAME, "end"),
                ),
            ),
        )
    }

    private data class TestEvent(val name: String, val action: String? = null)

    private interface TestCaseCallback {
        @Throws(Exception::class)
        suspend fun runTest(expectedEventOrders: Array<Array<TestEvent>>)
    }

    private class EventCollectingApis(private val events: MutableList<TestEvent>) : DefaultEventApi() {
        override suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?> {
            synchronized(this) {
                for (event in body.events) {
                    events.add(TestEvent(event.name, action = event.dimensions?.optString("jt_action", null)))
                }
            }
            return Result.success(JSONObject())
        }
    }

    private class SuccessAttributionApi() : DefaultAttributionApi() {
        override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            return Result.success(AttributionTest.testAttribution)
        }
    }
}
