package io.justtrack

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.api.DefaultAttributionApi
import io.justtrack.database.Database
import io.justtrack.dtos.DTOPublishCustomUserIdRequest
import io.justtrack.dtos.DTOPublishFirebaseAppInstanceIdRequest
import io.justtrack.events.JtAppOpenEvent
import io.justtrack.events.TimeUnitGroup
import io.justtrack.exceptions.SdkNotTrackingException
import io.justtrack.util.ExecutorServiceFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PrivacyTest {
    private lateinit var databaseInterface: DatabaseInterface

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        databaseInterface = DatabaseInterface(context, LoggerImpl())
    }

    @Test
    @Throws(Exception::class)
    fun publishingEventWithPrivacy() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val executorBuilder = ExecutorServiceFactory {
            val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
            executor.allowCoreThreadTimeOut(true)
            executor
        }

        val sdk = TestSdk(context, executorBuilder, false)
        try {
            sdk.publishEvent(JtAppOpenEvent("sessionId", 1.0, TimeUnitGroup.MILLISECONDS, Date())).await()
            Assert.fail("Should failed.")
        } catch (exception: Exception) {
            Assert.assertTrue(exception.cause is SdkNotTrackingException)
        }

        sdk.start()
        try {
            sdk.publishEvent(JtAppOpenEvent("sessionId", 1.0, TimeUnitGroup.MILLISECONDS, Date())).await()
        } catch (exception: Exception) {
            Assert.fail("Should passed.")
        }

        sdk.stop()

        try {
            sdk.publishEvent(JtAppOpenEvent("sessionId", 1.0, TimeUnitGroup.MILLISECONDS, Date())).await()
            Assert.fail("Should failed.")
        } catch (exception: Exception) {
            Assert.assertTrue(exception.cause is SdkNotTrackingException)
        }
    }

    @Test
    fun startMultipleTimeTest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val executorBuilder = ExecutorServiceFactory {
            val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
            executor.allowCoreThreadTimeOut(true)
            executor
        }
        val sdk = TestSdk(context, executorBuilder, false)

        sdk.start()
        sdk.stop()
        sdk.start()
        sdk.stop()
        sdk.start()
        sdk.start()
        sdk.start()

        try {
            sdk.publishEvent(JtAppOpenEvent("sessionId", 1.0, TimeUnitGroup.MILLISECONDS, Date())).await()
        } catch (exception: Exception) {
            Assert.fail("Should passed.")
        }

        sdk.stop()

        try {
            sdk.publishEvent(JtAppOpenEvent("sessionId", 1.0, TimeUnitGroup.MILLISECONDS, Date())).await()
            Assert.fail("Should failed.")
        } catch (exception: Exception) {
            Assert.assertTrue(exception.cause is SdkNotTrackingException)
        }
    }

    @Test
    fun configurationEnablingTest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apis = ConfigurationTestApi()
        val userId = UUID.randomUUID()
        val firebaseId = UUID.randomUUID()
        val trackingId = UUID.randomUUID()
        val trackingProvider = "provider"
        val executorBuilder = ExecutorServiceFactory {
            val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
            executor.allowCoreThreadTimeOut(true)
            executor
        }
        val sdk = TestSdk(context, executorBuilder, false, attributionApi = apis)
        val configurationBuilder = JustTrackSdkConfig.Builder()
            .withUserId(userId.toString())
            .withFirebaseIntegration(firebaseId.toString())
            .withTrackingId(trackingId.toString(), trackingProvider)
            .build()
        sdk.startWithConfig(configurationBuilder)

        val trackedCustomId = apis.trackedCustomId
        Assert.assertEquals(userId.toString(), trackedCustomId.await())

        val trackedFirebaseId = apis.firebaseId
        Assert.assertEquals(firebaseId.toString(), trackedFirebaseId.await())

        Assert.assertEquals(sdk.trackingId.toString(), trackingId.toString())
        Assert.assertEquals(sdk.trackingProvider, trackingProvider)
    }

    @Test
    fun configurationDisablingTest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = ConfigurationTestApi()

        val executorBuilder = ExecutorServiceFactory {
            val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
            executor.allowCoreThreadTimeOut(true)
            executor
        }
        val sdk = TestSdk(context, executorBuilder, false, attributionApi = api)
        val configurationBuilder = JustTrackSdkConfig.Builder().build()
        sdk.startWithConfig(configurationBuilder)

        try {
            withTimeout(100) {
                api.trackedCustomId.await()
                Assert.fail()
            }
        } catch (e: TimeoutCancellationException) {
            // nop
        }

        try {
            withTimeout(100) {
                api.firebaseId.await()
                Assert.fail()
            }
        } catch (e: TimeoutCancellationException) {
            // nop
        }

        Assert.assertEquals(sdk.trackingId, null)
        Assert.assertEquals(sdk.trackingProvider, "advertiserId")
    }

    @Throws(Exception::class)
    @Test(timeout = 10_000L)
    fun publishingEventEdgeScenarioTest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val executorBuilder = ExecutorServiceFactory {
            val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
            executor.allowCoreThreadTimeOut(true)
            executor
        }

        val publishingResult: ArrayList<AsyncFuture<Void?>> = ArrayList()
        val sdk = TestSdk(context, executorBuilder, false)
        sdk.start()
        try {
            publishingResult.add(sdk.publishEvent(AppEvent("event1")))
            publishingResult.add(sdk.publishEvent(AppEvent("event2")))
            publishingResult.add(sdk.publishEvent(AppEvent("event3")))
            publishingResult.add(sdk.publishEvent(AppEvent("event4")))
            sdk.stop()
        } catch (exception: Exception) {
            Assert.fail()
        }

        publishingResult.forEach {
            it.await()
        }
    }

    private class ConfigurationTestApi : DefaultAttributionApi() {
        val trackedCustomId = CompletableDeferred<String>()
        val firebaseId = CompletableDeferred<String>()
        override suspend fun sendCustomUserId(
            body: DTOPublishCustomUserIdRequest,
            advertiserId: String?,
            uuid: String,
            installId: String,
        ): Result<Unit> {
            trackedCustomId.complete(body.customUserId)
            return super.sendCustomUserId(body, advertiserId, uuid, installId)
        }

        override suspend fun sendFirebaseAppInstanceId(body: JSONEncodable, advertiserId: String?, uuid: String, installId: String): Result<Unit> {
            val bodyDTO = body as DTOPublishFirebaseAppInstanceIdRequest
            firebaseId.complete(bodyDTO.firebaseInstanceId)
            return super.sendFirebaseAppInstanceId(body, advertiserId, uuid, installId)
        }
    }
}
