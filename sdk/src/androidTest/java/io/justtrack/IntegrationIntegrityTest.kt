package io.justtrack

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import io.justtrack.api.DefaultAttributionApi
import io.justtrack.api.IntegrityApi
import io.justtrack.database.Database
import io.justtrack.dtos.DTOIntegrityToken
import io.justtrack.integrity.IntegrityToken
import io.justtrack.integrity.StandardTokenProviderTask
import io.justtrack.publicInterface.SdkTest
import junit.framework.TestCase.fail
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class IntegrationIntegrityTest {
    private lateinit var context: Context
    private val formatter = Formatter
    private val retryConfig = RetryConfig(1, 1, 1, RetryConfig.TEST_INTEGRITY_CONFIG)

    @Before
    fun createSdk() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
    }

    @Test
    fun secret_generated_and_sent_successfully() = runBlocking {
        var reportAttributionRequestJSONObject: JSONObject? = null
        val httpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                reportAttributionRequestJSONObject = body.toJSON(formatter)
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                return false
            }
        }
        val apis = IntegrityApis(httpListener)

        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application),
            SdkTest.API_TOKEN,
        )
        builder.setAttributionRetryDelaySeconds(3)
        var sdk: JustTrackSdkImpl? = createForTesting(
            builder,
            retryConfig,
            null,
            null,
            attributionApi = apis,
            integrityApi = apis,
        )

        val secret = sdk?.integritySecret?.await()
        sdk?.attribution?.await()
        Assert.assertTrue(!secret.isNullOrEmpty())
        val dtoSecret = reportAttributionRequestJSONObject?.getJSONObject("parameters")?.getString("integritySecret")
        Assert.assertEquals(secret, dtoSecret)

        sdk?.shutdown()

        sdk = createForTesting(
            builder,
            retryConfig,
            null,
            null,
            attributionApi = apis,
            integrityApi = apis,
        )

        val reOpenSecret = sdk.integritySecret.await()
        Assert.assertEquals(secret, reOpenSecret)
    }

    @Test
    fun secret_attribution_report_fail_on_first_attempt_then_retry_successfully() = runBlocking {
        var requestJSONObject: JSONObject? = null
        val retryCount = AtomicInteger(0)
        val httpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                requestJSONObject = body.toJSON(formatter)
                return retryCount.getAndIncrement() != 0
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                return false
            }
        }
        val apis = IntegrityApis(httpListener)

        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application),
            SdkTest.API_TOKEN,
        )
        val sdk: JustTrackSdkImpl = createForTesting(
            builder,
            retryConfig,
            null,
            null,
            attributionApi = apis,
            integrityApi = apis,
        )

        val secret = sdk.integritySecret.await()
        sdk.attribution.await()
        Assert.assertTrue(!secret.isNullOrEmpty())
        val dtoSecret = requestJSONObject?.getJSONObject("parameters")?.getString("integritySecret")
        Assert.assertEquals(secret, dtoSecret)
    }

    @Test
    fun secret_attribution_report_fail_then_reopen_application_successfully() = runBlocking {
        var requestJSONObject: JSONObject? = null
        val failHttpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                return false
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                return false
            }
        }

        val failApiProvider = IntegrityApis(failHttpListener)

        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application),
            SdkTest.API_TOKEN,
        )
        builder.setAttributionRetryDelaySeconds(3)
        var sdk: JustTrackSdkImpl = createForTesting(
            builder,
            retryConfig,
            null,
            null,
            attributionApi = failApiProvider,
            integrityApi = failApiProvider,
        )

        val secret = sdk.integritySecret.await()
        try {
            sdk.attribution.await()
            fail("Fail attribution should throw exception")
        } catch (exception: Exception) {
            // nop
        }
        Assert.assertTrue(!secret.isNullOrEmpty())

        sdk.shutdown()

        val successHttpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                requestJSONObject = body.toJSON(formatter)
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                return false
            }
        }
        val successApis = IntegrityApis(successHttpListener)

        // restart sdk
        sdk = createForTesting(
            builder,
            retryConfig,
            null,
            null,
            attributionApi = successApis,
            integrityApi = successApis,
        )

        val reOpenSecret = sdk.integritySecret.await()
        sdk.attribution.await()
        Assert.assertEquals(secret, reOpenSecret)
        val dtoSecret = requestJSONObject?.getJSONObject("parameters")?.getString("integritySecret")
        Assert.assertEquals(secret, dtoSecret)
    }

    @Test
    fun integrity_token_request_and_sent_successfully() = runBlocking {
        var requestJSONObject: JSONObject? = null
        val httpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                requestJSONObject = body.toJSON(formatter)
                return true
            }
        }

        val apis = IntegrityApis(httpListener)
        val mockStandardTokenProvider = mock<StandardIntegrityTokenProvider>()
        val standardTokenProviderTask = StandardTokenProviderTask(IntegrityToken(UUID.randomUUID().toString()))
        whenever(mockStandardTokenProvider.request(any())).then {
            standardTokenProviderTask
        }

        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application),
            SdkTest.API_TOKEN,
        )
        var sdk: JustTrackSdkImpl = spy(
            createForTesting(
                builder,
                retryConfig,
                null,
                mockStandardTokenProvider,
                attributionApi = apis,
                integrityApi = apis,
            ),
        )

        val secret = sdk.integritySecret.await()
        sdk.attribution.await()
        val sendTokenResult = sdk.integrityTokenPublisher.getCurrentFuture()!!.await()

        Assert.assertTrue(!secret.isNullOrEmpty())
        Assert.assertTrue(sendTokenResult)

        val reportedBody = DTOIntegrityToken(requestJSONObject!!)

        Assert.assertTrue(!reportedBody.integrityToken.isNullOrEmpty())
        Assert.assertTrue(reportedBody.errorMessage.isNullOrEmpty())
        Assert.assertTrue(reportedBody.errorCode == null)

        sdk.shutdown()

        var isCallAgain = false
        val reOpenHttpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                isCallAgain = true
                return true
            }
        }
        val reOpenApis = IntegrityApis(reOpenHttpListener)

        // restart sdk
        sdk = createForTesting(
            builder,
            retryConfig,
            null,
            mockStandardTokenProvider,
            attributionApi = reOpenApis,
            integrityApi = reOpenApis,
        )
        val result = sdk.integrityTokenPublisher.getCurrentFuture()!!.await()
        Assert.assertTrue(result)
        Assert.assertFalse(isCallAgain)
    }

    @Test
    fun integrity_token_request_fail_and_retry_and_sent_successfully() = runBlocking {
        val errorCode = -3
        val integrityToken = UUID.randomUUID().toString()
        val mockStandardTokenProvider = mock<StandardIntegrityTokenProvider>()
        val exception = mock<StandardIntegrityException>()
        val standardTokenProviderTask = StandardTokenProviderTask(IntegrityToken(integrityToken), integrityException = exception, 1)
        var requestJSONObject: JSONObject? = null
        val httpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                requestJSONObject = body.toJSON(formatter)
                return true
            }
        }

        val apis = IntegrityApis(httpListener)
        whenever(mockStandardTokenProvider.request(any())).then {
            standardTokenProviderTask
        }
        whenever(exception.getErrorCode()).then {
            errorCode
        }
        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application),
            SdkTest.API_TOKEN,
        )
        builder.setAttributionRetryDelaySeconds(3)
        var sdk: JustTrackSdkImpl = spy(
            createForTesting(
                builder,
                retryConfig,
                null,
                mockStandardTokenProvider,
                attributionApi = apis,
                integrityApi = apis,
            ),
        )

        val secret = sdk.integritySecret.await()
        sdk.attribution.await()
        val sendTokenResult = sdk.publishIntegrityToken().await()

        Assert.assertTrue(!secret.isNullOrEmpty())
        Assert.assertTrue(sendTokenResult)

        val reportedBody = DTOIntegrityToken(requestJSONObject!!)
        Assert.assertTrue(!reportedBody.integrityToken.isNullOrEmpty())
        Assert.assertTrue(reportedBody.errorMessage.isNullOrEmpty())
        Assert.assertTrue(reportedBody.errorCode == null)

        sdk.shutdown()

        var isCallAgain = false
        val reOpenHttpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                isCallAgain = true
                return true
            }
        }
        val reOpenApis = IntegrityApis(reOpenHttpListener)
        // restart sdk
        sdk = createForTesting(
            builder,
            retryConfig,
            null,
            mockStandardTokenProvider,
            attributionApi = reOpenApis,
            integrityApi = reOpenApis,
        )
        val result = sdk.integrityTokenPublisher.getCurrentFuture()!!.await()
        Assert.assertTrue(result)
        Assert.assertFalse(isCallAgain)
    }

    @Test
    fun integrity_request_fail_with_retryAble_and_and_sent_successfully() = runBlocking {
        val errorCode = -3
        val mockStandardTokenProvider = mock<StandardIntegrityTokenProvider>()
        val exception = mock<StandardIntegrityException>()
        val standardTokenProviderTask = StandardTokenProviderTask(integrityException = exception)
        var requestJSONObject: JSONObject? = null
        val httpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                requestJSONObject = body.toJSON(formatter)
                return true
            }
        }

        val apis = IntegrityApis(httpListener)

        whenever(mockStandardTokenProvider.request(any())).then {
            standardTokenProviderTask
        }
        whenever(exception.getErrorCode()).then {
            errorCode
        }

        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application),
            SdkTest.API_TOKEN,
        )
        builder.setAttributionRetryDelaySeconds(3)
        var sdk: JustTrackSdkImpl = spy(
            createForTesting(
                builder,
                retryConfig,
                null,
                mockStandardTokenProvider,
                attributionApi = apis,
                integrityApi = apis,
            ),
        )

        val secret = sdk.integritySecret.await()
        sdk.attribution.await()
        sdk.integrityToken.await()
        val sendTokenResult = sdk.integrityTokenPublisher.getCurrentFuture()!!.await()

        Assert.assertTrue(!secret.isNullOrEmpty())
        Assert.assertTrue(sendTokenResult)

        val reportedBody = DTOIntegrityToken(requestJSONObject!!)
        Assert.assertTrue(reportedBody.integrityToken.isNullOrEmpty())
        Assert.assertEquals(errorCode, reportedBody.errorCode)

        sdk.shutdown()

        var isCallAgain = false
        val reOpenHttpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                isCallAgain = true
                return true
            }
        }
        val reOpenApis = IntegrityApis(reOpenHttpListener)
        // restart sdk
        sdk = createForTesting(
            builder,
            retryConfig,
            null,
            mockStandardTokenProvider,
            attributionApi = reOpenApis,
            integrityApi = reOpenApis,
        )
        val result = sdk.integrityTokenPublisher.getCurrentFuture()!!.await()
        Assert.assertTrue(result)
        Assert.assertTrue(isCallAgain)
    }

    @Test
    fun integrity_request_success_fail_to_publish_but_retry_successfully() = runBlocking {
        val mockStandardTokenProvider = mock<StandardIntegrityTokenProvider>()
        val integrityToken = UUID.randomUUID().toString()
        val standardTokenProviderTask = StandardTokenProviderTask(tokenResult = IntegrityToken(integrityToken))
        whenever(mockStandardTokenProvider.request(any())).then {
            standardTokenProviderTask
        }
        val retryCount = AtomicInteger(0)
        var requestJSONObject: JSONObject? = null
        val httpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                if (retryCount.getAndIncrement() > 1) {
                    requestJSONObject = body.toJSON(formatter)
                    return true
                } else {
                    return false
                }
            }
        }
        val apis = spy(IntegrityApis(httpListener))

        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application),
            SdkTest.API_TOKEN,
        )
        builder.setAttributionRetryDelaySeconds(3)
        var sdk: JustTrackSdkImpl = spy(
            createForTesting(
                builder,
                retryConfig,
                null,
                mockStandardTokenProvider,
                attributionApi = apis,
                integrityApi = apis,
            ),
        )

        val secret = sdk.integritySecret.await()
        sdk.attribution.await()
        sdk.integrityToken.await()
        val sendTokenResult = sdk.integrityTokenPublisher.getCurrentFuture()!!.await()

        Assert.assertTrue(!secret.isNullOrEmpty())
        Assert.assertTrue(sendTokenResult)

        val reportedBody = DTOIntegrityToken(requestJSONObject!!)
        verify(apis, times(3)).reportIntegrity(any(), any())
        Assert.assertEquals(integrityToken, reportedBody.integrityToken.toString())
        Assert.assertEquals(null, reportedBody.errorCode)
    }

    @Test
    fun integrity_request_success_fail_to_publish_reopen_successfully() = runBlocking {
        val mockStandardTokenProvider = mock<StandardIntegrityTokenProvider>()
        val integrityToken = UUID.randomUUID().toString()
        val standardTokenProviderTask = StandardTokenProviderTask(tokenResult = IntegrityToken(integrityToken))
        whenever(mockStandardTokenProvider.request(any())).then {
            standardTokenProviderTask
        }
        val failHttpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                return false
            }
        }
        val failApis = spy(IntegrityApis(failHttpListener))

        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application),
            SdkTest.API_TOKEN,
        )
        var sdk: JustTrackSdkImpl = spy(
            createForTesting(
                builder,
                retryConfig,
                null,
                mockStandardTokenProvider,
                attributionApi = failApis,
                integrityApi = failApis,
            ),
        )

        val secret = sdk.integritySecret.await()
        sdk.attribution.await()
        sdk.integrityToken.await()
        val sendTokenResult = sdk.integrityTokenPublisher.getCurrentFuture()!!.await()

        Assert.assertTrue(!secret.isNullOrEmpty())
        Assert.assertFalse(sendTokenResult)

        verify(failApis, times(4)).reportIntegrity(any(), any())

        sdk.shutdown()

        // restart sdk
        var requestJSONObject: JSONObject? = null
        val successHttpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                requestJSONObject = body.toJSON(formatter)
                return true
            }
        }
        val successApis = spy(IntegrityApis(successHttpListener))
        sdk = createForTesting(
            builder,
            retryConfig,
            null,
            mockStandardTokenProvider,
            attributionApi = successApis,
            integrityApi = successApis,
        )
        val result = sdk.integrityTokenPublisher.getCurrentFuture()!!.await()
        val reportedBody = DTOIntegrityToken(requestJSONObject!!)
        Assert.assertTrue(result)
        Assert.assertTrue(!reportedBody.integrityToken.isNullOrEmpty())
        Assert.assertEquals(null, reportedBody.errorCode)
    }

    @Test
    fun integrity_request_fail_with_non_retryAble_and_publish_successfully() = runBlocking {
        val errorCode = -1
        val mockStandardTokenProvider = mock<StandardIntegrityTokenProvider>()
        val exception = mock<StandardIntegrityException>()
        val standardTokenProviderTask = StandardTokenProviderTask(integrityException = exception)
        whenever(mockStandardTokenProvider.request(any())).then {
            standardTokenProviderTask
        }
        whenever(exception.getErrorCode()).then {
            errorCode
        }

        var requestJSONObject: JSONObject? = null
        val httpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                requestJSONObject = body.toJSON(formatter)
                return true
            }
        }
        val integrityApis = IntegrityApis(httpListener)

        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application),
            SdkTest.API_TOKEN,
        )
        var sdk: JustTrackSdkImpl = spy(
            createForTesting(
                builder,
                retryConfig,
                null,
                mockStandardTokenProvider,
                attributionApi = integrityApis,
                integrityApi = integrityApis,
            ),
        )

        val secret = sdk.integritySecret.await()
        sdk.attribution.await()
        sdk.integrityToken.await()
        val sendTokenResult = sdk.integrityTokenPublisher.getCurrentFuture()!!.await()

        Assert.assertTrue(!secret.isNullOrEmpty())
        Assert.assertTrue(sendTokenResult)

        val reportedBody = DTOIntegrityToken(requestJSONObject!!)

        Assert.assertTrue(reportedBody.integrityToken.isNullOrEmpty())
        Assert.assertEquals(errorCode, reportedBody.errorCode)

        sdk.shutdown()

        var isCallAgain = false
        val reOpenHttpListener = object : HttpClientListener {
            override fun reportAttribution(body: JSONEncodable): Boolean {
                return true
            }

            override fun reportIntegrity(body: JSONEncodable): Boolean {
                isCallAgain = true
                return true
            }
        }
        val reOpenApis = IntegrityApis(reOpenHttpListener)
        // restart sdk
        sdk = createForTesting(
            builder,
            retryConfig,
            null,
            mockStandardTokenProvider,
            attributionApi = reOpenApis,
            integrityApi = reOpenApis,
        )
        val result = sdk.integrityTokenPublisher.getCurrentFuture()!!.await()
        Assert.assertTrue(result)
        Assert.assertFalse(isCallAgain)
    }

    internal open class IntegrityApis(private val listener: HttpClientListener) : DefaultAttributionApi(), IntegrityApi {
        override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            val result = listener.reportAttribution(body)
            if (result) {
                return Result.success(attributionResponse)
            } else {
                return Result.failure(Exception("Attribution Error"))
            }
        }

        override suspend fun reportIntegrity(body: JSONEncodable, installId: String): Result<JSONObject> {
            val result = listener.reportIntegrity(body)
            if (result) {
                return Result.success(JSONObject("{}"))
            } else {
                return Result.failure(Exception("Integrity Error"))
            }
        }
    }

    internal interface HttpClientListener {
        fun reportAttribution(body: JSONEncodable): Boolean
        fun reportIntegrity(body: JSONEncodable): Boolean
    }

    companion object {
        val attributionResponse = try {
            val user = JSONObject()
            user.put("id", UUID.randomUUID().toString())
            user.put("installId", UUID.randomUUID().toString())
            user.put("type", "acquisition")
            user.put("testGroup", 2)
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

            val response = JSONObject()
            response.put("user", user)
            response.put("attribution", attribution)
            response
        } catch (exception: JSONException) {
            throw RuntimeException(exception)
        }
    }
}
