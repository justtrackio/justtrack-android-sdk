package io.justtrack

import io.justtrack.api.ExperimentApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.exceptions.SdkNotTrackingException
import io.justtrack.providers.AdvertiserIdProvider
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean

class ExperimentVariantHandlerTest {

    private lateinit var isTracking: AtomicBoolean
    private lateinit var handler: ExperimentVariantHandler

    @Before
    fun setup() {
        isTracking = AtomicBoolean(true)
        handler = createHandler()
    }

    @Test
    fun returnsError_whenNotTracking() {
        isTracking.set(false)
        val future = handler.setExperimentVariant("exp", "var", listOf(), null)
        assertThrowsCause<SdkNotTrackingException>(future)
    }

    @Test
    fun returnsError_whenExperimentTooLong() {
        val longExperiment = "a".repeat(257)
        val future = handler.setExperimentVariant(longExperiment, "variant", listOf(), null)
        assertThrowsCause<InvalidFieldException>(future)
    }

    @Test
    fun returnsError_whenVariantTooLong() {
        val longVariant = "a".repeat(257)
        val future = handler.setExperimentVariant("experiment", longVariant, listOf(), null)
        assertThrowsCause<InvalidFieldException>(future)
    }

    @Test
    fun returnsError_whenTooManyTags() {
        val tags = listOf("a", "b", "c", "d", "e", "f")
        val future = handler.setExperimentVariant("experiment", "variant", tags, null)
        assertThrowsCause<InvalidFieldException>(future)
    }

    @Test
    fun returnsError_whenTagIsNull() {
        val tags = listOf("valid", null)
        val future = handler.setExperimentVariant("experiment", "variant", tags, null)
        assertThrowsCause<InvalidFieldException>(future)
    }

    @Test
    fun returnsError_whenTagTooLong() {
        val tags = listOf("a".repeat(65))
        val future = handler.setExperimentVariant("experiment", "variant", tags, null)
        assertThrowsCause<InvalidFieldException>(future)
    }

    @Test
    fun succeeds_withValidInput() {
        val future = handler.setExperimentVariant("experiment", "variant", listOf("tag1"), null)
        assertTrue(future.get() == null)
    }

    @Test
    fun succeeds_withNullTags() {
        val future = handler.setExperimentVariant("experiment", "variant", listOf(), null)
        assertTrue(future.get() == null)
    }

    @Test
    fun succeeds_withMaxTags() {
        val tags = listOf("a", "b", "c", "d", "e")
        val future = handler.setExperimentVariant("experiment", "variant", tags, null)
        assertTrue(future.get() == null)
    }

    private inline fun <reified T : Exception> assertThrowsCause(future: AsyncFuture<*>) {
        try {
            future.get()
            throw AssertionError("Expected ExecutionException with cause ${T::class.simpleName}")
        } catch (e: ExecutionException) {
            assertTrue(
                "Expected ${T::class.simpleName} but got ${e.cause?.javaClass?.simpleName}",
                e.cause is T,
            )
        }
    }

    private fun createHandler(): ExperimentVariantHandler {
        val mockVersion: Version = mock {
            on { name }.thenReturn("test-sdk-1.0.0")
            on { major }.thenReturn(1)
            on { minor }.thenReturn(0)
            on { patch }.thenReturn(0)
        }
        val mockAppVersion: ApplicationVersion = mock {
            on { getVersionName() }.thenReturn("1.0.0")
            on { getVersionCode() }.thenReturn("1")
        }
        val mockAppVersionProvider: AppVersionProvider = mock {
            on { currentVersion }.thenReturn(mockAppVersion)
        }

        return ExperimentVariantHandler(
            deps = ExperimentVariantHandler.Dependencies(
                taskExecutor = ImmediateSyncTaskExecutor(),
                deviceInfo = TestDeviceInfoImpl(),
                experimentApi = SuccessExperimentApi(),
                logger = TestLogger(),
                isTracking = isTracking,
                appVersionProvider = mockAppVersionProvider,
                sdkVersion = mockVersion,
            ),
            attributionParams = ExperimentVariantHandler.AttributionParams(
                installInstanceIdFuture = { ValueFuture("install-id") },
                userIdFuture = { ValueFuture("user-id") },
                advertiserIdProvider = AdvertiserIdProvider {
                    ValueFuture(TestAdvertiserIdInfo())
                },
            ),
        )
    }

    private class SuccessExperimentApi : ExperimentApi {
        override suspend fun setExperimentVariant(
            body: JSONEncodable,
            advertiserId: String?,
            uuid: String?,
            installId: String?,
        ): Result<JSONObject> = Result.success(JSONObject())
    }

    private class TestAdvertiserIdInfo : AdvertiserIdInfo {
        override val advertiserId: String? = "ad-id"
        override val isLimitedAdTracking: Boolean = false
    }
}
