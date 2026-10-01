package io.justtrack

import io.justtrack.api.ConfigApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.config.Assignment
import io.justtrack.config.RemoteConfigActivator
import io.justtrack.config.RemoteConfigImpl
import io.justtrack.config.RemoteConfigQueryParams
import io.justtrack.config.RemoteConfigResponse
import io.justtrack.config.RemoteConfigStore
import io.justtrack.config.RemoteConfigStoreImpl
import io.justtrack.versions.SdkVersion
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.atomic.AtomicInteger

class RemoteConfigActivatorTest {
    @Test
    fun activate_updatesPendingStatusForActivatedExperiments() {
        val store = InMemoryRemoteConfigStore()
        val sdkVersion = object : SdkVersion {
            override val platformType: PlatformType = PlatformType.ANDROID
            override val major: Int = 1
            override val minor: Int = 0
            override val patch: Int = 0
            override val name: String = "1.0.0"
        }
        val storedAssignments = listOf(
            Assignment(
                "config_a",
                "value_a",
                "exp-a",
                false,
            ),
            Assignment(
                "config_b",
                "value_b",
                "exp-b",
                false,
            ),
        )
        store.setStoredAssignments(rawAssignments(storedAssignments))
        val configApi = ActivateExperimentsConfigApi()
        val executor = ImmediateSyncTaskExecutor()
        val activator = RemoteConfigActivator(
            store,
            executor,
            configApi,
            RemoteConfigImpl.AttributionParams(
                { ValueFuture("install-id") },
                { ValueFuture("user-id") },
                { ValueFuture(TestAdvertiserIdInfo("advertiser-id")) },
                mock(),
                TestDeviceInfoImpl(),
                sdkVersion,
            ),
            TestLogger(),
            listOf(1),
        )

        activator.activate(listOf("exp-a")).get()

        val updatedAssignments = store.getStoredAssignments()
        val assignmentByExperiment = updatedAssignments?.values?.associateBy { it.experimentId }
        assertEquals(false, assignmentByExperiment?.get("exp-a")?.isPending)
        assertEquals(false, assignmentByExperiment?.get("exp-b")?.isPending)
        assertEquals(0, configApi.activateCalls.get())
    }

    @Test
    fun activate_doesNothingForEmptyExperimentList() {
        val store = InMemoryRemoteConfigStore()
        store.setStoredAssignments(rawAssignments(listOf(Assignment("k", "v", "exp-a", false))))
        val configApi = ActivateExperimentsConfigApi()
        val activator = createActivator(store, configApi)

        activator.activate(emptyList()).get()

        // Empty list short-circuits before calling the API.
        assertEquals(0, configApi.activateCalls.get())
        // Stored state unchanged.
        assertEquals(false, store.getStoredAssignments()?.get("k")?.isPending)
    }

    @Test
    fun activate_doesNothingWhenStoredAssignmentsAreMissing() {
        val store = InMemoryRemoteConfigStore()
        // No setStoredAssignments – getStoredAssignments() returns null.
        val configApi = ActivateExperimentsConfigApi()
        val activator = createActivator(store, configApi)

        activator.activate(listOf("exp-a")).get()

        // API was called but no updates happened.
        assertEquals(1, configApi.activateCalls.get())
        assertEquals(null, store.getStoredAssignments())
    }

    @Test
    fun activate_doesNotRewriteStoreWhenAllExperimentsAreAlreadyPending() {
        val store = InMemoryRemoteConfigStore()
        // Already-pending and non-matching experiments should not cause a rewrite.
        store.setStoredAssignments(
            rawAssignments(
                listOf(
                    Assignment("k1", "v1", "exp-pending", true),
                    Assignment("k2", "v2", "exp-other", false),
                ),
            ),
        )
        val rawBefore = store.rawStoredAssignments()
        val activator = createActivator(store, ActivateExperimentsConfigApi())

        activator.activate(listOf("exp-pending")).get()

        // Store payload byte-for-byte identical (early-return when nothing changed).
        assertEquals(rawBefore, store.rawStoredAssignments())
    }

