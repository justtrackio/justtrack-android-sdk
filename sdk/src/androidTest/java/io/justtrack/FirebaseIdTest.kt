package io.justtrack

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.Store.clearForTesting
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.log.Logger
import io.justtrack.publicInterface.SdkTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class FirebaseIdTest {
    @Test
    @Throws(Exception::class)
    fun provideInvalidFirebaseId(): Unit = runBlocking {
        runTest(
            object : SdkConsumer<Array<String?>> {
                override suspend fun run(sdk: JustTrackSdk): Array<String?> {
                    try {
                        sdk.setFirebaseAppInstanceId("This firebase id contains a null byte: \u0000")
                            .await()
                    } catch (exception: ExecutionException) {
                        if (exception.cause !is InvalidFieldException) {
                            throw exception
                        }
                        val ex = exception.cause as InvalidFieldException
                        Assert.assertEquals(
                            "Invalid firebaseAppInstanceId value: 'This firebase id contains a null byte: \u0000'. " +
                                "It needs to be between 8 and 256 characters and only include ASCII characters.",
                            ex.message,
                        )
                    }
                    return arrayOfNulls(0)
                }
            },
        )
    }

    @Test
    @Throws(Exception::class)
    fun provideDifferentFirebaseIds(): Unit = runBlocking {
        runTest(
            object : SdkConsumer<Array<String?>> {
                override suspend fun run(sdk: JustTrackSdk): Array<String?> {
                    sdk.setFirebaseAppInstanceId("First Id").await()
                    sdk.setFirebaseAppInstanceId("Second Id").await()
                    return arrayOf(
                        "First Id",
                        "Second Id",
                    )
                }
            },
        )
    }

    @Test
    @Throws(Exception::class)
    fun provideFirebaseIdTwice(): Unit = runBlocking {
        runTest(
            object : SdkConsumer<Array<String?>> {
                override suspend fun run(sdk: JustTrackSdk): Array<String?> {
                    sdk.setFirebaseAppInstanceId("First Id").await()
                    sdk.setFirebaseAppInstanceId("First Id").await()
                    return arrayOf(
                        "First Id",
                    )
                }
            },
        )
    }

    @Test
    @Throws(Exception::class)
    fun firebaseIdSentAfterFirstAttribution(): Unit = runBlocking {
        val httpClient = SlowAttributingHttpClient()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withSdk(
            context,
            httpClient,
            true,
            object : SdkConsumer<Any> {
                override suspend fun run(sdk: JustTrackSdk): Any {
                    sdk.setFirebaseAppInstanceId("my firebase id")
                    Assert.assertFalse(httpClient.firebaseIdFuture.isDone)
                    sdk.attribution.await()
                    val firebaseId = httpClient.waitForFirebaseId().firebaseInstanceId
                    Assert.assertEquals("my firebase id", firebaseId)
                    return Any()
                }
            },
        )
    }

    @Test
    fun testSendIdAfterInstallIdChanged(): Unit = runBlocking {
        val httpClient = SlowAttributingHttpClient(0)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withSdk(
            context,
            httpClient,
            true,
            object : SdkConsumer<Unit> {
                override suspend fun run(sdk: JustTrackSdk) {
                    val firstId = UUID.randomUUID().toString()
                    val newId = UUID.randomUUID().toString()
                    sdk.setFirebaseAppInstanceId(firstId).await()
                    FirebaseIdStore.getInstance().storeNewId(context, UUID.randomUUID().toString(), newId)
                    httpClient.clearFutureId()
                    val caughtId = httpClient.waitForFirebaseId().firebaseInstanceId
                    Assert.assertEquals(newId, caughtId)
                }
            },
        )
    }

    @Throws(Exception::class)
    private suspend fun runTest(test: SdkConsumer<Array<String?>>) {
        val httpClient = FirebaseIdHttpClient()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedIds = withSdk(
            context,
            httpClient,
            true,
            object : SdkConsumer<Array<String?>> {
                override suspend fun run(sdk: JustTrackSdk): Array<String?> {
                    // wait for the attribution to be done and sleep a short while so we don't
                    // get firebase ids resubmitted after an install id change
                    sdk.attribution.await()
                    delay(300)
                    return test.run(sdk)
                }
            },
        )

        // Deadlock-Safety: This is a test.
        synchronized(httpClient.firebaseIds) {
            Assert.assertEquals(
                expectedIds.size,
                httpClient.firebaseIds.size,
            )
            for (i in expectedIds.indices) {
                Assert.assertEquals(expectedIds[i], httpClient.firebaseIds[i])
            }
        }
    }

    @Throws(Exception::class)
    private suspend fun <T> withSdk(context: Context, httpClient: HttpClient, clearStorage: Boolean, test: SdkConsumer<T>): T {
        if (clearStorage) {
            // reset and remove data for the test
            clearForTesting(context)
            SessionManagerImpl.Session.clearForTesting(context)
            FirebaseIdStore.getInstance().clearForTesting(context)
            JustTrack.resetForTesting()
        }
        val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
        executor.allowCoreThreadTimeOut(true)
        val builder = JustTrackSdkBuilder((context.applicationContext as Application), SdkTest.API_TOKEN)
        val sdk = JustTrackSdkImpl.createForTesting(builder, httpClient, RetryConfig(5, 0, 5, RetryConfig.TEST_INTEGRITY_CONFIG), null, null)
        return try {
            test.run(sdk)
        } finally {
            // shut down the sdk so we don't leave threads for other tests to conflict with running
            sdk.shutdown()
            sdk.publishEventsQueue.waitClosed()
        }
    }

    private class FirebaseIdHttpClient : BaseTestHttpClient() {
        val firebaseIds: MutableList<String> = ArrayList()

        override suspend fun sendAttributionRequest(logger: Logger, body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            return Result.success(AttributionTest.testAttribution)
        }

        override suspend fun sendFirebaseAppInstanceId(
            logger: Logger,
            body: JSONEncodable,
            advertiserId: String?,
            uuid: String,
            installId: String,
        ): Result<Unit> {
            // Deadlock-Safety: This is a test.
            val request = DTOPublishFirebaseAppInstanceIdRequest(body.toJSON(Formatter))
            synchronized(firebaseIds) { firebaseIds.add(request.firebaseInstanceId) }
            return Result.success(Unit)
        }
    }

    internal interface SdkConsumer<T> {
        @Throws(Exception::class)
        suspend fun run(sdk: JustTrackSdk): T
    }

    internal class SlowAttributingHttpClient constructor(private val sleepTime: Long = 1000) :
        BaseTestHttpClient() {
        internal var firebaseIdFuture = ResolvableFuture<DTOPublishFirebaseAppInstanceIdRequest>()
        private val attributionDisabled = false

        override suspend fun sendAttributionRequest(logger: Logger, body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
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

        override suspend fun sendFirebaseAppInstanceId(
            logger: Logger,
            body: JSONEncodable,
            advertiserId: String?,
            uuid: String,
            installId: String,
        ): Result<Unit> {
            firebaseIdFuture.resolve(DTOPublishFirebaseAppInstanceIdRequest(body.toJSON(Formatter)))
            return Result.success(Unit)
        }

        @Throws(
            ExecutionException::class,
            InterruptedException::class,
            TimeoutException::class,
        )
        fun waitForFirebaseId(): DTOPublishFirebaseAppInstanceIdRequest {
            return firebaseIdFuture[30, TimeUnit.SECONDS]
        }

        fun clearFutureId() {
            firebaseIdFuture = ResolvableFuture()
        }
    }
}
