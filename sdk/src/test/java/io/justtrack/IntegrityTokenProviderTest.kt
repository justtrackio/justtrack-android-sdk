package io.justtrack

import android.content.Context
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import io.justtrack.exceptions.IntegrityException
import io.justtrack.executor.TaskExecutor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import java.util.concurrent.ExecutionException
import com.google.android.gms.tasks.Task as GmsTask

internal class IntegrityTokenProviderTest {

    private lateinit var context: Context
    private lateinit var deviceInfo: DeviceInfo
    private lateinit var logger: HttpLogger
    private lateinit var taskExecutor: ImmediateSyncTaskExecutor
    private lateinit var databaseInterface: DatabaseInterface
    private lateinit var attribution: DatabaseAttributionInterface

    @Before
    fun setUp() {
        context = mock()
        deviceInfo = mock()
        whenever(deviceInfo.getConnectionType()).thenReturn(ConnectionType.WIFI)
        logger = mock()
        taskExecutor = ImmediateSyncTaskExecutor()
        databaseInterface = mock()
        attribution = mock()
        whenever(databaseInterface.openAttribution()).thenReturn(attribution)
        runBlocking { whenever(attribution.isIntegrityTokenSent()).thenReturn(false) }
    }

    @After
    fun tearDown() {
        // Reset the static so tests don't leak into each other.
        IntegrityTokenProvider.sGoogleIntegrityProvider = null
    }

    private fun resolved(value: String): AsyncFuture<String> = AsyncFutureImpl(ValueFuture(value), taskExecutor)

    private fun newProvider(injectedTokenProvider: StandardIntegrityTokenProvider? = null): IntegrityTokenProvider = IntegrityTokenProvider(
        taskExecutor,
        context,
        deviceInfo,
        injectedTokenProvider,
        RetryConfig.TEST_INTEGRITY_CONFIG,
    )

    // -------- getOrRenewFuture --------

