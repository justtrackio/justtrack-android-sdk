package io.justtrack

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.database.Database
import io.justtrack.exceptions.IntegrityException
import io.justtrack.log.Logger
import io.justtrack.util.ExecutorServiceFactory
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class IntegrityTokenPublisherTest {
    lateinit var context: Context
    private lateinit var databaseInterface: DatabaseInterface
    internal val formatter = Formatter
    private val executorBuilder = ExecutorServiceFactory {
        val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
        executor.allowCoreThreadTimeOut(true)
        executor
    }
    private lateinit var deviceInfo: DeviceInfo

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
    fun test_alreadySent() = runBlocking {
        val logger = mock<HttpLogger>()
        val httpClient = spy<TestIntegrityHttpClient>()
        val integrityTokenTask = TestAsyncFuture(IntegrityTokenData(previouslySent = true))
        val integrityTokenMethod: () -> TestAsyncFuture<IntegrityTokenData> = object : () -> TestAsyncFuture<IntegrityTokenData> {
            override fun invoke(): TestAsyncFuture<IntegrityTokenData> {
                return integrityTokenTask
            }
        }
        val installID = ValueFuture(UUID.randomUUID().toString())
        val sdk = TestSdk(context, executorBuilder, httpClient, false)
        val publisher = IntegrityTokenPublisher(sdk.taskExecutor, deviceInfo, RetryConfig.TEST_INTEGRITY_CONFIG)

        val result = publisher.publishIntegrityToken(
            logger,
            NetworkErrorLogger(),
            httpClient,
            databaseInterface,
            integrityTokenMethod,
            installID,
        ).await()
        verify(httpClient, times(0)).reportIntegrity(any(), any(), any())
        Assert.assertTrue(result)
    }

    @Test
    fun test_success() = runBlocking {
        val logger = mock<HttpLogger>()
        val token = UUID.randomUUID().toString()
        val httpClient = spy(TestIntegrityHttpClient(true))
        val integrityTokenTask = TestAsyncFuture(IntegrityTokenData(previouslySent = false, token = token))
        val integrityTokenMethod: () -> TestAsyncFuture<IntegrityTokenData> = object : () -> TestAsyncFuture<IntegrityTokenData> {
            override fun invoke(): TestAsyncFuture<IntegrityTokenData> {
                return integrityTokenTask
            }
        }
        val installID = ValueFuture(UUID.randomUUID().toString())
        val sdk = TestSdk(context, executorBuilder, httpClient, false)
        val publisher = IntegrityTokenPublisher(sdk.taskExecutor, deviceInfo, RetryConfig.TEST_INTEGRITY_CONFIG)

        val result = publisher.publishIntegrityToken(
            logger,
            NetworkErrorLogger(),
            httpClient,
            databaseInterface,
            integrityTokenMethod,
            installID,
        ).await()

        val argumentCaptor = argumentCaptor<JSONEncodable>()
        verify(httpClient, times(1)).reportIntegrity(any(), argumentCaptor.capture(), any())
        var isSent: Boolean?
        databaseInterface.openAttribution().use {
            isSent = it.isIntegrityTokenSent()
        }
        val inputDTO = DTOIntegrityToken(argumentCaptor.firstValue.toJSON(formatter))

        Assert.assertEquals(token, inputDTO.integrityToken)
        Assert.assertEquals(true, isSent)
        Assert.assertTrue(result)
    }

    @Test
    fun test_successRetryAble() = runBlocking {
        val logger = mock<HttpLogger>()
        val errorCode = -3
        val errorMessage = "retryAle error message"
        val httpClient = spy(TestIntegrityHttpClient(true))
        val integrityTokenTask = TestAsyncFuture(
            IntegrityTokenData(
                previouslySent = false,
                integrityException = IntegrityException(errorCode, true, errorMessage, Exception(errorMessage)),
            ),
        )
        val integrityTokenMethod: () -> TestAsyncFuture<IntegrityTokenData> = object : () -> TestAsyncFuture<IntegrityTokenData> {
            override fun invoke(): TestAsyncFuture<IntegrityTokenData> {
                return integrityTokenTask
            }
        }
        val installID = ValueFuture(UUID.randomUUID().toString())
        val sdk = TestSdk(context, executorBuilder, httpClient, false)
        val publisher = IntegrityTokenPublisher(sdk.taskExecutor, deviceInfo, RetryConfig.TEST_INTEGRITY_CONFIG)

        val result = publisher.publishIntegrityToken(
            logger,
            NetworkErrorLogger(),
            httpClient,
            databaseInterface,
            integrityTokenMethod,
            installID,
        ).await()
        val argumentCaptor = argumentCaptor<JSONEncodable>()
        verify(httpClient, times(1)).reportIntegrity(any(), argumentCaptor.capture(), any())
        var isSent: Boolean?
        databaseInterface.openAttribution().use {
            isSent = it.isIntegrityTokenSent()
        }
        val inputDTO = DTOIntegrityToken(argumentCaptor.firstValue.toJSON(formatter))

        Assert.assertEquals(null, inputDTO.integrityToken)
        Assert.assertEquals(errorCode, inputDTO.errorCode)
        Assert.assertEquals(errorMessage, inputDTO.errorMessage)
        Assert.assertEquals(false, isSent)
        Assert.assertTrue(result)
    }

    @Test
    fun test_successNonRetryAble() = runBlocking {
        val logger = mock<HttpLogger>()
        val errorCode = -1
        val errorMessage = "non retryAble error message"
        val httpClient = spy(TestIntegrityHttpClient(true))
        val integrityTokenTask = TestAsyncFuture(
            IntegrityTokenData(
                previouslySent = false,
                integrityException = IntegrityException(errorCode, false, errorMessage, Exception(errorMessage)),
            ),
        )
        val integrityTokenMethod: () -> TestAsyncFuture<IntegrityTokenData> = object : () -> TestAsyncFuture<IntegrityTokenData> {
            override fun invoke(): TestAsyncFuture<IntegrityTokenData> {
                return integrityTokenTask
            }
        }
        val installID = ValueFuture(UUID.randomUUID().toString())
        val sdk = TestSdk(context, executorBuilder, httpClient, false)
        val publisher = IntegrityTokenPublisher(sdk.taskExecutor, deviceInfo, RetryConfig.TEST_INTEGRITY_CONFIG)

        val result = publisher.publishIntegrityToken(
            logger,
            NetworkErrorLogger(),
            httpClient,
            databaseInterface,
            integrityTokenMethod,
            installID,
        ).await()
        val argumentCaptor = argumentCaptor<JSONEncodable>()
        verify(httpClient, times(1)).reportIntegrity(any(), argumentCaptor.capture(), any())
        var isSent: Boolean?
        databaseInterface.openAttribution().use {
            isSent = it.isIntegrityTokenSent()
        }
        val inputDTO = DTOIntegrityToken(argumentCaptor.firstValue.toJSON(formatter))

        Assert.assertEquals(null, inputDTO.integrityToken)
        Assert.assertEquals(errorCode, inputDTO.errorCode)
        Assert.assertEquals(errorMessage, inputDTO.errorMessage)
        Assert.assertEquals(true, isSent)
        Assert.assertTrue(result)
    }

    @Test
    fun test_fail() = runBlocking {
        val logger = mock<HttpLogger>()
        val token = UUID.randomUUID().toString()
        val errorMessage = "Fail"
        val httpClient = spy(TestIntegrityHttpClient(false, errorMessage))
        val integrityTokenTask = TestAsyncFuture(IntegrityTokenData(previouslySent = false, token = token))
        val integrityTokenMethod: () -> TestAsyncFuture<IntegrityTokenData> = object : () -> TestAsyncFuture<IntegrityTokenData> {
            override fun invoke(): TestAsyncFuture<IntegrityTokenData> {
                return integrityTokenTask
            }
        }
        val installID = ValueFuture(UUID.randomUUID().toString())
        val sdk = TestSdk(context, executorBuilder, httpClient, false)
        val publisher = IntegrityTokenPublisher(sdk.taskExecutor, deviceInfo, RetryConfig.TEST_INTEGRITY_CONFIG)

        val result = publisher.publishIntegrityToken(
            logger,
            NetworkErrorLogger(),
            httpClient,
            databaseInterface,
            integrityTokenMethod,
            installID,
        ).await()

        val argumentCaptor = argumentCaptor<JSONEncodable>()
        verify(httpClient, times(4)).reportIntegrity(any(), argumentCaptor.capture(), any())
        var isSent: Boolean?
        databaseInterface.openAttribution().use {
            isSent = it.isIntegrityTokenSent()
        }
        val inputDTO = DTOIntegrityToken(argumentCaptor.firstValue.toJSON(formatter))

        Assert.assertEquals(token, inputDTO.integrityToken)
        Assert.assertEquals(false, isSent)
        Assert.assertFalse(result)
    }

    internal open class TestIntegrityHttpClient(
        private val isReportIntegritySuccess: Boolean = true,
        private val reportIntegrityErrorMessage: String? = null,
    ) : BaseTestHttpClient() {
        override suspend fun reportIntegrity(logger: Logger, body: JSONEncodable, installId: String): Result<JSONObject> {
            if (isReportIntegritySuccess) {
                return Result.success(JSONObject("{}"))
            } else {
                return Result.failure(Exception(reportIntegrityErrorMessage))
            }
        }

        override suspend fun sendAttributionRequest(logger: Logger, body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            return Result.success(null)
        }
    }
}
