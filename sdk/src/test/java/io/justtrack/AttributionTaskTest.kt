package io.justtrack

import android.content.Intent
import android.net.Uri
import com.google.android.gms.appset.AppSetIdInfo
import io.justtrack.api.AttributionApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.installreferrer.api.ReferrerDetails
import io.justtrack.log.LoggerFields
import io.justtrack.util.InstallerSourceIdProvider
import io.justtrack.versions.SdkVersion
import io.justtrack.versions.VersionBundle
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ExecutionException

@RunWith(RobolectricTestRunner::class)
internal class AttributionTaskTest {
    private val userId = UUID.fromString("8a4929d4-b3f4-4593-84f9-b2fad0e9cc1e")
    private val installId = "install-id"
    private val logger: HttpLogger = mock()
    private val attributionDb: DatabaseAttributionInterface = mock()
    private val databaseInterface: DatabaseInterface = mock()
    private val idManager: AttributionIdManager = mock()

    @Test
    fun `successful attribution stores response logs metric and sends attribution input`() = runBlocking {
        val api = RecordingAttributionApi(Result.success(attributionJson()))
        val claimProvider = RecordingClaimProvider(claims("claim-1", timedOut = true))
        val appSetIdInfo = mock<AppSetIdInfo> {
            on { id }.thenReturn("app-set-id")
        }
        val task = newTask(
            api = api,
            claimProvider = claimProvider,
            appSetIdInfo = appSetIdInfo,
            advertiserIdInfo = TestAdvertiserIdInfo("advertiser-id", true),
        )

        val output = task.execute()

        assertTrue(claimProvider.refreshCalled)
        assertEquals(123L, claimProvider.timeout)
        assertEquals(userId, output.getAttributionResponse().getUserId())
        assertEquals(installId, output.getAttributionResponse().getInstallId())
        assertTrue(output.didClaimsTimeOut())
        assertNull(output.getRetargetingParameters())
        verify(attributionDb).setAttributionFinished(same(output.getAttributionResponse()))
        verify(logger).setUser(userId, installId)
        verify(logger).publishMetric(any(), any(), any<LoggerFields>())
        assertEquals("advertiser-id", api.advertiserId)

        val body = api.body!!.toJSON(Formatter)
        assertEquals("user-config", body.getJSONObject("user").getString("customUserId"))
        assertEquals("app-set-id", body.getJSONObject("user").getString("appSetId"))
        assertEquals("installer", body.getJSONObject("parameters").getString("installSource"))
        assertEquals("secret", body.getJSONObject("parameters").getString("integritySecret"))
        assertEquals("claim-1", body.getJSONArray("claims").getString(0))
    }

    @Test
    fun `referrer failure logs warning and continues without referrer`() = runBlocking {
        val referrerFailure = IOException("referrer failed")
        val api = RecordingAttributionApi(Result.success(attributionJson()))
        val task = newTask(api = api, referrerDetails = ErrorFuture(referrerFailure))

        task.execute()

        verify(logger).warn(eq("Failed to read install referrer"), any<ExecutionException>())
        val body = api.body!!.toJSON(Formatter)
        assertEquals(JSONObject.NULL, body["referrer"])
    }

    @Test
    fun `successful attribution parses already-installed retargeting launch`() = runBlocking {
        val url = "https://example.com/deeplink"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val task = newTask(api = RecordingAttributionApi(Result.success(attributionJson(retargetingUrl = url))), intent = intent)

        val output = task.execute()

        val retargeting = output.getRetargetingParameters()!!
        assertTrue(retargeting.wasAlreadyInstalled())
        assertEquals(url, retargeting.uri.toString())
        assertEquals("value", retargeting.parameters["key"])
        verify(logger).debug("Detected retargeting app launch of already installed app")
    }

    @Test
    fun `successful attribution parses fresh retargeting launch with missing intent values`() = runBlocking {
        val url = "https://example.com/target"
        val task = newTask(api = RecordingAttributionApi(Result.success(attributionJson(retargetingUrl = url))), intent = Intent())

        val output = task.execute()

        val retargeting = output.getRetargetingParameters()!!
        assertFalse(retargeting.wasAlreadyInstalled())
        verify(logger).debug(eq("Retargeting app launch, but app was not yet installed"), any<LoggerFields>())
    }

