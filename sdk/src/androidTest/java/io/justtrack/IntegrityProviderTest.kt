package io.justtrack

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import io.justtrack.IntegrityTokenProvider.Companion.UNKNOWN_ERROR_CODE
import io.justtrack.database.Database
import io.justtrack.exceptions.IntegrityException
import io.justtrack.integrity.IntegrityToken
import io.justtrack.integrity.StandardTokenProviderTask
import io.justtrack.log.Logger
import io.justtrack.util.ExecutorServiceFactory
import junit.framework.TestCase.fail
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class IntegrityProviderTest {
    lateinit var context: Context
    internal lateinit var databaseInterface: DatabaseInterface
    internal lateinit var deviceInfo: DeviceInfo

    @Before
    fun createDb() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        JustTrack.resetForTesting()
        databaseInterface = DatabaseInterface(context, LoggerImpl())
        deviceInfo = DeviceInfoImpl(context)
    }

    @Test
    fun provider_provideNewTokenTest() = runBlocking {
        val expectedResult = UUID.randomUUID().toString()
        val getterTask = mock<IntegrityTokenProvider.IntegrityTokenGetterTask>()
        val secretProviderTask = TestAsyncFuture<String>("")
        val logger = mock<HttpLogger>()

        whenever(getterTask.execute()).thenReturn(IntegrityTokenData(token = expectedResult.toString()))
        val httpClient: HttpClient = TestHttpClient()
        val executorBuilder = ExecutorServiceFactory {
            val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
            executor.allowCoreThreadTimeOut(true)
            executor
        }

        val sdk = TestSdk(context, executorBuilder, httpClient, false)
        val provider = IntegrityTokenProvider(
            sdk.taskExecutor,
            context,
            DeviceInfoImpl(context),
            null,
            RetryConfig.TEST_INTEGRITY_CONFIG,
        )

        val result =
            provider.getOrRenewFuture(
                logger,
                TestAsyncFuture("123"),
                secretProviderTask,
                databaseInterface,
                getterTask,
            ).await()
        Assert.assertEquals(expectedResult.toString(), result.token)
    }

    @Test
    fun retryGetter_provideTokenTest() = runBlocking {
        val expectedResult = UUID.randomUUID().toString()
        val logger = mock<HttpLogger>()
        val getterTask = mock<IntegrityTokenProvider.IntegrityTokenGetterTask>()
        whenever(getterTask.execute()).thenReturn(IntegrityTokenData(token = expectedResult.toString()))
        val retryingTask = FixedRetryingTask(
            getterTask,
            deviceInfo,
            logger,
            TrackingEventErrorClassifier.instance,
            null,
            listOf(1, 1, 1),
        )
        val result = IntegrityTokenProvider.IntegrityRetryGetterTask(retryingTask, logger).execute()
        verify(getterTask, times(1)).execute()
        Assert.assertEquals(expectedResult, result.token)
    }

    @Test
    internal fun retryGetter_retryAndFailTest() = runBlocking {
        val logger = mock<HttpLogger>()
        val getterTask = mock<IntegrityTokenProvider.IntegrityTokenGetterTask>()
        whenever(getterTask.execute()).then {
            throw Exception("test exception")
        }
        val retryingTask = FixedRetryingTask(
            getterTask,
            deviceInfo,
            logger,
            TrackingEventErrorClassifier.instance,
            null,
            listOf(1, 1, 1),
        )
        try {
            IntegrityTokenProvider.IntegrityRetryGetterTask(retryingTask, logger).execute()
        } catch (exception: Exception) {
            // nop
        }
        verify(getterTask, times(4)).execute()
        Assert.assertTrue(true)
    }

    @Test
    fun retryGetter_retryAndSuccessTest() = runBlocking {
        val expectedToken = UUID.randomUUID().toString()
        var callCount = 0
        val expectedResult = IntegrityTokenData(previouslySent = false, token = expectedToken)
        val logger = mock<HttpLogger>()
        val getterTask = mock<IntegrityTokenProvider.IntegrityTokenGetterTask>()
        whenever(getterTask.execute()).then {
            callCount++
            if (callCount > 2) {
                expectedResult
            } else {
                throw Exception("test exception")
            }
        }
        val retryingTask = FixedRetryingTask(
            getterTask,
            deviceInfo,
            logger,
            TrackingEventErrorClassifier.instance,
            null,
            listOf(1, 1, 1),
        )
        val result = IntegrityTokenProvider.IntegrityRetryGetterTask(retryingTask, logger).execute()
        verify(getterTask, times(3)).execute()
        Assert.assertEquals(expectedResult, result)
    }

    @Test
    fun getter_alreadySentSuccess() = runBlocking {
        databaseInterface.openAttribution().use {
            it.setIntegrityTokenSent(true)
        }
        val logger = mock<HttpLogger>()
        val secretProviderTask = TestAsyncFuture<String>("")

        val provider = IntegrityTokenProvider(
            TaskExecutorTest(),
            context,
            deviceInfo,
            null,
            RetryConfig.TEST_INTEGRITY_CONFIG,
        )

        val getterTask = IntegrityTokenProvider.IntegrityTokenGetterTask(
            context,
            logger,
            secretProviderTask,
            TestAsyncFuture("123"),
            databaseInterface,
            provider,
        )

        val result = getterTask.execute()

        Assert.assertTrue(result.previouslySent)
        Assert.assertTrue(result.token.isNullOrEmpty())
        Assert.assertEquals(null, result.integrityException)
    }

    @Test
    fun getter_success() = runBlocking {
        val token = UUID.randomUUID().toString()
        val integrityToken = IntegrityToken(token)
        val logger = mock<HttpLogger>()
        val secretProviderTask = TestAsyncFuture<String>("")
        val provider = IntegrityTokenProvider(
            TaskExecutorTest(),
            context,
            deviceInfo,
            null,
            RetryConfig.TEST_INTEGRITY_CONFIG,
        )

        val mockStandardTokenProvider = mock<StandardIntegrityTokenProvider>()
        val standardTokenProviderTask = StandardTokenProviderTask(integrityToken)
        provider.tokenProvider = mockStandardTokenProvider
        whenever(mockStandardTokenProvider.request(any())).then {
            standardTokenProviderTask
        }

        val getterTask = IntegrityTokenProvider.IntegrityTokenGetterTask(
            context,
            logger,
            secretProviderTask,
            TestAsyncFuture("123"),
            databaseInterface,
            provider,
        )
        val result = getterTask.execute()

        Assert.assertTrue(!result.previouslySent)
        Assert.assertEquals(token, result.token)
        Assert.assertEquals(null, result.integrityException)
    }

    @Test
    fun getter_failToGetTokenRetryAble() = runBlocking {
        var result: IntegrityException? = null
        val errorCode = -3
        val exception = mock<StandardIntegrityException>()
        val logger = mock<HttpLogger>()
        val secretProviderTask = TestAsyncFuture<String>("")
        val provider = IntegrityTokenProvider(
            TaskExecutorTest(),
            context,
            deviceInfo,
            null,
            RetryConfig.TEST_INTEGRITY_CONFIG,
        )
        val mockStandardTokenProvider = mock<StandardIntegrityTokenProvider>()
        provider.tokenProvider = mockStandardTokenProvider
        val standardTokenProviderTask = StandardTokenProviderTask(integrityException = exception)
        whenever(mockStandardTokenProvider.request(any())).then {
            standardTokenProviderTask
        }
        whenever(exception.getErrorCode()).then {
            errorCode
        }

        val getterTask = IntegrityTokenProvider.IntegrityTokenGetterTask(
            context,
            logger,
            secretProviderTask,
            TestAsyncFuture(UUID.randomUUID().toString()),
            databaseInterface,
            provider,
        )

        try {
            getterTask.execute()
        } catch (exception: Exception) {
            result = exception as IntegrityException
        }

        Assert.assertEquals(errorCode, result?.errorCode)
        Assert.assertEquals(true, result?.isRetryAbleErrorCode)
    }

    @Test
    fun getter_failToGetTokenNonRetryAble() = runBlocking {
        var result: IntegrityTokenData? = null
        val errorCode = -1
        val exception = mock<StandardIntegrityException>()
        val logger = mock<HttpLogger>()
        val secretProviderTask = TestAsyncFuture<String>("")
        val provider = IntegrityTokenProvider(
            TaskExecutorTest(),
            context,
            deviceInfo,
            null,
            RetryConfig.TEST_INTEGRITY_CONFIG,
        )
        val mockStandardTokenProvider = mock<StandardIntegrityTokenProvider>()
        provider.tokenProvider = mockStandardTokenProvider
        val standardTokenProviderTask = StandardTokenProviderTask(integrityException = exception)
        whenever(mockStandardTokenProvider.request(any())).then {
            standardTokenProviderTask
        }
        whenever(exception.getErrorCode()).then {
            errorCode
        }

        val getterTask = IntegrityTokenProvider.IntegrityTokenGetterTask(
            context,
            logger,
            secretProviderTask,
            TestAsyncFuture(UUID.randomUUID().toString()),
            databaseInterface,
            provider,
        )

        try {
            result = getterTask.execute()
        } catch (exception: Exception) {
            fail("Non-retryAble should not throw Exception")
        }

        Assert.assertEquals(errorCode, result?.integrityException?.errorCode)
        Assert.assertEquals(false, result?.integrityException?.isRetryAbleErrorCode)
    }

    @Test
    fun getter_failToGetWithUnknownException() = runBlocking {
        var result: IntegrityTokenData?
        val exception = mock<Exception>()
        val logger = mock<HttpLogger>()
        val secretProviderTask = TestAsyncFuture("")
        val provider = IntegrityTokenProvider(
            TaskExecutorTest(),
            context,
            deviceInfo,
            null,
            RetryConfig.TEST_INTEGRITY_CONFIG,
        )
        val mockStandardTokenProvider = mock<StandardIntegrityTokenProvider>()
        provider.tokenProvider = mockStandardTokenProvider
        val standardTokenProviderTask = StandardTokenProviderTask(integrityException = exception)
        whenever(mockStandardTokenProvider.request(any())).then {
            standardTokenProviderTask
        }

        val getterTask = IntegrityTokenProvider.IntegrityTokenGetterTask(
            context,
            logger,
            secretProviderTask,
            TestAsyncFuture(UUID.randomUUID().toString()),
            databaseInterface,
            provider,
        )

        try {
            result = getterTask.execute()
        } catch (exception: Exception) {
            result = IntegrityTokenData(integrityException = exception as IntegrityException)
        }

        Assert.assertEquals(UNKNOWN_ERROR_CODE, result?.integrityException?.errorCode)
    }

    @Test
    fun getter_successButEmptyWithUnknownError() = runBlocking {
        val token: String = ""
        val integrityToken = IntegrityToken(token)
        val logger = mock<HttpLogger>()
        val secretProviderTask = TestAsyncFuture<String>("")
        val provider = IntegrityTokenProvider(
            TaskExecutorTest(),
            context,
            deviceInfo,
            null,
            RetryConfig.TEST_INTEGRITY_CONFIG,
        )
        val mockStandardTokenProvider = mock<StandardIntegrityTokenProvider>()
        val standardTokenProviderTask = StandardTokenProviderTask(integrityToken)
        provider.tokenProvider = mockStandardTokenProvider
        whenever(mockStandardTokenProvider.request(any())).then {
            standardTokenProviderTask
        }

        val getterTask = IntegrityTokenProvider.IntegrityTokenGetterTask(
            context,
            logger,
            secretProviderTask,
            TestAsyncFuture(UUID.randomUUID().toString()),
            databaseInterface,
            provider,
        )
        val result = getterTask.execute()

        Assert.assertTrue(!result.previouslySent)
        Assert.assertEquals(token, result.token)
        Assert.assertEquals(UNKNOWN_ERROR_CODE, result.integrityException?.errorCode)
    }

    internal class TestHttpClient : BaseTestHttpClient() {
        override suspend fun sendAttributionRequest(logger: Logger, body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            return Result.success(null)
        }
    }
}
