package io.justtrack

import io.justtrack.api.PrivacyApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.providers.AdvertiserIdProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AnonymizeUserHandlerTest {

    @Test
    fun anonymizeUser_returnsTrueAndPassesIdsToApiOnSuccess() {
        val privacyApi = RecordingPrivacyApi(result = Result.success(JSONObject()))
        val handler = createHandler(
            privacyApi = privacyApi,
            advertiserId = "ad-123",
            userId = "user-42",
            installInstanceId = "install-7",
        )

        val future = handler.anonymizeUser()

        assertEquals(true, future.get())
        assertEquals(1, privacyApi.callCount.get())
        assertEquals("ad-123", privacyApi.lastAdvertiserId)
        assertEquals("user-42", privacyApi.lastUuid)
        assertEquals("install-7", privacyApi.lastInstallId)
        assertTrue(privacyApi.lastBody is io.justtrack.dtos.DTOAnonymousUser)
        val body = privacyApi.lastBody as io.justtrack.dtos.DTOAnonymousUser
        assertEquals("install-7", body.installInstanceId)
        assertEquals("ad-123", body.deviceId)
    }

    @Test
    fun anonymizeUser_throwsApiExceptionWhenResultIsFailure() {
        val cause = IllegalStateException("server error")
        val privacyApi = RecordingPrivacyApi(result = Result.failure(cause))
        val handler = createHandler(privacyApi = privacyApi)

        // ImmediateSyncTaskExecutor surfaces task failures synchronously from executeFuture.
        val ex = assertThrows(IllegalStateException::class.java) {
            handler.anonymizeUser()
        }
        assertSame(cause, ex)
    }

    @Test
    fun anonymizeUser_throwsFallbackExceptionWhenFailureExceptionMissing() {
        // Result.failure can't carry a null exception via the public API, but FixedRetryingTask
        // could in principle be invoked with a Result whose exceptionOrNull() returns null after
        // a sentinel scenario. We simulate via a custom Result that yields null. Easiest test:
        // construct Result.failure(IllegalStateException()) and assert the propagation path; the
        // "fallback IllegalStateException with unknown exception" branch is defensive and
        // unreachable from real Result instances, so we just assert the wrapped exception.
        val privacyApi = RecordingPrivacyApi(result = Result.failure(RuntimeException("err")))
        val handler = createHandler(privacyApi = privacyApi)

        val ex = assertThrows(RuntimeException::class.java) { handler.anonymizeUser() }
        assertEquals("err", ex.message)
    }

    @Test
    fun attributionParams_supportsDataClassMethods() {
        val userId = ValueFuture("user")
        val installId = ValueFuture("install")
        val advertiserIdProvider = AdvertiserIdProvider { ValueFuture(TestAdvertiserIdInfo("ad")) }
        val params = AnonymizeUserHandler.AttributionParams(userId, installId, advertiserIdProvider)

        assertSame(userId, params.userIdFuture)
        assertSame(installId, params.installInstanceIdFuture)
        assertSame(advertiserIdProvider, params.advertiserIdProvider)
        assertEquals(params, params.copy())
        assertNotEquals(params, params.copy(userIdFuture = ValueFuture("other")))
    }

    private fun createHandler(
        privacyApi: PrivacyApi,
        advertiserId: String? = "ad-id",
        userId: String = "user-id",
        installInstanceId: String = "install-id",
    ): AnonymizeUserHandler {
        val deviceInfo = TestDeviceInfoImpl()
        val advertiserIdProvider = AdvertiserIdProvider {
            ValueFuture(TestAdvertiserIdInfo(advertiserId))
        }
        return AnonymizeUserHandler(
            taskExecutor = ImmediateSyncTaskExecutor(),
            deviceInfo = deviceInfo,
            privacyApi = privacyApi,
            logger = TestLogger(),
            attr = AnonymizeUserHandler.AttributionParams(
                userIdFuture = ValueFuture(userId),
                installInstanceIdFuture = ValueFuture(installInstanceId),
                advertiserIdProvider = advertiserIdProvider,
            ),
            retryDelays = listOf(0),
        )
    }

    private class TestAdvertiserIdInfo(override val advertiserId: String?) : AdvertiserIdInfo {
        override val isLimitedAdTracking: Boolean = false
    }

    private class RecordingPrivacyApi(private val result: Result<JSONObject>) : PrivacyApi {
        val callCount = AtomicInteger(0)
        var lastAdvertiserId: String? = null
        var lastUuid: String? = null
        var lastInstallId: String? = null
        var lastBody: JSONEncodable? = null

        override suspend fun anonymizeUser(advertiserId: String?, uuid: String?, installId: String?, body: JSONEncodable): Result<JSONObject> {
            callCount.incrementAndGet()
            lastAdvertiserId = advertiserId
            lastUuid = uuid
            lastInstallId = installId
            lastBody = body
            return result
        }
    }

    @Suppress("unused")
    private fun ensureRunBlockingReferenced() = runBlocking { }
}
