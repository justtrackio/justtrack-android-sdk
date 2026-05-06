package io.justtrack

import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.config.Assignment
import io.justtrack.config.RemoteConfigActivator
import io.justtrack.config.RemoteConfigImpl
import io.justtrack.config.RemoteConfigStore
import io.justtrack.config.RemoteConfigStoreImpl
import io.justtrack.log.Logger
import io.justtrack.versions.SdkVersion
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
        val httpClient = ActivateExperimentsHttpClient()
        val executor = ImmediateTaskExecutor()
        val activator = RemoteConfigActivator(
            store,
            executor,
            httpClient,
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
        assertEquals(true, assignmentByExperiment?.get("exp-a")?.isPending)
        assertEquals(false, assignmentByExperiment?.get("exp-b")?.isPending)
        assertEquals(1, httpClient.activateCalls.get())
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

    private class ActivateExperimentsHttpClient : TestHttpClient {
        val activateCalls = AtomicInteger(0)

        override suspend fun activateExperiments(
            logger: Logger,
            body: JSONEncodable,
            advertiserId: String?,
            uuid: String?,
            installId: String?,
        ): Result<JSONObject> {
            activateCalls.incrementAndGet()
            return Result.success(JSONObject())
        }
    }

    private class TestAdvertiserIdInfo(override val advertiserId: String?) : AdvertiserIdInfo {
        override val isLimitedAdTracking: Boolean = false
    }
}