    @Test
    fun `getOrRenewFuture creates a new future on first call and caches it on the second call`() = runBlocking<Unit> {
        val provider = newProvider()
        val getterTask = mock<IntegrityTokenProvider.IntegrityTokenGetterTask>()
        whenever(getterTask.execute()).thenReturn(IntegrityTokenData(token = "tok"))

        val first = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)
        val second = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)

        assertSame("Token still valid, expected cached future", first, second)
        assertEquals("tok", first.await().token)
        // getterTask is wrapped in FixedRetryingTask + IntegrityRetryGetterTask; one execution path => 1 call.
        verify(getterTask, times(1)).execute()
    }

    @Test
    fun `getOrRenewFuture renews the future when the cached token is outdated`() = runBlocking<Unit> {
        val provider = newProvider()
        // Token generated longer than VALID_TOKEN_DURATION ago -> isTokenOutDated == true.
        val staleTimestamp = System.currentTimeMillis() - (IntegrityTokenProvider.VALID_TOKEN_DURATION.toLong() + 1_000L)
        val staleData = IntegrityTokenData(token = "stale", generatedTimestamp = staleTimestamp)
        val freshData = IntegrityTokenData(token = "fresh")
        val getterTask = mock<IntegrityTokenProvider.IntegrityTokenGetterTask>()
        whenever(getterTask.execute()).thenReturn(staleData, freshData)

        val first = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)
        val second = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)

        assertNotSame(first, second)
        assertEquals("stale", first.await().token)
        assertEquals("fresh", second.await().token)
        verify(getterTask, times(2)).execute()
    }

    @Test
    fun `getOrRenewFuture caches future even when getterTask throws because retry wrapper packs error into data`() = runBlocking<Unit> {
        // IntegrityRetryGetterTask catches any Exception from the inner task and packs it
        // into IntegrityTokenData with UNKNOWN_ERROR_CODE. So the resulting future is "done"
        // with a fresh timestamp and isTokenOutDated returns false on the next call.
        val provider = newProvider()
        val getterTask = mock<IntegrityTokenProvider.IntegrityTokenGetterTask>()
        whenever(getterTask.execute()).thenAnswer { throw IllegalStateException("boom") }

        val first = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)
        val second = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)

        assertSame(first, second)
        val data = first.await()
        assertNotNull(data.integrityException)
        assertEquals(IntegrityTokenProvider.UNKNOWN_ERROR_CODE, data.integrityException?.errorCode)
    }

    @Test
    fun `getOrRenewFuture creates a default getterTask when none is provided`() = runBlocking<Unit> {
        // We can't intercept the internally-constructed IntegrityTokenGetterTask, but we
        // can still drive the path: stub the StandardIntegrityTokenProvider so the
        // default getterTask resolves successfully.
        val tokenProvider = mock<StandardIntegrityTokenProvider>()
        whenever(tokenProvider.request(any())).thenReturn(stubStandardTokenTask("ok"))
        val provider = newProvider(injectedTokenProvider = tokenProvider)

        val result = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface).await()

        assertEquals("ok", result.token)
    }

    // -------- IntegrityRetryGetterTask --------

    @Test
    fun `IntegrityRetryGetterTask returns retrying task result on success`() = runBlocking<Unit> {
        val expected = IntegrityTokenData(token = "good")
        val inner = Task { expected }
        val result = IntegrityTokenProvider.IntegrityRetryGetterTask(inner, logger).execute()

        assertSame(expected, result)
    }

    @Test
    fun `IntegrityRetryGetterTask packs IntegrityException into IntegrityTokenData`() = runBlocking<Unit> {
        val cause = IntegrityException(errorCode = -3, isRetryAbleErrorCode = true, "fail", null)
        val inner = Task<IntegrityTokenData> { throw cause }

        val result = IntegrityTokenProvider.IntegrityRetryGetterTask(inner, logger).execute()

        assertNull(result.token)
        assertSame(cause, result.integrityException)
    }

    @Test
    fun `IntegrityRetryGetterTask wraps unknown exception into IntegrityTokenData and logs warning`() = runBlocking<Unit> {
        val cause = IllegalStateException("weird")
        val inner = Task<IntegrityTokenData> { throw cause }

        val result = IntegrityTokenProvider.IntegrityRetryGetterTask(inner, logger).execute()

        assertNull(result.token)
        val ex = result.integrityException
        assertNotNull(ex)
        assertEquals(IntegrityTokenProvider.UNKNOWN_ERROR_CODE, ex!!.errorCode)
        assertEquals(true, ex.isRetryAbleErrorCode)
        assertEquals("weird", ex.errorMessage)
        verify(logger).warn(eq("Integrity provider throwing unknown exception"), eq(cause))
    }

    // -------- IntegrityTokenGetterTask --------

    private fun newGetterTask(
        provider: IntegrityTokenProvider,
        installInstanceId: String = "iid",
        integritySecret: String = "secret",
    ): IntegrityTokenProvider.IntegrityTokenGetterTask = IntegrityTokenProvider.IntegrityTokenGetterTask(
        context,
        logger,
        resolved(integritySecret),
        resolved(installInstanceId),
        databaseInterface,
        provider,
    )

    /** Concrete subclass of StandardIntegrityToken with a configurable token() return value. */
    private class FakeStandardIntegrityToken(private val tokenValue: String?) : StandardIntegrityToken() {
        override fun token(): String? = tokenValue
        override fun showDialog(activity: android.app.Activity?, dialogTypeCode: Int): GmsTask<Int> =
            throw UnsupportedOperationException("not used in tests")
    }

    /** Minimal stub of a Play GmsTask that immediately invokes the success listener. */
    private fun stubStandardTokenTask(token: String?): GmsTask<StandardIntegrityToken> = immediateSuccessTask(FakeStandardIntegrityToken(token))

    /** A trivial fake GmsTask that dispatches synchronously to the registered listener. */
    private class FakeGmsTask<T>(
        private val value: T? = null,
        private val error: Exception? = null,
    ) : GmsTask<T>() {
        override fun isComplete(): Boolean = true
        override fun isSuccessful(): Boolean = error == null
        override fun isCanceled(): Boolean = false

        @Suppress("UNCHECKED_CAST")
        override fun getResult(): T = value as T
        override fun <X : Throwable> getResult(exceptionType: Class<X>): T = result
        override fun getException(): Exception? = error

        override fun addOnSuccessListener(listener: OnSuccessListener<in T>): GmsTask<T> {
            if (error == null) listener.onSuccess(value)
            return this
        }
        override fun addOnSuccessListener(executor: java.util.concurrent.Executor, listener: OnSuccessListener<in T>): GmsTask<T> =
            addOnSuccessListener(listener)
        override fun addOnSuccessListener(activity: android.app.Activity, listener: OnSuccessListener<in T>): GmsTask<T> =
            addOnSuccessListener(listener)

        override fun addOnFailureListener(listener: OnFailureListener): GmsTask<T> {
            if (error != null) listener.onFailure(error)
            return this
        }
        override fun addOnFailureListener(executor: java.util.concurrent.Executor, listener: OnFailureListener): GmsTask<T> =
            addOnFailureListener(listener)
        override fun addOnFailureListener(activity: android.app.Activity, listener: OnFailureListener): GmsTask<T> = addOnFailureListener(listener)
    }

    private fun <T> immediateSuccessTask(value: T): GmsTask<T> = FakeGmsTask(value = value)

    private fun <T> immediateFailureTask(error: Exception): GmsTask<T> = FakeGmsTask(error = error)

    @Test
    fun `getter returns previouslySent data when token was already sent`() = runBlocking<Unit> {
        whenever(attribution.isIntegrityTokenSent()).thenReturn(true)
        val provider = newProvider(injectedTokenProvider = mock())

        val result = newGetterTask(provider).execute()

        assertTrue(result.previouslySent)
        assertNull(result.token)
        assertNull(result.integrityException)
    }

    @Test
    fun `getter returns successful token when standard provider resolves`() = runBlocking<Unit> {
        val tokenProvider = mock<StandardIntegrityTokenProvider>()
        whenever(tokenProvider.request(any())).thenReturn(stubStandardTokenTask("the-token"))
        val provider = newProvider(injectedTokenProvider = tokenProvider)

        val result = newGetterTask(provider).execute()

        assertFalse(result.previouslySent)
        assertEquals("the-token", result.token)
        assertNull(result.integrityException)
    }

    @Test
    fun `getter returns UNKNOWN_ERROR_CODE when standard provider resolves with empty token`() = runBlocking<Unit> {
        val tokenProvider = mock<StandardIntegrityTokenProvider>()
        whenever(tokenProvider.request(any())).thenReturn(stubStandardTokenTask(""))
        val provider = newProvider(injectedTokenProvider = tokenProvider)

        val result = newGetterTask(provider).execute()

        assertEquals("", result.token)
        assertEquals(IntegrityTokenProvider.UNKNOWN_ERROR_CODE, result.integrityException?.errorCode)
        assertEquals("Received token with empty token value", result.integrityException?.errorMessage)
    }

    @Test
    fun `getter returns UNKNOWN_ERROR_CODE when standard provider resolves with null token`() = runBlocking<Unit> {
        val tokenProvider = mock<StandardIntegrityTokenProvider>()
        whenever(tokenProvider.request(any())).thenReturn(stubStandardTokenTask(null))
        val provider = newProvider(injectedTokenProvider = tokenProvider)

        val result = newGetterTask(provider).execute()

        assertNull(result.token)
        assertEquals(IntegrityTokenProvider.UNKNOWN_ERROR_CODE, result.integrityException?.errorCode)
        assertEquals("Received token with null token value", result.integrityException?.errorMessage)
    }

    @Test
    fun `getter rethrows IntegrityException when standard provider fails with retryable code`() = runBlocking<Unit> {
        val standardEx = mock<StandardIntegrityException>()
        whenever(standardEx.errorCode).thenReturn(-3) // -3 is in retryAbleErrorCode
        val tokenProvider = mock<StandardIntegrityTokenProvider>()
        whenever(tokenProvider.request(any())).thenReturn(immediateFailureTask(standardEx))
        val provider = newProvider(injectedTokenProvider = tokenProvider)

        try {
            newGetterTask(provider).execute()
            fail("Expected IntegrityException to be rethrown")
        } catch (e: IntegrityException) {
            assertEquals(-3, e.errorCode)
            assertTrue(e.isRetryAbleErrorCode)
        }
    }

    @Test
    fun `getter returns IntegrityException data when standard provider fails with non-retryable code`() = runBlocking<Unit> {
        val standardEx = mock<StandardIntegrityException>()
        whenever(standardEx.errorCode).thenReturn(-1) // not in retryAbleErrorCode
        val tokenProvider = mock<StandardIntegrityTokenProvider>()
        whenever(tokenProvider.request(any())).thenReturn(immediateFailureTask(standardEx))
        val provider = newProvider(injectedTokenProvider = tokenProvider)

        val result = newGetterTask(provider).execute()

        assertEquals(-1, result.integrityException?.errorCode)
        assertFalse(result.integrityException?.isRetryAbleErrorCode ?: true)
    }

    @Test
    fun `getter wraps unknown exception from standard provider into retryable IntegrityException`() = runBlocking<Unit> {
        val tokenProvider = mock<StandardIntegrityTokenProvider>()
        whenever(tokenProvider.request(any())).thenReturn(immediateFailureTask(RuntimeException("nope")))
        val provider = newProvider(injectedTokenProvider = tokenProvider)

        try {
            newGetterTask(provider).execute()
            fail("Expected IntegrityException to be rethrown")
        } catch (e: IntegrityException) {
            assertEquals(IntegrityTokenProvider.UNKNOWN_ERROR_CODE, e.errorCode)
            assertTrue(e.isRetryAbleErrorCode)
            assertEquals("nope", e.errorMessage)
        }
    }

    @Test
    fun `getter wraps exception thrown during await into UNKNOWN_ERROR_CODE`() = runBlocking<Unit> {
        // Use a Future whose get() throws ExecutionException so installInstanceIdFuture.await()
        // propagates an exception that the catch-all in IntegrityTokenGetterTask wraps into
        // IntegrityException with UNKNOWN_ERROR_CODE.
        val failingFuture: AsyncFuture<String> = AsyncFutureImpl(
            FailingFuture(IllegalStateException("await failed")),
            taskExecutor,
        )
        val provider = newProvider(injectedTokenProvider = mock())

        val getterTask = IntegrityTokenProvider.IntegrityTokenGetterTask(
            context,
            logger,
            resolved("secret"),
            failingFuture,
            databaseInterface,
            provider,
        )

        try {
            getterTask.execute()
            fail("Expected IntegrityException to be rethrown")
        } catch (e: IntegrityException) {
            assertEquals(IntegrityTokenProvider.UNKNOWN_ERROR_CODE, e.errorCode)
            assertTrue(e.isRetryAbleErrorCode)
        }
    }

    /** A Future that always fails its get() with the supplied cause. */
    private class FailingFuture<T>(private val cause: Throwable) : java.util.concurrent.Future<T> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = true
        override fun get(): T = throw ExecutionException(cause)
        override fun get(timeout: Long, unit: java.util.concurrent.TimeUnit): T = throw ExecutionException(cause)
    }

    @Test
    fun `getter uses sGoogleIntegrityProvider when no provider is injected and caches it on the IntegrityTokenProvider`() = runBlocking<Unit> {
        val staticProvider = mock<StandardIntegrityTokenProvider>()
        whenever(staticProvider.request(any())).thenReturn(stubStandardTokenTask("from-static"))
        IntegrityTokenProvider.sGoogleIntegrityProvider = staticProvider

        val provider = newProvider() // no injected provider

        val result = newGetterTask(provider).execute()

        assertEquals("from-static", result.token)
        // tokenProvider should NOT have been overwritten because we used the static branch.
        assertNull(provider.tokenProvider)
    }

    @Test
    fun `getter calls IntegrityManagerFactory createStandard when no provider is available`() = runBlocking<Unit> {
        // Cover the `getTokenProvider(context)` branch in `getStandardIntegrityTokenProvider`.
        val realProvider = mock<StandardIntegrityTokenProvider>()
        whenever(realProvider.request(any())).thenReturn(stubStandardTokenTask("from-factory"))

        val standardManager = mock<StandardIntegrityManager>()
        whenever(standardManager.prepareIntegrityToken(any())).thenReturn(immediateSuccessTask(realProvider))

        val mockedStatic: MockedStatic<IntegrityManagerFactory> = Mockito.mockStatic(IntegrityManagerFactory::class.java)
        try {
            mockedStatic.`when`<StandardIntegrityManager> { IntegrityManagerFactory.createStandard(any()) }
                .thenReturn(standardManager)

            val provider = newProvider() // no injected provider, sGoogleIntegrityProvider is null (cleared in tearDown)
            val result = newGetterTask(provider).execute()

            assertEquals("from-factory", result.token)
            // The factory branch should cache the provider back on IntegrityTokenProvider.tokenProvider.
            assertSame(realProvider, provider.tokenProvider)
        } finally {
            mockedStatic.close()
        }
    }

    @Test
    fun `getter wraps StandardIntegrityException from prepareIntegrityToken into IntegrityException`() = runBlocking<Unit> {
        val standardEx = mock<StandardIntegrityException>()
        whenever(standardEx.errorCode).thenReturn(-100) // in retryAbleErrorCode

        val standardManager = mock<StandardIntegrityManager>()
        whenever(standardManager.prepareIntegrityToken(any())).thenReturn(immediateFailureTask(standardEx))

        val mockedStatic: MockedStatic<IntegrityManagerFactory> = Mockito.mockStatic(IntegrityManagerFactory::class.java)
        try {
            mockedStatic.`when`<StandardIntegrityManager> { IntegrityManagerFactory.createStandard(any()) }
                .thenReturn(standardManager)

            val provider = newProvider()

            try {
                newGetterTask(provider).execute()
                fail("Expected IntegrityException to be rethrown")
            } catch (e: IntegrityException) {
                assertEquals(-100, e.errorCode)
                assertTrue(e.isRetryAbleErrorCode)
            }
        } finally {
            mockedStatic.close()
        }
    }

    @Test
    fun `getter wraps unknown exception from prepareIntegrityToken into UNKNOWN_ERROR_CODE`() = runBlocking<Unit> {
        val standardManager = mock<StandardIntegrityManager>()
        whenever(standardManager.prepareIntegrityToken(any())).thenReturn(immediateFailureTask(RuntimeException("oops")))

        val mockedStatic: MockedStatic<IntegrityManagerFactory> = Mockito.mockStatic(IntegrityManagerFactory::class.java)
        try {
            mockedStatic.`when`<StandardIntegrityManager> { IntegrityManagerFactory.createStandard(any()) }
                .thenReturn(standardManager)

            val provider = newProvider()

            try {
                newGetterTask(provider).execute()
                fail("Expected IntegrityException to be rethrown")
            } catch (e: IntegrityException) {
                assertEquals(IntegrityTokenProvider.UNKNOWN_ERROR_CODE, e.errorCode)
                assertTrue(e.isRetryAbleErrorCode)
            }
        } finally {
            mockedStatic.close()
        }
    }

    @Test
    fun `request hash combines installInstanceId and securitySecret`() = runBlocking<Unit> {
        // Verify the requestHash format ("$installInstanceId:$securitySecret") is built by
        // capturing the StandardIntegrityTokenRequest passed to tokenProvider.request().
        val tokenProvider = mock<StandardIntegrityTokenProvider>()
        whenever(tokenProvider.request(any())).thenReturn(stubStandardTokenTask("t"))
        val provider = newProvider(injectedTokenProvider = tokenProvider)

        val iid = UUID.randomUUID().toString()
        val secret = UUID.randomUUID().toString()
        newGetterTask(provider, installInstanceId = iid, integritySecret = secret).execute()

        verify(tokenProvider).request(argThat { req -> req.requestHash() == "$iid:$secret" })
    }

    @Test
    fun `getter wraps exception thrown by database openAttribution into UNKNOWN_ERROR_CODE`() = runBlocking<Unit> {
        // Cover the AutoCloseable.use synthetic exception-path bytecode (line 139).
        runBlocking {
            whenever(attribution.isIntegrityTokenSent()).thenThrow(IllegalStateException("db fail"))
        }
        val provider = newProvider(injectedTokenProvider = mock())

        try {
            newGetterTask(provider).execute()
            fail("Expected IntegrityException to be rethrown")
        } catch (e: IntegrityException) {
            assertEquals(IntegrityTokenProvider.UNKNOWN_ERROR_CODE, e.errorCode)
            assertTrue(e.isRetryAbleErrorCode)
        }
    }

    @Test
    fun `getToken second success listener invocation is ignored due to continued guard`() = runBlocking<Unit> {
        // Cover the `if (!continued.getAndSet(true))` else-branches in getToken's success listener.
        val stdToken: StandardIntegrityToken = FakeStandardIntegrityToken("payload")
        val task = MultiCallSuccessTask(stdToken)
        val tokenProvider = mock<StandardIntegrityTokenProvider>()
        whenever(tokenProvider.request(any())).thenReturn(task)
        val provider = newProvider(injectedTokenProvider = tokenProvider)

        val result = newGetterTask(provider).execute()
        assertEquals("payload", result.token)
    }

    @Test
    fun `getToken second failure listener invocation is ignored due to continued guard`() = runBlocking<Unit> {
        // Cover the second-branch of the `continued` guard in the failure listener.
        val standardEx = mock<StandardIntegrityException>()
        whenever(standardEx.errorCode).thenReturn(-3)
        val task = MultiCallFailureTask<StandardIntegrityToken>(standardEx)
        val tokenProvider = mock<StandardIntegrityTokenProvider>()
        whenever(tokenProvider.request(any())).thenReturn(task)
        val provider = newProvider(injectedTokenProvider = tokenProvider)

        try {
            newGetterTask(provider).execute()
            fail("Expected IntegrityException")
        } catch (e: IntegrityException) {
            assertEquals(-3, e.errorCode)
        }
    }

    @Test
    fun `prepareIntegrityToken second success listener invocation is ignored due to continued guard`() = runBlocking<Unit> {
        val realProvider = mock<StandardIntegrityTokenProvider>()
        whenever(realProvider.request(any())).thenReturn(stubStandardTokenTask("from-factory"))

        val standardManager = mock<StandardIntegrityManager>()
        whenever(standardManager.prepareIntegrityToken(any()))
            .thenReturn(MultiCallSuccessTask(realProvider))

        val mockedStatic: MockedStatic<IntegrityManagerFactory> = Mockito.mockStatic(IntegrityManagerFactory::class.java)
        try {
            mockedStatic.`when`<StandardIntegrityManager> { IntegrityManagerFactory.createStandard(any()) }
                .thenReturn(standardManager)

            val provider = newProvider()
            val result = newGetterTask(provider).execute()
            assertEquals("from-factory", result.token)
        } finally {
            mockedStatic.close()
        }
    }

    @Test
    fun `prepareIntegrityToken second failure listener invocation is ignored due to continued guard`() = runBlocking<Unit> {
        val standardEx = mock<StandardIntegrityException>()
        whenever(standardEx.errorCode).thenReturn(-100)
        val standardManager = mock<StandardIntegrityManager>()
        whenever(standardManager.prepareIntegrityToken(any()))
            .thenReturn(MultiCallFailureTask<StandardIntegrityTokenProvider>(standardEx))

        val mockedStatic: MockedStatic<IntegrityManagerFactory> = Mockito.mockStatic(IntegrityManagerFactory::class.java)
        try {
            mockedStatic.`when`<StandardIntegrityManager> { IntegrityManagerFactory.createStandard(any()) }
                .thenReturn(standardManager)

            val provider = newProvider()
            try {
                newGetterTask(provider).execute()
                fail("Expected IntegrityException")
            } catch (e: IntegrityException) {
                assertEquals(-100, e.errorCode)
            }
        } finally {
            mockedStatic.close()
        }
    }

    /** A GmsTask that invokes the success listener twice synchronously so the second
     *  call exercises the `continued` AtomicBoolean's else-branch. */
    private class MultiCallSuccessTask<T>(private val value: T) : GmsTask<T>() {
        override fun isComplete(): Boolean = true
        override fun isSuccessful(): Boolean = true
        override fun isCanceled(): Boolean = false
        override fun getResult(): T = value
        override fun <X : Throwable> getResult(exceptionType: Class<X>): T = value
        override fun getException(): Exception? = null
        override fun addOnSuccessListener(listener: OnSuccessListener<in T>): GmsTask<T> {
            listener.onSuccess(value)
            listener.onSuccess(value) // second call must be ignored by `continued` guard
            return this
        }
        override fun addOnSuccessListener(executor: java.util.concurrent.Executor, listener: OnSuccessListener<in T>): GmsTask<T> =
            addOnSuccessListener(listener)
        override fun addOnSuccessListener(activity: android.app.Activity, listener: OnSuccessListener<in T>): GmsTask<T> =
            addOnSuccessListener(listener)
        override fun addOnFailureListener(listener: OnFailureListener): GmsTask<T> = this
        override fun addOnFailureListener(executor: java.util.concurrent.Executor, listener: OnFailureListener): GmsTask<T> = this
        override fun addOnFailureListener(activity: android.app.Activity, listener: OnFailureListener): GmsTask<T> = this
    }

    /** A GmsTask that invokes the failure listener twice synchronously. */
    private class MultiCallFailureTask<T>(private val error: Exception) : GmsTask<T>() {
        override fun isComplete(): Boolean = true
        override fun isSuccessful(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun getResult(): T = error("not used")
        override fun <X : Throwable> getResult(exceptionType: Class<X>): T = error("not used")
        override fun getException(): Exception = error
        override fun addOnSuccessListener(listener: OnSuccessListener<in T>): GmsTask<T> = this
        override fun addOnSuccessListener(executor: java.util.concurrent.Executor, listener: OnSuccessListener<in T>): GmsTask<T> = this
        override fun addOnSuccessListener(activity: android.app.Activity, listener: OnSuccessListener<in T>): GmsTask<T> = this
        override fun addOnFailureListener(listener: OnFailureListener): GmsTask<T> {
            listener.onFailure(error)
            listener.onFailure(error) // second call must be ignored
            return this
        }
        override fun addOnFailureListener(executor: java.util.concurrent.Executor, listener: OnFailureListener): GmsTask<T> =
            addOnFailureListener(listener)
        override fun addOnFailureListener(activity: android.app.Activity, listener: OnFailureListener): GmsTask<T> = addOnFailureListener(listener)
    }

    // -------- Additional coverage for `getOrRenewFuture` short-circuit and `isTokenOutDated` --------

    @Test
    fun `constructor with default retryConfig is usable`() {
        // Covers the default-value synthetic for `retryConfig`.
        val provider = IntegrityTokenProvider(taskExecutor, context, deviceInfo)
        assertNotNull(provider)
        assertNull(provider.tokenProvider)
    }

    @Test
    fun `getOrRenewFuture returns cached future when previous one is not done`() {
        // Force the executor to return a future that is NOT done so the
        // `currentFuture.isDone` short-circuit on line 57 is exercised.
        val pendingFuture: AsyncFuture<IntegrityTokenData> = AsyncFutureImpl(NeverDoneFuture(), taskExecutor)
        val executor = OneShotExecutor(pendingFuture, taskExecutor)
        val provider = IntegrityTokenProvider(executor, context, deviceInfo, null, RetryConfig.TEST_INTEGRITY_CONFIG)
        val getterTask = mock<IntegrityTokenProvider.IntegrityTokenGetterTask>()

        val first = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)
        val second = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)

        assertSame(first, second)
        // The executor should have only been hit once because isDone==false skips the renewal check.
        assertEquals(1, executor.calls)
    }

    @Test
    fun `getOrRenewFuture renews when cached future failed with ExecutionException`() = runBlocking<Unit> {
        // First call resolves to a future whose get() throws ExecutionException -> isTokenOutDated
        // catches it and returns true so the second call renews.
        val freshData = IntegrityTokenData(token = "fresh")
        val failingFuture: AsyncFuture<IntegrityTokenData> = AsyncFutureImpl(
            FailingFuture(IllegalStateException("fail")),
            taskExecutor,
        )
        val freshFuture: AsyncFuture<IntegrityTokenData> = AsyncFutureImpl(ValueFuture(freshData), taskExecutor)
        val executor = QueueExecutor(arrayListOf(failingFuture, freshFuture))
        val provider = IntegrityTokenProvider(executor, context, deviceInfo, null, RetryConfig.TEST_INTEGRITY_CONFIG)
        val getterTask = mock<IntegrityTokenProvider.IntegrityTokenGetterTask>()

        val first = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)
        val second = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)

        assertNotSame(first, second)
        assertSame(freshData, second.await())
        assertEquals(2, executor.calls)
    }

    @Test
    fun `getOrRenewFuture renews when cached future get throws TimeoutException`() = runBlocking<Unit> {
        // The isTokenOutDated() call uses get(0, MS) which a TimingOutFuture immediately
        // satisfies by throwing TimeoutException.
        val freshData = IntegrityTokenData(token = "fresh")
        val timingOut: AsyncFuture<IntegrityTokenData> = AsyncFutureImpl(TimingOutFuture(), taskExecutor)
        val freshFuture: AsyncFuture<IntegrityTokenData> = AsyncFutureImpl(ValueFuture(freshData), taskExecutor)
        val executor = QueueExecutor(arrayListOf(timingOut, freshFuture))
        val provider = IntegrityTokenProvider(executor, context, deviceInfo, null, RetryConfig.TEST_INTEGRITY_CONFIG)
        val getterTask = mock<IntegrityTokenProvider.IntegrityTokenGetterTask>()

        provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)
        val second = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)

        assertSame(freshData, second.await())
        assertEquals(2, executor.calls)
    }

    @Test
    fun `getOrRenewFuture renews when cached future get throws InterruptedException`() = runBlocking<Unit> {
        val freshData = IntegrityTokenData(token = "fresh")
        val interrupting: AsyncFuture<IntegrityTokenData> = AsyncFutureImpl(InterruptingFuture(), taskExecutor)
        val freshFuture: AsyncFuture<IntegrityTokenData> = AsyncFutureImpl(ValueFuture(freshData), taskExecutor)
        val executor = QueueExecutor(arrayListOf(interrupting, freshFuture))
        val provider = IntegrityTokenProvider(executor, context, deviceInfo, null, RetryConfig.TEST_INTEGRITY_CONFIG)
        val getterTask = mock<IntegrityTokenProvider.IntegrityTokenGetterTask>()

        provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)
        val second = provider.getOrRenewFuture(logger, resolved("iid"), resolved("sec"), databaseInterface, getterTask)

        assertSame(freshData, second.await())
        assertEquals(2, executor.calls)
    }

    /** A future that never completes. */
    private class NeverDoneFuture : java.util.concurrent.Future<IntegrityTokenData> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = false
        override fun get(): IntegrityTokenData = throw UnsupportedOperationException()
        override fun get(timeout: Long, unit: java.util.concurrent.TimeUnit): IntegrityTokenData = throw java.util.concurrent.TimeoutException()
    }

    /** A future that throws TimeoutException from get(timeout, unit). */
    private class TimingOutFuture : java.util.concurrent.Future<IntegrityTokenData> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = true
        override fun get(): IntegrityTokenData = throw java.util.concurrent.TimeoutException()
        override fun get(timeout: Long, unit: java.util.concurrent.TimeUnit): IntegrityTokenData = throw java.util.concurrent.TimeoutException()
    }

    /** A future that throws InterruptedException from get(timeout, unit). */
    private class InterruptingFuture : java.util.concurrent.Future<IntegrityTokenData> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = true
        override fun get(): IntegrityTokenData = throw InterruptedException()
        override fun get(timeout: Long, unit: java.util.concurrent.TimeUnit): IntegrityTokenData = throw InterruptedException()
    }

    /**
     * A [TaskExecutor] that returns a single pre-built future regardless of the task, and
     * delegates all other methods to an inner executor.
     */
    private class OneShotExecutor(
        private val pending: AsyncFuture<IntegrityTokenData>,
        private val delegate: TaskExecutor,
    ) : TaskExecutor {
        var calls = 0

        @Suppress("UNCHECKED_CAST")
        override fun <V> executeFuture(task: Task<V>): AsyncFuture<V> {
            calls++
            return pending as AsyncFuture<V>
        }
        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) = delegate.execute(task, rejectedHandler)
        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean) =
            delegate.execute(task, rejectedHandler, ignoreSerially)
        override fun <V> wrap(callback: Callback<V>): Callback<V> = delegate.wrap(callback)
    }

    /** A [TaskExecutor] that hands out futures from a queue in order. */
    private class QueueExecutor(
        private val futures: ArrayList<AsyncFuture<IntegrityTokenData>>,
    ) : TaskExecutor {
        var calls = 0

        @Suppress("UNCHECKED_CAST")
        override fun <V> executeFuture(task: Task<V>): AsyncFuture<V> {
            calls++
            return futures.removeAt(0) as AsyncFuture<V>
        }
        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
            task.run()
        }
        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean) {
            task.run()
        }
        override fun <V> wrap(callback: Callback<V>): Callback<V> = callback
    }
}
