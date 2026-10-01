package io.justtrack.api

import io.justtrack.Environment
import io.justtrack.Formatter
import io.justtrack.HttpClient
import io.justtrack.IPProtocol
import io.justtrack.JSONEncodable
import io.justtrack.dtos.DTOPublishCustomUserIdRequest
import io.justtrack.log.Logger
import io.justtrack.okhttp.Request
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AttributionApiTest {

    private val attributionUrl = "https://api.justtrack.io/v1/attribution"
    private val ipv4Url = "https://api.justtrack.io/v0/sign-ip-v4"
    private val ipv6Url = "https://api.justtrack.io/v0/sign-ip-v6"
    private val customUserIdUrl = "https://api.justtrack.io/v0/publish-custom-user-id"
    private val firebaseUrl = "https://api.justtrack.io/v0/publish-firebase-app-instance-id"

    private val environment = mock<Environment>().apply {
        whenever(getUrl(any())).thenAnswer { invocation ->
            when (invocation.getArgument<Environment.Route>(0)) {
                Environment.Route.ATTRIBUTION -> attributionUrl
                Environment.Route.SIGN_IP_V4 -> ipv4Url
                Environment.Route.SIGN_IP_V6 -> ipv6Url
                Environment.Route.PUBLISH_CUSTOM_USER_ID -> customUserIdUrl
                Environment.Route.PUBLISH_FIREBASE_APP_INSTANCE_ID -> firebaseUrl
                else -> error("Unexpected route: ${invocation.getArgument<Environment.Route>(0)}")
            }
        }
    }
    private val logger = TestApiLogger()
    private val headerProvider = TestHeaderProvider()

    private val emptyBody = object : JSONEncodable {
        override fun toJSON(formatter: Formatter): JSONObject = JSONObject()
    }

    // region sendAttributionRequest

    @Test
    fun sendAttributionRequest_success_returnsBodyAndPostsToAttributionUrl() = runBlocking {
        val responseBody = JSONObject("""{"ok":true}""")
        val capturing = CapturingHttpClient(Result.success(responseBody))
        val api = AttributionApiImpl(capturing, headerProvider, environment, logger)

        val result = api.sendAttributionRequest(emptyBody, advertiserId = "adv-1")

        assertTrue(result.isSuccess)
        assertEquals(responseBody, result.getOrNull())
        assertEquals(AttributionApiImpl.GET_ATTRIBUTION_REQUEST_NAME, capturing.lastRequestName)
        assertEquals(attributionUrl, capturing.lastRequest?.url?.toString())
        assertEquals("POST", capturing.lastRequest?.method)
    }

    @Test
    fun sendAttributionRequest_supportsNullAdvertiserId() = runBlocking {
        val capturing = CapturingHttpClient(Result.success(JSONObject()))
        val api = AttributionApiImpl(capturing, headerProvider, environment, logger)

        val result = api.sendAttributionRequest(emptyBody, advertiserId = null)

        assertTrue(result.isSuccess)
    }

    @Test
    fun sendAttributionRequest_returnsFailureWhenHttpClientFails() = runBlocking {
        val capturing = CapturingHttpClient(Result.failure(Exception("network error")))
        val api = AttributionApiImpl(capturing, headerProvider, environment, logger)

        val result = api.sendAttributionRequest(emptyBody, advertiserId = "adv-1")

        assertTrue(result.isFailure)
        assertEquals("network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun sendAttributionRequest_returnsFailureWhenBodyEncodingThrows() = runBlocking {
        val capturing = CapturingHttpClient(Result.success(JSONObject()))
        val api = AttributionApiImpl(capturing, headerProvider, environment, logger)
        val throwingBody = object : JSONEncodable {
            override fun toJSON(formatter: Formatter): JSONObject = error("encode failed")
        }

        val result = api.sendAttributionRequest(throwingBody, advertiserId = "adv-1")

        assertTrue(result.isFailure)
    }

    // endregion

    // region getSignedIpClaim

    @Test
    fun getSignedIpClaim_ipv4_usesIpv4RouteRequestNameAndGet() = runBlocking {
        val responseBody = JSONObject("""{"claim":"v4"}""")
        val capturing = CapturingHttpClient(Result.success(responseBody))
        val api = AttributionApiImpl(capturing, headerProvider, environment, logger)

        val result = api.getSignedIpClaim(IPProtocol.IPv4, advertiserId = "adv")

        assertTrue(result.isSuccess)
        assertEquals(responseBody, result.getOrNull())
        assertEquals(IPProtocol.IPv4.requestName, capturing.lastRequestName)
        assertEquals(ipv4Url, capturing.lastRequest?.url?.toString())
        assertEquals("GET", capturing.lastRequest?.method)
    }

    @Test
    fun getSignedIpClaim_ipv6_usesIpv6RouteAndRequestName() = runBlocking {
        val capturing = CapturingHttpClient(Result.success(JSONObject()))
        val api = AttributionApiImpl(capturing, headerProvider, environment, logger)

        api.getSignedIpClaim(IPProtocol.IPv6, advertiserId = null)

        assertEquals(IPProtocol.IPv6.requestName, capturing.lastRequestName)
        assertEquals(ipv6Url, capturing.lastRequest?.url?.toString())
    }

    @Test
    fun getSignedIpClaim_returnsFailureWhenHttpClientFails() = runBlocking {
        val capturing = CapturingHttpClient(Result.failure(Exception("boom")))
        val api = AttributionApiImpl(capturing, headerProvider, environment, logger)

        val result = api.getSignedIpClaim(IPProtocol.IPv4, advertiserId = "adv")

        assertTrue(result.isFailure)
    }

    // endregion

    // region sendCustomUserId

    @Test
    fun sendCustomUserId_success_postsExpectedBodyToCustomUserIdUrl() = runBlocking {
        val capturing = CapturingHttpClient(Result.success(JSONObject()))
        val api = AttributionApiImpl(capturing, headerProvider, environment, logger)
        val body = DTOPublishCustomUserIdRequest(installId = "install-1", customUserId = "user-1")

        val result = api.sendCustomUserId(body, advertiserId = "adv", uuid = "uuid", installId = "install-1")

        assertTrue(result.isSuccess)
        assertEquals(AttributionApiImpl.SEND_CUSTOM_USER_ID_REQUEST_NAME, capturing.lastRequestName)
        assertEquals(customUserIdUrl, capturing.lastRequest?.url?.toString())
        assertEquals("POST", capturing.lastRequest?.method)
        assertNotNull(capturing.lastRequest?.body)
    }

    @Test
    fun sendCustomUserId_returnsFailureWhenHttpClientFails() = runBlocking {
        val capturing = CapturingHttpClient(Result.failure(Exception("nope")))
        val api = AttributionApiImpl(capturing, headerProvider, environment, logger)
        val body = DTOPublishCustomUserIdRequest(installId = "i", customUserId = "u")

        val result = api.sendCustomUserId(body, advertiserId = null, uuid = "uuid", installId = "i")

        assertTrue(result.isFailure)
    }

    // endregion

    // region sendFirebaseAppInstanceId

    @Test
    fun sendFirebaseAppInstanceId_success_postsToFirebaseUrl() = runBlocking {
        val capturing = CapturingHttpClient(Result.success(JSONObject()))
        val api = AttributionApiImpl(capturing, headerProvider, environment, logger)

        val result = api.sendFirebaseAppInstanceId(emptyBody, advertiserId = "adv", uuid = "uuid", installId = "iid")

        assertTrue(result.isSuccess)
        assertEquals(AttributionApiImpl.SEND_FIREBASE_APP_INSTANCE_ID_REQUEST_NAME, capturing.lastRequestName)
        assertEquals(firebaseUrl, capturing.lastRequest?.url?.toString())
        assertEquals("POST", capturing.lastRequest?.method)
    }

    @Test
    fun sendFirebaseAppInstanceId_returnsFailureWhenHttpClientFails() = runBlocking {
        val capturing = CapturingHttpClient(Result.failure(Exception("err")))
        val api = AttributionApiImpl(capturing, headerProvider, environment, logger)

        val result = api.sendFirebaseAppInstanceId(emptyBody, advertiserId = null, uuid = "uuid", installId = "iid")

        assertTrue(result.isFailure)
    }

    @Test
    fun sendFirebaseAppInstanceId_returnsFailureWhenBodyEncodingThrows() = runBlocking {
        val capturing = CapturingHttpClient(Result.success(JSONObject()))
        val api = AttributionApiImpl(capturing, headerProvider, environment, logger)
        val throwingBody = object : JSONEncodable {
            override fun toJSON(formatter: Formatter): JSONObject = error("encode failed")
        }

        val result = api.sendFirebaseAppInstanceId(throwingBody, advertiserId = "adv", uuid = "u", installId = "i")

        assertTrue(result.isFailure)
    }

    // endregion

    // region companion

    @Test
    fun companion_requestNames_areStable() {
        assertEquals("GetAttribution", AttributionApiImpl.GET_ATTRIBUTION_REQUEST_NAME)
        assertEquals("SendCustomUserId", AttributionApiImpl.SEND_CUSTOM_USER_ID_REQUEST_NAME)
        assertEquals("SendFirebaseAppInstanceId", AttributionApiImpl.SEND_FIREBASE_APP_INSTANCE_ID_REQUEST_NAME)
    }

    // endregion

    private class CapturingHttpClient(private val response: Result<JSONObject>) : HttpClient {
        var lastRequest: Request? = null
        var lastRequestName: String? = null

        override suspend fun executeAsyncRequest(request: Request, requestName: String, logger: Logger): Result<JSONObject> {
            lastRequest = request
            lastRequestName = requestName
            delay(1)
            return response
        }

        override suspend fun executeAsyncJsonRequest(request: Request, requestName: String, logger: Logger): Result<HttpClient.JsonHttpResponse> =
            response.map { HttpClient.JsonHttpResponse(it, null) }
    }
}
