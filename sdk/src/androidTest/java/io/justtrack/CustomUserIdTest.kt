package io.justtrack

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.Store.clearForTesting
import io.justtrack.api.AttributionApi
import io.justtrack.api.DefaultAttributionApi
import io.justtrack.dtos.DTOPublishCustomUserIdRequest
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.publicInterface.SdkTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class CustomUserIdTest {
    @Test(timeout = 300_000L)
    @Throws(Exception::class)
    fun provideInvalidCustomUserId(): Unit = runBlocking {
        runTest(
            object : SdkConsumer<Array<String?>> {
                override suspend fun run(sdk: JustTrackSdk): Array<String?> {
                    try {
                        sdk.setUserId("This custom user id contains a null byte: \u0000").await()
                    } catch (e: ExecutionException) {
                        if (e.cause !is InvalidFieldException) {
                            throw e
                        }
                        val ex = e.cause as InvalidFieldException?
                        Assert.assertEquals(
                            "Invalid customUserId value: 'This custom user id contains a null byte: \u0000'. " +
                                "It needs to be between 1 and 4096 characters and only include ASCII characters.",
                            ex!!.message,
                        )
                    }
                    return arrayOfNulls(0)
                }
            },
        )
    }

    @Test(timeout = 300_000L)
    @Throws(Exception::class)
    fun provideDifferentCustomUserIds(): Unit = runBlocking {
        runTest(
            object : SdkConsumer<Array<String?>> {
                override suspend fun run(sdk: JustTrackSdk): Array<String?> {
                    sdk.setUserId("First Id").await()
                    sdk.setUserId("Second Id").await()
                    return arrayOf(
                        "First Id",
                        "Second Id",
                    )
                }
            },
        )
    }

    @Test(timeout = 300_000L)
    @Throws(Exception::class)
    fun provideCustomUserIdTwice(): Unit = runBlocking {
        runTest(
            object : SdkConsumer<Array<String?>> {
                override suspend fun run(sdk: JustTrackSdk): Array<String?> {
                    sdk.setUserId("First Id").await()
                    sdk.setUserId("First Id").await()
                    return arrayOf(
                        "First Id",
                    )
                }
            },
        )
    }

    @Test(timeout = 10_000L)
    @Throws(Exception::class)
    fun customUserIdSentAfterFirstAttribution(): Unit = runBlocking {
        val api = SlowAttributionApi()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withSdk(
            context,
            true,
            object : SdkConsumer<Unit> {
                override suspend fun run(sdk: JustTrackSdk) {
                    runBlocking {
                        sdk.setUserId("my custom user id")
                        Assert.assertFalse(api.customUserIdFuture.isDone)
                        sdk.attribution.await()
                        val customUserId = api.waitForCustomUserId().customUserId
                        Assert.assertEquals("my custom user id", customUserId)
                    }
                }
            },
            api,
        )
    }

    @Test(timeout = 50_000L)
    fun testSendIdAfterInstallIdChanged(): Unit = runBlocking {
        val apis = SlowAttributionApi(0)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withSdk(
            context,
            true,
            object : SdkConsumer<Unit> {
                override suspend fun run(sdk: JustTrackSdk) {
                    runBlocking {
                        val firstId = "00000000-0000-4000-a000-000000000000"
                        val newId = "11111111-1111-4111-b111-111111111111"
                        sdk.setUserId(firstId).await()
                        val caughtFirstId = apis.waitForCustomUserId().customUserId
                        Assert.assertEquals(firstId, caughtFirstId)
                        apis.resetCustomUserIdFuture()
                        CustomUserIdStore.getInstance().storeNewId(context, UUID.randomUUID().toString(), newId)

                        val caughtId = apis.waitForCustomUserId().customUserId
                        Assert.assertEquals(newId, caughtId)
                    }
                }
            },
            apis,
        )
    }

    @Throws(Exception::class)
    private suspend fun runTest(test: SdkConsumer<Array<String?>>) {
        val apis = CustomUserIdApi()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedIds = withSdk(
            context,
            true,
            object : SdkConsumer<Array<String?>> {
                override suspend fun run(sdk: JustTrackSdk): Array<String?> {
                    // wait for the attribution to be done and sleep a short while so we don't
                    // get custom user ids resubmitted after an install id change
                    sdk.attribution.await()
                    delay(300)
                    return test.run(sdk)
                }
            },
            apis,
        )

        // Deadlock-Safety: This is a test.
        synchronized(apis.customUserIds) {
            Assert.assertEquals(
                expectedIds.size,
                apis.customUserIds.size,
            )
            for (i in expectedIds.indices) {
                Assert.assertEquals(expectedIds[i], apis.customUserIds[i])
            }
        }
    }

    @Throws(Exception::class)
    private suspend fun <T> withSdk(context: Context, clearStorage: Boolean, test: SdkConsumer<T>, api: AttributionApi): T {
        if (clearStorage) {
            // reset and remove data for the test
            clearForTesting(context)
            SessionManagerImpl.Session.clearForTesting(context)
            CustomUserIdStore.getInstance().clearForTesting(context)
            FirebaseIdStore.getInstance().clearForTesting(context)
            JustTrack.resetForTesting()
        }

        val builder =
            JustTrackSdkBuilder((context.applicationContext as Application), SdkTest.API_TOKEN)
        val sdk = createForTesting(
            builder,
            RetryConfig(5, 0, 5, RetryConfig.TEST_INTEGRITY_CONFIG),
            null,
            null,
            api,
        )
        return try {
            test.run(sdk)
        } finally {
            // shut down the sdk so we don't leave threads for other tests to conflict with running
            sdk.shutdown()
            sdk.publishEventsQueue.waitClosed()
        }
    }

    internal class CustomUserIdApi : DefaultAttributionApi() {
        internal val customUserIds: MutableList<String> = ArrayList()

        override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            return Result.success(AttributionTest.testAttribution)
        }

        override suspend fun sendCustomUserId(
            body: DTOPublishCustomUserIdRequest,
            advertiserId: String?,
            uuid: String,
            installId: String,
        ): Result<Unit> {
            // Deadlock-Safety: This is a test.
            synchronized(customUserIds) { customUserIds.add(body.customUserId) }
            return Result.success(Unit)
        }
    }

    private interface SdkConsumer<T> {
        @Throws(Exception::class)
        suspend fun run(sdk: JustTrackSdk): T
    }

    internal class SlowAttributionApi(
        private val sleepTime: Long = 100,
    ) : DefaultAttributionApi() {
        internal var customUserIdFuture = ResolvableFuture<DTOPublishCustomUserIdRequest>()
        private val attributionDisabled = false

        override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            try {
                Thread.sleep(sleepTime)
            } catch (e: InterruptedException) {
                return Result.failure(e)
            }
            if (attributionDisabled) {
                return Result.failure(BadResponseException("attribution is disabled for test", 400))
            }
            return Result.success(AttributionTest.testAttribution)
        }

        override suspend fun sendCustomUserId(
            body: DTOPublishCustomUserIdRequest,
            advertiserId: String?,
            uuid: String,
            installId: String,
        ): Result<Unit> {
            customUserIdFuture.resolve(body)
            return Result.success(Unit)
        }

        @Throws(
            ExecutionException::class,
            InterruptedException::class,
            TimeoutException::class,
        )
        suspend fun waitForCustomUserId(): DTOPublishCustomUserIdRequest {
            return customUserIdFuture.await()
        }

        fun resetCustomUserIdFuture() {
            customUserIdFuture = ResolvableFuture()
        }
    }
}