    @Test
    fun activate_propagatesExceptionWhenApiCallFails() {
        val store = InMemoryRemoteConfigStore()
        store.setStoredAssignments(rawAssignments(listOf(Assignment("k", "v", "exp-a", true))))
        val configApi = object : ConfigApi {
            override suspend fun fetchRemoteConfig(
                queryParams: RemoteConfigQueryParams,
                advertiserId: String?,
                uuid: String?,
                installId: String?,
            ): Result<RemoteConfigResponse> = Result.failure(Throwable("unused"))

            override suspend fun activateExperiments(
                body: JSONEncodable,
                advertiserId: String?,
                uuid: String?,
                installId: String?,
            ): Result<JSONObject> = Result.failure(IllegalStateException("activate failed"))
        }
        val activator = createActivator(store, configApi)

        // ImmediateSyncTaskExecutor surfaces task failures synchronously from executeFuture.
        val ex = assertThrows(IllegalStateException::class.java) {
            activator.activate(listOf("exp-a"))
        }
        assertEquals("activate failed", ex.message)
        // Stored state unchanged because the failure prevented updateStoredPendingStatus.
        assertEquals(true, store.getStoredAssignments()?.get("k")?.isPending)
    }

    @Test
    fun filterRemoteConfig_filtersOutNonPendingByExperimentId() {
        val store = InMemoryRemoteConfigStore()
        store.setStoredAssignments(
            rawAssignments(
                listOf(
                    Assignment("config_a", "v1", "exp-a", false),
                    Assignment("config_b", "v2", "exp-b", true),
                ),
            ),
        )
        val activator = createActivator(store, ActivateExperimentsConfigApi())

        val result = activator.filterRemoteConfig(listOf("exp-a", "exp-b", "exp-c"))

        // exp-a is non-pending so filtered out; exp-b is pending and exp-c is unknown so both pass
        assertEquals(listOf("exp-b", "exp-c"), result)
    }

    @Test
    fun filterRemoteConfig_returnsAllWhenStoreIsEmpty() {
        val store = InMemoryRemoteConfigStore()
        val activator = createActivator(store, ActivateExperimentsConfigApi())

        val result = activator.filterRemoteConfig(listOf("exp-a", "exp-b"))

        assertEquals(listOf("exp-a", "exp-b"), result)
    }

    private fun createActivator(store: RemoteConfigStore, configApi: ConfigApi): RemoteConfigActivator {
        val sdkVersion = object : SdkVersion {
            override val platformType: PlatformType = PlatformType.ANDROID
            override val major: Int = 1
            override val minor: Int = 0
            override val patch: Int = 0
            override val name: String = "1.0.0"
        }
        return RemoteConfigActivator(
            store,
            ImmediateSyncTaskExecutor(),
            configApi,
            RemoteConfigImpl.AttributionParams(
                { ValueFuture("install-id") },
                { ValueFuture("user-id") },
                { ValueFuture(TestAdvertiserIdInfo("advertiser-id")) },
                mock(),
                TestDeviceInfoImpl(),
                sdkVersion,
            ),
            TestLogger(),
            listOf(1),
        )
    }

    private fun rawAssignments(assignments: List<Assignment>): String {
        val assignmentsArray = JSONArray()
        for (assignment in assignments) {
            assignmentsArray.put(assignment.toJson())
        }
        val root = JSONObject()
        root.put("assignments", assignmentsArray)
        return root.toString()
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

        fun rawStoredAssignments(): String? = storedAssignmentsRaw
    }

    private class ActivateExperimentsConfigApi : ConfigApi {
        val activateCalls = AtomicInteger(0)
        override suspend fun fetchRemoteConfig(
            queryParams: RemoteConfigQueryParams,
            advertiserId: String?,
            uuid: String?,
            installId: String?,
        ): Result<RemoteConfigResponse> {
            return Result.failure(Throwable("not implemented"))
        }

        override suspend fun activateExperiments(body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<JSONObject> {
            activateCalls.incrementAndGet()
            return Result.success(JSONObject())
        }
    }

    private class TestAdvertiserIdInfo(override val advertiserId: String?) : AdvertiserIdInfo {
        override val isLimitedAdTracking: Boolean = false
    }
}
