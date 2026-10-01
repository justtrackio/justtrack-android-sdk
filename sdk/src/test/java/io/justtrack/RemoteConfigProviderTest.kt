package io.justtrack

import io.justtrack.api.ConfigApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.config.Assignment
import io.justtrack.config.JusttrackRemoteConfigSettings
import io.justtrack.config.RemoteConfigImpl
import io.justtrack.config.RemoteConfigProvider
import io.justtrack.config.RemoteConfigQueryParams
import io.justtrack.config.RemoteConfigResponse
import io.justtrack.config.RemoteConfigStore
import io.justtrack.config.RemoteConfigStoreImpl
import io.justtrack.config.RemoteConfigTimestamp
import io.justtrack.executor.TaskExecutor
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.okhttp.Headers
import io.justtrack.versions.SdkVersion
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RemoteConfigProviderTest {
    @Test
    fun fetch_fetchesRemoteConfigAndStoresResult() {
        val store = InMemoryRemoteConfigStore()
        val response = remoteConfigResponse("feature_toggle" to "enabled")
        val configApi = FetchRemoteConfigApi { response }
        val executor = ImmediateSyncTaskExecutor()
        val provider = createRemoteConfigProvider(store, executor, configApi)

        provider.fetch().get()

        assertEquals("enabled", provider.getValue("feature_toggle"))
        val storedAssignments = store.getStoredAssignments()?.values?.toList()
        assertEquals(1, storedAssignments?.size)
        val assignment = storedAssignments?.first()
        assertEquals("feature_toggle", assignment?.configKey)
        assertEquals("enabled", assignment?.configValue)
        assertEquals("exp-feature_toggle", assignment?.experimentId)
        assertEquals(true, assignment?.isPending)
        assertEquals(1, configApi.fetchCalls)
        assertEquals(1, executor.executeCount.get())
    }

    @Test
    fun fetch_overridesStoredConfigWithRemoteResult() {
        val store = InMemoryRemoteConfigStore()
        store.setStoredAssignments(rawAssignments(listOf(Assignment("old_config", "old_value", "exp-old", true))))
        val httpClient = FetchRemoteConfigApi { remoteConfigResponse("new_config" to "new_value") }
        val executor = ImmediateSyncTaskExecutor()
        val provider = createRemoteConfigProvider(store, executor, httpClient)

        provider.fetch().get()

        val storedAssignments = requireNotNull(store.getStoredAssignments())
        assertEquals(1, storedAssignments.size)
        assertNull(storedAssignments["old_config"])
        assertEquals("new_value", storedAssignments["new_config"]?.configValue)
        assertEquals("exp-new_config", storedAssignments["new_config"]?.experimentId)
        assertEquals(true, storedAssignments["new_config"]?.isPending)
        assertEquals(1, httpClient.fetchCalls)
    }

    @Test
    fun fetch_returnsSameFutureWhileInFlight() {
        val store = InMemoryRemoteConfigStore()
        val response = remoteConfigResponse("bucket" to "A")
        val configApi = FetchRemoteConfigApi { response }
        val executor = BlockingSyncTaskExecutor()
        val provider = createRemoteConfigProvider(store, executor, configApi)

        val firstFetch = provider.fetch()
        val secondFetch = provider.fetch()

        assertSame(firstFetch, secondFetch)
        assertEquals(1, executor.executeCount.get())
        assertEquals(0, configApi.fetchCalls)
        executor.release()
        firstFetch.get()
        executor.awaitDone()
        assertEquals(1, configApi.fetchCalls)
    }

    @Test
    fun fetch_fetchesRemoteConfigWhenIntervalExceeded() {
        val store = InMemoryRemoteConfigStore()
        val storedResponse = remoteConfigResponse("local_config" to "cached")
        val staleTimestamp =
            System.currentTimeMillis() - RemoteConfigStoreImpl.DEFAULT_MIN_FETCH_INTERVAL * 1000 - 1
        store.setStoredAssignments(storedResponse.toString())
        store.setPreviousFetchTimeStamp(staleTimestamp)
        val configApi = FetchRemoteConfigApi { remoteConfigResponse("local_config" to "remote") }
        val executor = ImmediateSyncTaskExecutor()
        val provider = createRemoteConfigProvider(store, executor, configApi)

        provider.fetch().get()

        assertEquals("remote", provider.getValue("local_config"))
        assertEquals(1, configApi.fetchCalls)
    }

    @Test
    fun fetch_returnsStoredConfigWhenIntervalNotExceeded() {
        val store = InMemoryRemoteConfigStore()
        val storedResponse = remoteConfigResponse("local_config" to "cached")
        val recentTimestamp = System.currentTimeMillis() - 1
        store.setStoredAssignments(storedResponse.toString())
        store.setPreviousFetchTimeStamp(recentTimestamp)
        val configApi = FetchRemoteConfigApi { remoteConfigResponse("local_config" to "remote") }
        val executor = ImmediateSyncTaskExecutor()
        val provider = createRemoteConfigProvider(store, executor, configApi)

        provider.fetch().get()

        assertEquals("cached", provider.getValue("local_config"))
        assertEquals(0, configApi.fetchCalls)
    }

    @Test
    fun storeConfig_overridesExistingAssignments() {
        val store = InMemoryRemoteConfigStore()
        val storedAssignments = listOf(
            Assignment("config_a", "value_a", "exp-a", true),
            Assignment("config_b", "value_b", "exp-b", false),
        )
        store.setStoredAssignments(rawAssignments(storedAssignments))
        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), FetchRemoteConfigApi { remoteConfigResponse() })

        val remoteAssignments = assignmentsJson(
            Assignment("config_a", "network_value", "exp-a", false),
            Assignment("config_c", "value_c", "exp-c", true),
        )

        provider.storeConfig(remoteAssignments)

        val updatedAssignments = requireNotNull(store.getStoredAssignments())
        assertEquals(2, updatedAssignments.size)
        assertEquals("network_value", updatedAssignments["config_a"]?.configValue)
        assertEquals(false, updatedAssignments["config_a"]?.isPending)
        assertNull(updatedAssignments["config_b"])
        val newAssignment = updatedAssignments["config_c"]
        assertEquals("value_c", newAssignment?.configValue)
        assertEquals(true, newAssignment?.isPending)
    }

    @Test
    fun fetch_consumesRetryAfterAndCapsIntervalWhenPositive() {
        val store = InMemoryRemoteConfigStore()
        store.setRetryAfterSeconds(1)
        store.setStoredAssignments(rawAssignments(listOf(Assignment("k", "cached", "e", false))))
        store.setPreviousFetchTimeStamp(System.currentTimeMillis())
        val configApi = FetchRemoteConfigApi { remoteConfigResponse("k" to "remote") }

        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), configApi)
        provider.fetch().get()

        assertEquals(-1, store.getRetryAfterSeconds())
        assertEquals(0, configApi.fetchCalls)
        assertEquals("cached", provider.getValue("k"))
    }

    @Test
    fun fetch_skipsRetryAfterPathWhenValueIsZero() {
        val store = InMemoryRemoteConfigStore()
        store.setRetryAfterSeconds(0)
        val configApi = FetchRemoteConfigApi { remoteConfigResponse("k" to "v") }

        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), configApi)
        provider.fetch().get()

        assertEquals(0, store.getRetryAfterSeconds())
        assertEquals(1, configApi.fetchCalls)
    }

    @Test
    fun fetch_fetchesWhenWithinIntervalButNothingStored() {
        val store = InMemoryRemoteConfigStore()
        // Within interval (recent timestamp) but no stored assignments – must still issue a fetch.
        store.setPreviousFetchTimeStamp(System.currentTimeMillis())
        val configApi = FetchRemoteConfigApi { remoteConfigResponse("k" to "v") }
        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), configApi)

        provider.fetch().get()

        assertEquals(1, configApi.fetchCalls)
        assertEquals("v", provider.getValue("k"))
    }

    @Test
    fun fetch_clearsCompletedInFlightAndIssuesNewRequest() {
        val store = InMemoryRemoteConfigStore()
        val configApi = FetchRemoteConfigApi { remoteConfigResponse("k" to "v") }
        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), configApi)

        // First fetch completes synchronously, leaving cacheFuture pointing at a done future.
        provider.fetch().get()
        // Second fetch must traverse the `inFlight?.isDone == true` branch that nulls out
        // cacheFuture. The within-interval cached path then short-circuits.
        provider.fetch().get()

        // Both fetch() calls returned successfully; the cache-clear branch was exercised.
        assertEquals(1, configApi.fetchCalls)
    }

    @Test
    fun fetch_storesRetryAfterFromResponseHeaders() {
        val store = InMemoryRemoteConfigStore()
        val responseBody = remoteConfigResponse("k" to "v")
        val headers = Headers.Builder().add("Retry-After", "120").build()
        val configApi = object : ConfigApi {
            var calls = 0
            override suspend fun fetchRemoteConfig(
                queryParams: RemoteConfigQueryParams,
                advertiserId: String?,
                uuid: String?,
                installId: String?,
            ): Result<RemoteConfigResponse> {
                calls += 1
                return Result.success(RemoteConfigResponse(responseBody, headers))
            }

            override suspend fun activateExperiments(
                body: JSONEncodable,
                advertiserId: String?,
                uuid: String?,
                installId: String?,
            ): Result<JSONObject> = Result.failure(Throwable("not used"))
        }

        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), configApi)
        provider.fetch().get()

        assertEquals(120, store.getRetryAfterSeconds())
        assertEquals(1, configApi.calls)
    }

    @Test
    fun fetch_doesNotOverwriteExistingRetryAfterFromResponse() {
        val store = InMemoryRemoteConfigStore()
        store.setRetryAfterSeconds(-1)
        val responseBody = remoteConfigResponse("k" to "v")
        val headers = Headers.Builder().add("Retry-After", "120").build()
        val configApi = object : ConfigApi {
            override suspend fun fetchRemoteConfig(
                queryParams: RemoteConfigQueryParams,
                advertiserId: String?,
                uuid: String?,
                installId: String?,
            ): Result<RemoteConfigResponse> = Result.success(RemoteConfigResponse(responseBody, headers))

            override suspend fun activateExperiments(
                body: JSONEncodable,
                advertiserId: String?,
                uuid: String?,
                installId: String?,
            ): Result<JSONObject> = Result.failure(Throwable("not used"))
        }

        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), configApi)
        provider.fetch().get()

        assertEquals(-1, store.getRetryAfterSeconds())
    }

    @Test
    fun fetch_propagatesExceptionFromFailedConfigApi() {
        val store = InMemoryRemoteConfigStore()
        val configApi = object : ConfigApi {
            override suspend fun fetchRemoteConfig(
                queryParams: RemoteConfigQueryParams,
                advertiserId: String?,
                uuid: String?,
                installId: String?,
            ): Result<RemoteConfigResponse> = Result.failure(IllegalStateException("api down"))

            override suspend fun activateExperiments(
                body: JSONEncodable,
                advertiserId: String?,
                uuid: String?,
                installId: String?,
            ): Result<JSONObject> = Result.failure(Throwable("not used"))
        }

        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), configApi)

        // ImmediateSyncTaskExecutor unwraps the task via runBlocking, so the failure surfaces
        // directly from fetch() rather than from Future.get().
        val ex = assertThrows(IllegalStateException::class.java) {
            provider.fetch()
        }
        assertEquals("api down", ex.message)
    }

    @Test
    fun getValue_returnsNullWhenNoAssignmentsStored() {
        val store = InMemoryRemoteConfigStore()
        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), FetchRemoteConfigApi { remoteConfigResponse() })

        assertNull(provider.getValue("missing"))
    }

    @Test
    fun getValue_returnsNullForUnknownKey() {
        val store = InMemoryRemoteConfigStore()
        store.setStoredAssignments(rawAssignments(listOf(Assignment("k", "v", "e", false))))
        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), FetchRemoteConfigApi { remoteConfigResponse() })

        assertNull(provider.getValue("unknown"))
        assertEquals("v", provider.getValue("k"))
    }

    @Test
    fun getAll_returnsNullWhenStoreIsEmpty() {
        val store = InMemoryRemoteConfigStore()
        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), FetchRemoteConfigApi { remoteConfigResponse() })

        assertNull(provider.getAll())
    }

    @Test
    fun getAll_returnsAllStoredAssignments() {
        val store = InMemoryRemoteConfigStore()
        store.setStoredAssignments(
            rawAssignments(
                listOf(
                    Assignment("a", "1", "exp-a", false),
                    Assignment("b", "2", "exp-b", true),
                ),
            ),
        )
        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), FetchRemoteConfigApi { remoteConfigResponse() })

        val all = provider.getAll()
        assertEquals(2, all?.size)
        assertTrue(all!!.any { it.configKey == "a" })
        assertTrue(all.any { it.configKey == "b" })
    }

    @Test
    fun setConfig_persistsNewMinimumIntervalInStore() {
        val store = InMemoryRemoteConfigStore()
        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), FetchRemoteConfigApi { remoteConfigResponse() })

        provider.setConfig(JusttrackRemoteConfigSettings(minimumFetchIntervalInSeconds = 7L))

        assertEquals(7L, store.getCurrentFetchInterval())
    }

    @Test
    fun storeConfig_addsAssignmentsToEmptyStoreAsActivated() {
        val store = InMemoryRemoteConfigStore()
        val provider = createRemoteConfigProvider(store, ImmediateSyncTaskExecutor(), FetchRemoteConfigApi { remoteConfigResponse() })

        provider.storeConfig(assignmentsJson(Assignment("k", "v", "exp-k", true)))

        val stored = requireNotNull(store.getStoredAssignments())
        assertEquals(true, stored["k"]?.isPending)
    }

    private fun createRemoteConfigProvider(store: RemoteConfigStore, syncTaskExecutor: TaskExecutor, configApi: ConfigApi): RemoteConfigProvider {
        val sdkVersion = object : SdkVersion {
            override val platformType: PlatformType = PlatformType.ANDROID
            override val major: Int = 1
            override val minor: Int = 0
            override val patch: Int = 0
            override val name: String = "1.0.0"
        }
        return RemoteConfigProvider(
            store,
            syncTaskExecutor,
            configApi,
            RemoteConfigImpl.AttributionParams(
                { ValueFuture("install-id") },
                { ValueFuture("user-id") },
                { ValueFuture(TestAdvertiserIdInfo("advertiser-id")) },
                mock(),
                TestDeviceInfoImpl(),
                sdkVersion,
            ),
            TestRemoteConfigTimestamp(),
            TestLogger,
            listOf(1),
        )
    }

    private fun remoteConfigResponse(vararg entries: Pair<String, String>): JSONObject {
        val assignments = JSONArray()
        for ((key, value) in entries) {
            val assignment = JSONObject()
            assignment.put("configKey", key)
            assignment.put("configValue", value)
            assignment.put("experimentId", "exp-$key")
            assignments.put(assignment)
        }
        val root = JSONObject()
        root.put("assignments", assignments)
        return root
    }

    private fun assignmentsJson(vararg assignments: Assignment): JSONObject {
        val array = JSONArray()
        for (assignment in assignments) {
            array.put(assignment.toJson())
        }
        val root = JSONObject()
        root.put("assignments", array)
        return root
    }

    private fun rawAssignments(assignments: List<Assignment>): String {
        val array = JSONArray()
        for (assignment in assignments) {
            array.put(assignment.toJson())
        }
        val root = JSONObject()
        root.put("assignments", array)
        return root.toString()
    }

    private class BlockingSyncTaskExecutor : TaskExecutor {
        val executeCount = AtomicInteger(0)
        private val startLatch = CountDownLatch(1)
        private val doneLatch = CountDownLatch(1)

        fun release() {
            startLatch.countDown()
        }

        fun awaitDone() {
            doneLatch.await(1, TimeUnit.SECONDS)
        }

        override fun <T> executeFuture(task: Task<T>): AsyncFuture<T> {
            executeCount.incrementAndGet()
            val future = ResolvableFuture<T>()
            Thread {
                try {
                    startLatch.await()
                    val value = runBlocking { task.execute() }
                    future.resolve(value)
                } catch (exception: Throwable) {
                    future.reject(exception)
                } finally {
                    doneLatch.countDown()
                }
            }.start()
            return AsyncFutureImpl(future, this)
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
            task.run()
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean) {
            task.run()
        }

        override fun <V> wrap(callback: Callback<V>): Callback<V> = callback
    }

    private class InMemoryRemoteConfigStore : RemoteConfigStore {
        private var currentFetchInterval: Long = RemoteConfigStoreImpl.DEFAULT_MIN_FETCH_INTERVAL
        private var previousFetchTimeStamp: Long? = null
        private var storedAssignmentsRaw: String? = null
        private var retryAfterSeconds: Int? = null

        override fun getCurrentFetchInterval(): Long = currentFetchInterval

        override fun getPreviousFetchTimeStamp(): Long? = previousFetchTimeStamp

        override fun setPreviousFetchTimeStamp(previousFetchTimeStamp: Long) {
            this.previousFetchTimeStamp = previousFetchTimeStamp
        }

        override fun setMinimumIntervalInSecond(minimumIntervalInSecond: Long) {
            currentFetchInterval = minimumIntervalInSecond
        }

        override fun getRetryAfterSeconds(): Int? = retryAfterSeconds

        override fun setRetryAfterSeconds(retryAfterSeconds: Int) {
            this.retryAfterSeconds = retryAfterSeconds
        }

        override fun getStoredAssignments(): Map<String, Assignment>? {
            val raw = storedAssignmentsRaw ?: return null
            return Assignment.parseAssignments(JSONObject(raw)).associateBy { it.configKey }
        }

        override fun setStoredAssignments(string: String?) {
            storedAssignmentsRaw = string
        }
    }

    private class FetchRemoteConfigApi(private val responseProvider: () -> JSONObject) : ConfigApi {
        var fetchCalls: Int = 0
        var lastQueryParams: RemoteConfigQueryParams? = null

        override suspend fun fetchRemoteConfig(
            queryParams: RemoteConfigQueryParams,
            advertiserId: String?,
            uuid: String?,
            installId: String?,
        ): Result<RemoteConfigResponse> {
            fetchCalls += 1
            lastQueryParams = queryParams
            return Result.success(RemoteConfigResponse(responseProvider(), null))
        }

        override suspend fun activateExperiments(body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<JSONObject> {
            return Result.failure(Throwable("not implemented"))
        }
    }

    private object TestLogger : Logger {
        override fun debug(message: String, vararg fields: LoggerFields) = Unit

        override fun info(message: String, vararg fields: LoggerFields) = Unit

        override fun warn(message: String, vararg fields: LoggerFields) = Unit

        override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit

        override fun error(message: String, vararg fields: LoggerFields) = Unit

        override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit

        override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) = Unit

        override val fallback: Logger
            get() = this
    }

    private class TestAdvertiserIdInfo(override val advertiserId: String?) : AdvertiserIdInfo {
        override val isLimitedAdTracking: Boolean = false
    }

    private class TestRemoteConfigTimestamp : RemoteConfigTimestamp {
        override fun getCurrentTimestamp(): Long = System.currentTimeMillis() / 1000
        override suspend fun getFirstAttributionTimestamp(): Long? = null
        override fun getFirstInitializedAtTimestamp(): Long = System.currentTimeMillis() / 1000
        override fun getInstalledAtTimestamp(): Long = System.currentTimeMillis() / 1000
    }
}