    @Test
    fun `successful attribution parses retargeting launch without intent`() = runBlocking {
        val url = "https://example.com/no-intent"
        val task = newTask(api = RecordingAttributionApi(Result.success(attributionJson(retargetingUrl = url))), intent = null)

        val output = task.execute()

        val retargeting = output.getRetargetingParameters()!!
        assertFalse(retargeting.wasAlreadyInstalled())
        assertEquals(url, retargeting.uri.toString())
        verify(logger).debug(eq("Retargeting app launch, but app was not yet installed"), any<LoggerFields>())
    }

    @Test
    fun `successful attribution parses action view retargeting launch with different url`() = runBlocking {
        val url = "https://example.com/retargeting-url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/intent-url"))
        val task = newTask(api = RecordingAttributionApi(Result.success(attributionJson(retargetingUrl = url))), intent = intent)

        val output = task.execute()

        val retargeting = output.getRetargetingParameters()!!
        assertFalse(retargeting.wasAlreadyInstalled())
        assertEquals(url, retargeting.uri.toString())
        verify(logger).debug(eq("Retargeting app launch, but app was not yet installed"), any<LoggerFields>())
    }

    @Test
    fun `null response success is wrapped as illegal state`() = runBlocking {
        val task = newTask(api = RecordingAttributionApi(Result.success(null)))

        val exception = expectIllegalState { task.execute() }

        assertEquals("Parsing server response for attribution failed", exception.message)
        assertTrue(exception.cause is AttributionTask.ParseAttributionException)
    }

    @Test
    fun `malformed response success is wrapped as illegal state`() = runBlocking {
        val task = newTask(api = RecordingAttributionApi(Result.success(JSONObject())))

        val exception = expectIllegalState { task.execute() }

        assertEquals("Parsing server response for attribution failed", exception.message)
        assertTrue(exception.cause is AttributionTask.ParseAttributionException)
    }

    @Test
    fun `api failure throws attribution exception`() = runBlocking {
        val cause = IOException("api failed")
        val task = newTask(api = RecordingAttributionApi(Result.failure(cause)))

        try {
            task.execute()
            fail("expected AttributionException")
        } catch (exception: AttributionException) {
            assertSame(cause, exception.cause)
        }
    }

    @Test
    fun `parse exception constructors keep message and cause`() {
        val cause = IOException("parse")

        assertEquals("message", AttributionTask.ParseAttributionException("message").message)
        val withCause = AttributionTask.ParseAttributionException("message", cause)
        assertEquals("message", withCause.message)
        assertSame(cause, withCause.cause)
    }

    private fun newTask(
        api: AttributionApi,
        intent: Intent? = null,
        claimProvider: ClaimProvider = RecordingClaimProvider(claims()),
        referrerDetails: AsyncFuture<ReferrerDetails?> = ValueFuture(null),
        appSetIdInfo: AppSetIdInfo? = null,
        advertiserIdInfo: AdvertiserIdInfo = TestAdvertiserIdInfo("advertiser-id", false),
    ): AttributionTask {
        whenever(databaseInterface.openAttribution()).thenReturn(attributionDb)
        runBlocking {
            whenever(attributionDb.setAttributionFinished(any())).thenReturn(true)
        }
        whenever(idManager.getOrCreateInstallId()).thenReturn(ValueFuture(installId))
        return AttributionTask(
            intent = intent,
            databaseInterface = databaseInterface,
            attributionParams = AttributionTask.AttributionParams(
                idManager = idManager,
                userIdProvider = UserIdProvider { ValueFuture(userId.toString()) },
                advertiserId = ValueFuture(advertiserIdInfo),
                referrerDetails = referrerDetails,
                appSetIdInfoFuture = ValueFuture(appSetIdInfo),
                trackingId = "tracking-id",
                trackingProvider = "tracking-provider",
                claimProvider = claimProvider,
                claimTimeout = 123L,
                sdkConfig = JustTrackSdkConfig("user-config", null, "tracking-provider", null, false),
                deviceInfo = TestDeviceInfoImpl(),
                integritySecretFuture = ValueFuture("secret"),
            ),
            attributionApi = api,
            logger = logger,
            versionBundle = VersionBundle(TestSdkVersion(), TestApplicationVersion()),
            installerSourceIdProvider = InstallerSourceIdProvider { "installer" },
        )
    }

    private fun attributionJson(retargetingUrl: String? = null): JSONObject {
        return JSONObject().apply {
            put("user", userJson())
            put("attribution", attributionOutputJson())
            if (retargetingUrl == null) {
                put("retargeting", JSONObject.NULL)
            } else {
                put(
                    "retargeting",
                    JSONObject().apply {
                        put("url", retargetingUrl)
                        put("attributes", JSONObject().put("key", "value"))
                    },
                )
            }
            put("sdkConfig", JSONObject.NULL)
        }
    }

    private fun userJson(): JSONObject = JSONObject().apply {
        put("installId", installId)
        put("type", "new")
        put("testGroup", JSONObject.NULL)
        put("redownload", false)
    }

    private fun attributionOutputJson(): JSONObject = JSONObject().apply {
        put("campaign", JSONObject().put("externalId", "1").put("name", "campaign").put("type", "cpi").put("organic", false))
        put("channel", JSONObject().put("id", 2).put("name", "channel").put("incent", false))
        put("network", JSONObject().put("id", 3).put("name", "network"))
        put("sourceId", "source-id")
        put("sourceBundleId", "source-bundle")
        put("sourcePlacement", "source-placement")
        put("adsetId", "adset")
        put("attributedAt", "2020-06-15T13:46:59.000Z")
    }

    private fun claims(vararg values: String, timedOut: Boolean = false): ProvidedClaims {
        val claims = ProvidedClaims()
        values.forEach { claims.addClaim(it) }
        if (timedOut) {
            claims.setTimedOut()
        }
        return claims
    }

    private suspend fun expectIllegalState(block: suspend () -> Unit): IllegalStateException {
        try {
            block()
            fail("expected IllegalStateException")
        } catch (exception: IllegalStateException) {
            return exception
        }
        throw AssertionError("unreachable")
    }

    private class RecordingAttributionApi(
        private val result: Result<JSONObject?>,
    ) : AttributionApi {
        var body: JSONEncodable? = null
        var advertiserId: String? = null

        override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            this.body = body
            this.advertiserId = advertiserId
            return result
        }

        override suspend fun getSignedIpClaim(protocol: IPProtocol, advertiserId: String?): Result<JSONObject> = Result.success(JSONObject())

        override suspend fun sendCustomUserId(
            body: io.justtrack.dtos.DTOPublishCustomUserIdRequest,
            advertiserId: String?,
            uuid: String,
            installId: String,
        ): Result<Unit> {
            return Result.success(Unit)
        }

        override suspend fun sendFirebaseAppInstanceId(body: JSONEncodable, advertiserId: String?, uuid: String, installId: String): Result<Unit> {
            return Result.success(Unit)
        }
    }

    private class RecordingClaimProvider(private val claims: ProvidedClaims) : ClaimProvider {
        var refreshCalled = false
        var timeout: Long? = null

        override fun refreshClaims() {
            refreshCalled = true
        }

        override fun provideClaims(timeout: Long): ProvidedClaims {
            this.timeout = timeout
            return claims
        }
    }

    private class TestAdvertiserIdInfo(
        override val advertiserId: String?,
        override val isLimitedAdTracking: Boolean,
    ) : AdvertiserIdInfo

    private class TestSdkVersion : SdkVersion {
        override val platformType: PlatformType = PlatformType.ANDROID
        override val major: Int = 1
        override val minor: Int = 2
        override val patch: Int = 3
        override val name: String = "1.2.3-test"
    }

    private class TestApplicationVersion : ApplicationVersion {
        override fun getVersionName(): String = "1.0.0"
        override fun getVersionCode(): String = "100"
    }
}
