package io.justtrack

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.database.Database
import io.justtrack.log.Logger
import io.justtrack.publicInterface.SdkTest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class AttributionClaimRetriesTest {
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
    fun testAttributionRetriesAfterClaimTimeout() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val requestBodyChannel = Channel<JSONEncodable>()

        val builder = JustTrackSdkBuilder((context.applicationContext as Application), SdkTest.API_TOKEN)
        builder.setAttributionRetryDelaySeconds(3)

        val sdk = JustTrackSdkImpl.createForTesting(
            builder,
            HttpClientWithClaimReport(requestBodyChannel),
            RetryConfig.DEFAULT_CONFIG,
            DelayClaimProvider(),
            null,
        )

        val firstClaims = requestBodyChannel.receive()

        // no claim on first request
        val firstClaimLength = firstClaims.toJSON(Formatter).getJSONArray("claims").length()
        Assert.assertEquals(0, firstClaimLength)

        val secondClaims = requestBodyChannel.receive()
        val secondClaimLength = secondClaims.toJSON(Formatter)
            .getJSONArray("claims").length()
        Assert.assertEquals(2, secondClaimLength)
        val secondClaimArray = secondClaims.toJSON(Formatter)
            .getJSONArray("claims")

        Assert.assertEquals("claim_get_ipv4", secondClaimArray.getString(0))
        Assert.assertEquals("claim_get_ipv6", secondClaimArray.getString(1))

        sdk.shutdown()
    }
    internal class HttpClientWithClaimReport constructor(private val requestBodyChannel: Channel<JSONEncodable>) :
        BaseTestHttpClient() {
        override suspend fun sendAttributionRequest(logger: Logger, body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            requestBodyChannel.send(body)
            val response = JSONObject(AttributionTest.testAttribution.toString())
            response.getJSONObject("attribution").getJSONObject("campaign").put("organic", true)
            return Result.success(response)
        }
    }

    private class DelayClaimProvider : ClaimProvider {
        private var remainingDelay: Long = 5000

        override fun refreshClaims(sdk: BaseJustTrackSdk) {
            // no need to refresh anything
        }

        override fun provideClaims(timeout: Long): ProvidedClaims {
            remainingDelay -= timeout
            val claims = ProvidedClaims().apply {
                if (remainingDelay > 0) {
                    setTimedOut()
                } else {
                    addClaim("claim_get_ipv4")
                    addClaim("claim_get_ipv6")
                }
            }
            return claims
        }
    }
}
