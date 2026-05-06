package io.justtrack

import android.content.Context
import android.content.SharedPreferences
import io.justtrack.config.Assignment
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.config.RemoteConfigImpl
import io.justtrack.config.RemoteConfigStoreImpl
import io.justtrack.exceptions.SdkNotTrackingException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean

class RemoteConfigImplTest {
    @Test
    fun fetch_returnsErrorFutureWhenNotTracking() {
        val remoteConfig = createRemoteConfig(isTracking = false, rawAssignments = null)

        val future = remoteConfig.fetch()

        assertThrowsSdkNotTracking(future)
    }

    @Test
    fun activate_returnsErrorFutureWhenNotTracking() {
        val remoteConfig = createRemoteConfig(isTracking = false, rawAssignments = null)

        val future = remoteConfig.activate(listOf("exp-a"))

        assertThrowsSdkNotTracking(future)
    }

    @Test
    fun fetchAndActivate_returnsErrorFutureWhenNotTracking() {
        val remoteConfig = createRemoteConfig(isTracking = false, rawAssignments = null)

        val future = remoteConfig.fetchAndActivate()

        assertThrowsSdkNotTracking(future)
    }

    @Test
    fun getters_parseStoredValues() {
        val rawAssignments = rawAssignments(
            Assignment("boolean_true", " true ", "exp-a", false),
            Assignment("boolean_false", "FALSE", "exp-b", false),
            Assignment("int_value", "42", "exp-c", false),
            Assignment("long_value", "9223372036854775807", "exp-d", false),
            Assignment("double_value", "3.14", "exp-e", false),
            Assignment("string_value", "hello", "exp-f", false),
            Assignment("invalid_bool", "yes", "exp-g", false),
            Assignment("invalid_int", "not-int", "exp-h", false),
            Assignment("invalid_long", "", "exp-i", false),
            Assignment("invalid_double", "abc", "exp-j", false),
        )
        val remoteConfig = createRemoteConfig(isTracking = true, rawAssignments = rawAssignments)

        assertEquals(true, remoteConfig.getBoolean("boolean_true"))
        assertEquals(false, remoteConfig.getBoolean("boolean_false"))
        assertNull(remoteConfig.getBoolean("invalid_bool"))
        assertEquals(42, remoteConfig.getInt("int_value"))
        assertNull(remoteConfig.getInt("invalid_int"))
        assertEquals(9223372036854775807L, remoteConfig.getLong("long_value"))
        assertNull(remoteConfig.getLong("invalid_long"))
        assertEquals(3.14, remoteConfig.getDouble("double_value") ?: 0.0, 0.0)
        assertNull(remoteConfig.getDouble("invalid_double"))
        assertEquals("hello", remoteConfig.getString("string_value"))
        assertNull(remoteConfig.getString("missing_key"))
    }

    @Test
    fun getAll_returnsAssignmentsFromStore() {
        val rawAssignments = rawAssignments(
            Assignment("config_a", "value_a", "exp-a", false),
            Assignment("config_b", "value_b", "exp-b", true),
        )
        val remoteConfig = createRemoteConfig(isTracking = true, rawAssignments = rawAssignments)

        val assignments = remoteConfig.getAll()

        assertEquals(2, assignments?.size)
        val keys = assignments?.map { it.configKey }?.toSet()
        assertEquals(setOf("config_a", "config_b"), keys)
    }

    private fun createRemoteConfig(isTracking: Boolean, rawAssignments: String?): RemoteConfigImpl {
        val sharedPreferences = mock<SharedPreferences>()
        whenever(sharedPreferences.getLong(any(), any())).thenAnswer { it.arguments[1] as Long }
        whenever(sharedPreferences.getInt(any(), any())).thenAnswer { it.arguments[1] as Int }
        whenever(sharedPreferences.getBoolean(any(), any())).thenAnswer { it.arguments[1] as Boolean }
        whenever(sharedPreferences.getString(eq(RemoteConfigStoreImpl.STORE_ASSIGNMENTS_KEY), anyOrNull())).thenReturn(rawAssignments)

        val context = mock<Context>()
        whenever(context.getSharedPreferences(eq(RemoteConfigStoreImpl.STORE_NAME), eq(Context.MODE_PRIVATE))).thenReturn(sharedPreferences)

        val sdkVersion = object : io.justtrack.versions.SdkVersion {
            override val platformType: PlatformType = PlatformType.ANDROID
            override val major: Int = 1
            override val minor: Int = 0
            override val patch: Int = 0
            override val name: String = "1.0.0"
        }

        return RemoteConfigImpl(
            context = context,
            isTracking = AtomicBoolean(isTracking),
            attributionParams = RemoteConfigImpl.AttributionParams(
                { ValueFuture("install-instance") },
                { ValueFuture("user-id") },
                { ValueFuture(TestAdvertiserIdInfo("advertiser-id")) },
                mock(),
                FakeDeviceInfo(),
                sdkVersion,
            ),
            sdkFirstInitializationTimestampRepo = mock(),
            taskExecutor = ImmediateTaskExecutor(),
            httpClient = mock<HttpClient>(),
            logger = mock(),
        )
    }

    private fun rawAssignments(vararg assignments: Assignment): String {
        val array = JSONArray()
        for (assignment in assignments) {
            array.put(assignment.toJson())
        }
        val root = JSONObject()
        root.put("assignments", array)
        return root.toString()
    }

    private fun assertThrowsSdkNotTracking(future: AsyncFuture<Void?>) {
        try {
            future.get()
            fail("Expected ExecutionException")
        } catch (exception: ExecutionException) {
            val cause = exception.cause
            if (cause !is SdkNotTrackingException) {
                fail("Expected SdkNotTrackingException but was ${cause?.javaClass}")
            }
        }
    }

    private class TestAdvertiserIdInfo(override val advertiserId: String?) : AdvertiserIdInfo {
        override val isLimitedAdTracking: Boolean = false
    }

    private class ImmediateTaskExecutor : TaskExecutor {
        override fun <T> executeAsFuture(task: Task<T>): AsyncFuture<T> {
            val value = runBlocking { task.execute() }
            return ValueFuture(value)
        }

        override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
            task.run()
        }
    }

    private class FakeDeviceInfo : DeviceInfo {
        override fun getConnectionType(): ConnectionType = ConnectionType.WIFI

        override fun getAppVersion(): ApplicationVersion = object : ApplicationVersion {
            override fun getVersionName(): String = "1.0.0"
            override fun getVersionCode(): String = "100"
        }

        override fun getAppName(): String? = "justtrack"

        override fun getApplicationPackageName(): String = "io.justtrack"

        override fun getAndroidId(): String? = "android-id"

        override fun getAndroidIdOrDefault(def: String): String = "android-id"

        override fun getCountryIso(): String? = "DE"

        override fun getDeviceLocale(): String? = "EN"

        override fun getDeviceType(): DeviceType = DeviceType.PHONE

        override fun getDisplaySize(): android.graphics.Point = android.graphics.Point(100, 100)

        override val deviceName: String
            get() = "test-device"
        override val deviceModel: String
            get() = "test-model"
        override val deviceProduct: String
            get() = "test-product"
        override val osVersion: String
            get() = "14"
        override val osName: String
            get() = "Android"
        override val osLevel: Int
            get() = 34
        override val cpuArch: String?
            get() = "arm64"
        override val build: String
            get() = "build"
    }
}
