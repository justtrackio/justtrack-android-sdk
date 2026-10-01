package io.justtrack

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.Store.clearForTesting
import io.justtrack.api.DefaultAttributionApi
import io.justtrack.publicInterface.SdkTest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.ArrayDeque
import java.util.Queue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ClaimProviderTest {

    @Test
    @Throws(java.lang.Exception::class)
    fun provideClaimsSuccess() {
        runTest(listOf(TokenAnswer.ANSWER_1, TokenAnswer.ANSWER_2))
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun provideClaimsFailIPv4() {
        runTest(listOf(TokenAnswer.FAILURE, TokenAnswer.ANSWER_2))
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun provideClaimsFailIPv6() {
        runTest(listOf(TokenAnswer.ANSWER_1, TokenAnswer.FAILURE))
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun provideClaimsFailBoth() {
        runTest(listOf(TokenAnswer.FAILURE, TokenAnswer.FAILURE))
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun provideClaimsTimeoutV4() {
        runTest(listOf(TokenAnswer.TIMEOUT, TokenAnswer.ANSWER_2))
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun provideClaimsTimeoutV6() {
        runTest(listOf(TokenAnswer.ANSWER_1, TokenAnswer.TIMEOUT))
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun provideClaimsTimeoutBoth() {
        runTest(listOf(TokenAnswer.TIMEOUT, TokenAnswer.TIMEOUT))
    }

    @Throws(Exception::class)
    private fun runTest(signResult: List<TokenAnswer>) {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        // reset and remove data for the test
        clearForTesting(context)
        SessionManagerImpl.Session.clearForTesting(context)
        CustomUserIdStore.getInstance().clearForTesting(context)
        FirebaseIdStore.getInstance().clearForTesting(context)
        JustTrack.resetForTesting()

        val attributionApi = ClaimProvidingAttributionApi(signResult)
        val executor = ThreadPoolExecutor(
            10,
            10,
            60L,
            TimeUnit.SECONDS,
            LinkedBlockingDeque<Runnable>(),
        )
        executor.allowCoreThreadTimeOut(true)

        val builder = JustTrackSdkBuilder(
            (context.applicationContext as Application)!!,
            SdkTest.API_TOKEN,
        )
        val sdk = createForTesting(
            builder,
            RetryConfig(5, 0, 5, RetryConfig.TEST_INTEGRITY_CONFIG),
            null,
            null,
            attributionApi,
        )

        try {
            sdk.attribution.get()

            attributionApi.assertNoErrors()
        } finally {
            sdk.shutdown()
        }
    }

    private class ClaimProvidingAttributionApi(signResults: List<TokenAnswer>) : DefaultAttributionApi() {
        private val errorList: MutableList<AssertionError> = ArrayList()
        private val signResults: Queue<TokenAnswer> = ArrayDeque()
        private val expectedClaims = JSONArray()

        init {
            for (signResult in signResults) {
                this.signResults.add(signResult)
                if (signResult.hasToken()) {
                    expectedClaims.put(signResult.token)
                }
            }
        }

        override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
            try {
                val claims = body.toJSON(Formatter).getJSONArray("claims")
                try {
                    if (expectedClaims.length() < 2) {
                        Assert.assertEquals(expectedClaims, claims)
                    } else {
                        Assert.assertEquals(expectedClaims.length().toLong(), claims.length().toLong())
                        Assert.assertEquals(2, claims.length().toLong())
                        if (expectedClaims != claims) {
                            Assert.assertEquals(expectedClaims[0], claims[1])
                            Assert.assertEquals(expectedClaims[1], claims[0])
                        }
                    }
                } catch (exception: AssertionError) {
                    // Deadlock-Safety: This is a test.
                    synchronized(this) {
                        errorList.add(exception)
                    }
                }
                return Result.success(AttributionTest.testAttribution)
            } catch (exception: JSONException) {
                return Result.failure(exception)
            }
        }

        override suspend fun getSignedIpClaim(protocol: IPProtocol, advertiserId: String?): Result<JSONObject> {
            try {
                val next: TokenAnswer
                // Deadlock-Safety: This is a test.
                synchronized(this) {
                    next = signResults.remove()
                }
                when (next) {
                    TokenAnswer.ANSWER_1, TokenAnswer.ANSWER_2 -> {
                        val result = JSONObject()
                        result.put("ip", "your ip value")
                        result.put("type", "your ip type")
                        result.put("token", next.token)
                        result.put("redownload", false)
                        return Result.success(result)
                    }

                    TokenAnswer.TIMEOUT -> {}
                    TokenAnswer.FAILURE -> return Result.failure(RuntimeException("You shall not pass"))
                }
            } catch (exception: NoSuchElementException) {
                // Deadlock-Safety: This is a test.
                synchronized(this) {
                    errorList.add(AssertionError("No more answers stored", exception))
                }
            } catch (exception: JSONException) {
                // Deadlock-Safety: This is a test.
                synchronized(this) {
                    errorList.add(AssertionError("Failed to construct answer", exception))
                }
            } catch (exception: AssertionError) {
                // Deadlock-Safety: This is a test.
                synchronized(this) {
                    errorList.add(exception)
                }
            }
            return Result.failure(RuntimeException("You shall not pass"))
        }

        // Deadlock-Safety: This is a test.
        @Synchronized
        fun assertNoErrors() {
            for (error in errorList) {
                throw error
            }

            Assert.assertTrue(signResults.isEmpty())
        }
    }

    enum class TokenAnswer {
        ANSWER_1,
        ANSWER_2,
        TIMEOUT,
        FAILURE,
        ;

        fun hasToken(): Boolean {
            return when (this) {
                ANSWER_1, ANSWER_2 -> true
                else -> false
            }
        }

        val token: String
            get() {
                when (this) {
                    ANSWER_1 -> return "token 1"
                    ANSWER_2 -> return "token 2"
                    else -> {
                        Assert.fail("can not provide a token for $this")
                        return ""
                    }
                }
            }
    }
}
