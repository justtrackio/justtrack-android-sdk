package io.justtrack

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.api.DefaultAttributionApi
import io.justtrack.database.Database
import io.justtrack.publicInterface.SdkTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AttributionTest {
    @Before
    fun setupDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        Store.clearForTesting(context)
        SessionManagerImpl.Session.clearForTesting(context)
        CustomUserIdStore.getInstance().clearForTesting(context)
        FirebaseIdStore.getInstance().clearForTesting(context)
        JustTrack.resetForTesting()
    }

    @Test
    @Throws(Exception::class)
    fun testAttributionRetriesAfterTime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // reset and remove data for the test
        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application),
            SdkTest.API_TOKEN,
        )
        val successAfterSomeRetriesAttributionApi = SuccessAfterSomeRetriesAttributionApi()

        builder.setAttributionRetryDelaySeconds(3)
        val sdk = createForTesting(
            builder,
            RetryConfig.DEFAULT_CONFIG,
            null,
            null,
            successAfterSomeRetriesAttributionApi,
        )
        // try a few times to get the attribution, should stay cached after the first failure
        for (i in 0..2) {
            try {
                val attribution = sdk.attributionOutputProvider.provideAttributionOutput(null).get().getAttributionResponse()
                Assert.fail("Should've failed, got " + attribution.getUserId())
            } catch (exception: ExecutionException) {
                var ex: Throwable? = exception
                while (ex!!.cause != null) {
                    ex = ex.cause
                }
                Assert.assertEquals("I guess the network is down in this test", ex.message)
            }
        }
        // sleep some time, we should only now fetch a new attribution
        Thread.sleep(5000)
        val attribution = sdk.attributionOutputProvider.provideAttributionOutput(null).get().getAttributionResponse()
        Assert.assertNotEquals("00000000-0000-0000-0000-000000000000", attribution.getUserId().toString())

        sdk.shutdown()
    }

    /***
     * Testing scenario where multiple attribution is being requested. First attribution failed but after subsequent attempt finishes
     *
     * Expected: Subsequent request should still succeed. And first attempt still failed.
     */
    @Test
    @Throws(Exception::class)
    fun testMultipleAttributeCalled_withFailedRequestedLonger() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // reset and remove data for the test
        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application),
            SdkTest.API_TOKEN,
        )
        val nextRequestFailed = AtomicBoolean(false)
        val failedRequestStarted = CompletableDeferred<Unit>()
        val testApis = TestAttributionApi(
            failedDelay = 500,
            nextRequestFailed = nextRequestFailed,
            failedRequestStarted = failedRequestStarted,
        )

        val sdk = createForTesting(
            builder,
            RetryConfig(0, 0, 5, RetryConfig.TEST_INTEGRITY_CONFIG),
            null,
            null,
            testApis,
        )

        // Wait for the startup attribution to finish before arming the next API call to fail.
        sdk.attributionOutputProvider.provideAttributionOutput(null).get()
        testApis.nextRequestFailed.set(true)
        val firstAttribute = sdk.attributionOutputProvider.provideAttributionOutput(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION)
        failedRequestStarted.await()
        val secondAttribute = sdk.attributionOutputProvider.provideAttributionOutput(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION)
        val thirdAttribute = sdk.attributionOutputProvider.provideAttributionOutput(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION)

        try {
            firstAttribute.get()
            Assert.fail("Expected the first attribution request to fail")
        } catch (exception: Exception) {
            var ex: Throwable? = exception
            while (ex!!.cause != null) {
                ex = ex.cause
            }
            Assert.assertEquals("I guess the network is down in this test", ex.message)
        }

        val result2 = secondAttribute.get()

        val result3 = thirdAttribute.get()
        Assert.assertNotEquals("00000000-0000-0000-0000-000000000000", result2.getAttributionResponse().getUserId().toString())
        Assert.assertNotEquals("00000000-0000-0000-0000-000000000000", result3.getAttributionResponse().getUserId().toString())
        sdk.shutdown()
    }

    /***
     * Testing scenario where multiple attribution is being requested. First attribution failed and before subsequent attempt finishes
     *
     * Expected: Subsequent request should still succeed.
     */
    @Test
    @Throws(Exception::class)
    fun testMultipleAttributeCalled_withFailedRequestedShorter() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // reset and remove data for the test
        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application),
            SdkTest.API_TOKEN,
        )
        val nextRequestFailed = AtomicBoolean(false)
        val failedRequestStarted = CompletableDeferred<Unit>()
        val testApis = TestAttributionApi(
            failedDelay = 1000,
            successDelay = 2000,
            nextRequestFailed = nextRequestFailed,
            failedRequestStarted = failedRequestStarted,
        )

        val sdk = createForTesting(
            builder,
            RetryConfig(0, 0, 5, RetryConfig.TEST_INTEGRITY_CONFIG),
            null,
            null,
            testApis,
        )

        // Wait for the startup attribution to finish before arming the next API call to fail.
        sdk.attributionOutputProvider.provideAttributionOutput(null).get()
        testApis.nextRequestFailed.set(true)
        val firstAttribute = sdk.attributionOutputProvider.provideAttributionOutput(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION)
        failedRequestStarted.await()
        val secondAttribute = sdk.attributionOutputProvider.provideAttributionOutput(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION)
        val thirdAttribute = sdk.attributionOutputProvider.provideAttributionOutput(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION)

        try {
            firstAttribute.get()
            Assert.fail("Expected the first attribution request to fail")
        } catch (exception: Exception) {
            var ex: Throwable? = exception
            while (ex!!.cause != null) {
                ex = ex.cause
            }
            Assert.assertEquals("I guess the network is down in this test", ex.message)
        }

        val result2 = secondAttribute.get()
        val result3 = thirdAttribute.get()
        Assert.assertNotEquals("00000000-0000-0000-0000-000000000000", result2.getAttributionResponse().getUserId().toString())
        Assert.assertNotEquals("00000000-0000-0000-0000-000000000000", result3.getAttributionResponse().getUserId().toString())
        sdk.shutdown()
    }

    private class TestAttributionApi(
        val failedDelay: Long = 0,
        val successDelay: Long = 0,
        val nextRequestFailed: AtomicBoolean,
        val failedRequestStarted: CompletableDeferred<Unit>,
    ) : DefaultAttributionApi() {

        override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            if (nextRequestFailed.getAndSet(false)) {
                failedRequestStarted.complete(Unit)
                delay(failedDelay)
                return Result.failure(
                    NetworkProblemException(
                        java.lang.RuntimeException("I guess the network is down in this test"),
                    ),
                )
            }
            delay(successDelay)
            return Result.success(testAttribution)
        }
    }

    private class SuccessAfterSomeRetriesAttributionApi : DefaultAttributionApi() {
        private val remainingFails = AtomicInteger(6)

        override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            if (remainingFails.getAndDecrement() > 0) {
                return Result.failure(
                    NetworkProblemException(
                        java.lang.RuntimeException("I guess the network is down in this test"),
                    ),
                )
            }
            return Result.success(testAttribution)
        }
    }
    companion object {
        val testAttribution: JSONObject = JSONObject().apply {
            try {
                val user = JSONObject()
                user.put("id", UUID.randomUUID().toString())
                user.put("installId", UUID.randomUUID().toString())
                user.put("type", "acquisition")
                user.put("redownload", false)

                val campaign = JSONObject()
                campaign.put("externalId", "42")
                campaign.put("name", "Test Campaign")
                campaign.put("type", "acquisition")
                campaign.put("organic", false)

                val channel = JSONObject()
                channel.put("id", 43)
                channel.put("name", "Test Channel")
                channel.put("incent", true)

                val network = JSONObject()
                network.put("id", 44)
                network.put("name", "Test Network")

                val attribution = JSONObject()
                attribution.put("campaign", campaign)
                attribution.put("channel", channel)
                attribution.put("network", network)
                attribution.put("attributedAt", Formatter.formatDateMilliseconds(Date()))
                attribution.put("createdAt", Formatter.formatDateMilliseconds(Date()))

                put("user", user)
                put("attribution", attribution)
            } catch (exception: JSONException) {
                throw RuntimeException(exception)
            }
        }
    }
}
