package io.justtrack

import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.config.Assignment
import io.justtrack.config.RemoteConfigImpl
import io.justtrack.config.RemoteConfigProvider
import io.justtrack.config.RemoteConfigQueryParams
import io.justtrack.config.RemoteConfigResponse
import io.justtrack.config.RemoteConfigStore
import io.justtrack.config.RemoteConfigStoreImpl
import io.justtrack.config.RemoteConfigTimestamp
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.versions.SdkVersion
import org.mockito.kotlin.mock
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RemoteConfigProviderTest {
    @Test
    fun fetch_fetchesRemoteConfigAndStoresResult() {
        val store = InMemoryRemoteConfigStore()
        val response = remoteConfigResponse("feature_toggle" to "enabled")
        val httpClient = FetchRemoteConfigHttpClient { response }
        val executor = ImmediateTaskExecutor()
        val provider = createRemoteConfigProvider(store, executor, httpClient)

        provider.fetch().get()

        assertEquals("enabled", provider.getValue("feature_toggle"))
        val storedAssignments = store.getStoredAssignments()?.values?.toList()
        assertEquals(1, storedAssignments?.size)
        val assignment = storedAssignments?.first()
        assertEquals("feature_toggle", assignment?.configKey)
        assertEquals("enabled", assignment?.configValue)
        assertEquals("exp-feature_toggle", assignment?.experimentId)
        assertEquals(false, assignment?.isPending)
        assertEquals(1, httpClient.fetchCalls)
        assertEquals(1, executor.executeCount.get())
    }

    @Test
    fun fetch_returnsSameFutureWhileInFlight() {
        val store = InMemoryRemoteConfigStore()
        val response = remoteConfigResponse("bucket" to "A")
        val httpClient = FetchRemoteConfigHttpClient { response }
        val executor = BlockingTaskExecutor()
        val provider = createRemoteConfigProvider(store, executor, httpClient)

        val firstFetch = provider.fetch()
        val secondFetch = provider.fetch()

        assertSame(firstFetch, secondFetch)
        assertEquals(1, executor.executeCount.get())
        assertEquals(0, httpClient.fetchCalls)
        executor.release()
        firstFetch.get()
        executor.awaitDone()
        assertEquals(1, httpClient.fetchCalls)
    }

    @Test
    fun fetch_fetchesRemoteConfigWhenIntervalExceeded() {
        val store = InMemoryRemoteConfigStore()
        val storedResponse = remoteConfigResponse("local_config" to "cached")
        val staleTimestamp =
            System.currentTimeMillis() - RemoteConfigStoreImpl.DEFAULT_MIN_FETCH_INTERVAL * 1000 - 1
        store.setStoredAssignments(storedResponse.toString())
        store.setPreviousFetchTimeStamp(staleTimestamp)

        val httpClient = FetchRemoteConfigHttpClient { remoteConfigResponse("local_config" to "remote") }
        val executor = ImmediateTaskExecutor()
        val provider = createRemoteConfigProvider(store, executor, httpClient)

        provider.fetch().get()

        assertEquals("remote", provider.getValue("local_config"))
        assertEquals(1, httpClient.fetchCalls)
    }

    @Test
    fun fetch_returnsStoredConfigWhenIntervalNotExceeded() {
        val store = InMemoryRemoteConfigStore()
        val storedResponse = remoteConfigResponse("local_config" to "cached")
        val recentTimestamp = System.currentTimeMillis() - 1
        store.setStoredAssignments(storedResponse.toString())
        store.setPreviousFetchTimeStamp(recentTimestamp)

        val httpClient = FetchRemoteConfigHttpClient { remoteConfigResponse("local_config" to "remote") }
        val executor = ImmediateTaskExecutor()
        val provider = createRemoteConfigProvider(store, executor, httpClient)

        provider.fetch().get()

        assertEquals("cached", provider.getValue("local_config"))
        assertEquals(0, httpClient.fetchCalls)
    }

    @Test
    fun storeConfig_preservesExistingAssignmentsAndAddsUniqueOnes() {
        val store = InMemoryRemoteConfigStore()
        val storedAssignments = listOf(
            Assignment("config_a", "value_a", "exp-a", true),
            Assignment("config_b", "value_b", "exp-b", false),
        )
        store.setStoredAssignments(rawAssignments(storedAssignments))
        val provider = createRemoteConfigProvider(store, ImmediateTaskExecutor(), FetchRemoteConfigHttpClient { remoteConfigResponse() })

        val remoteAssignments = assignmentsJson(
            Assignment("config_a", "network_value", "exp-a", false),
            Assignment("config_c", "value_c", "exp-c", true),
        )

        provider.storeConfig(remoteAssignments)

        val updatedAssignments = requireNotNull(store.getStoredAssignments())
        assertEquals(3, updatedAssignments.size)
        assertEquals(true, updatedAssignments["config_a"]?.isPending)
        assertEquals("network_value", updatedAssignments["config_a"]?.configValue)
        assertEquals(false, updatedAssignments["config_b"]?.isPending)
        assertEquals("value_b", updatedAssignments["config_b"]?.configValue)
        val newAssignment = updatedAssignments["config_c"]
        assertEquals("value_c", newAssignment?.configValue)
        assertEquals(false, newAssignment?.isPending)
    }

    private fun createRemoteConfigProvider(store: RemoteConfigStore, taskExecutor: TaskExecutor, httpClient: HttpClient): RemoteConfigProvider {
        val sdkVersion = object : SdkVersion {
            override val platformType: PlatformType = PlatformType.ANDROID
            override val major: Int = 1
            override val minor: Int = 0
            override val patch: Int = 0
            override val name: String = "1.0.0"
        }
        return RemoteConfigProvider(
            store,
            taskExecutor,
            httpClient,
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

    private class ImmediateTaskExecutor : TaskExecutor {
        val executeCount = AtomicInteger(0)

        override fun <T> executeAsFuture(task: Task<T>): AsyncFuture<T> {
            executeCount.incrementAndGet()
            val value = runBlocking { task.execute() }
            return ValueFuture(value)
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
            task.run()
        }
    }

    private class BlockingTaskExecutor : TaskExecutor {
        val executeCount = AtomicInteger(0)
        private val startLatch = CountDownLatch(1)
        private val doneLatch = CountDownLatch(1)

        fun release() {
            startLatch.countDown()
        }

        fun awaitDone() {
            doneLatch.await(1, TimeUnit.SECONDS)
        }

        override fun <T> executeAsFuture(task: Task<T>): AsyncFuture<T> {
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
            return future
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
            task.run()
        }
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

    private class FetchRemoteConfigHttpClient(private val responseProvider: () -> JSONObject) : TestHttpClient {
        var fetchCalls: Int = 0
        var lastQueryParams: RemoteConfigQueryParams? = null

        override suspend fun fetchRemoteConfig(
            logger: Logger,
            queryParams: RemoteConfigQueryParams,
            advertiserId: String?,
            uuid: String?,
            installId: String?,
        ): Result<RemoteConfigResponse> {
            fetchCalls += 1
            lastQueryParams = queryParams
            return Result.success(RemoteConfigResponse(responseProvider(), null))
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
