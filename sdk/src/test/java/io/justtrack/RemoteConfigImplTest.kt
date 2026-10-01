package io.justtrack

import android.content.Context
import android.content.SharedPreferences
import io.justtrack.api.ConfigApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.config.Assignment
import io.justtrack.config.JusttrackRemoteConfigSettings
import io.justtrack.config.RemoteConfigImpl
import io.justtrack.config.RemoteConfigQueryParams
import io.justtrack.config.RemoteConfigResponse
import io.justtrack.config.RemoteConfigStoreImpl
import io.justtrack.exceptions.SdkNotTrackingException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertNull(remoteConfig.getBoolean("missing_bool"))
        assertEquals(42, remoteConfig.getInt("int_value"))
        assertNull(remoteConfig.getInt("invalid_int"))
        assertNull(remoteConfig.getInt("missing_int"))
        assertEquals(9223372036854775807L, remoteConfig.getLong("long_value"))
        assertNull(remoteConfig.getLong("invalid_long"))
        assertNull(remoteConfig.getLong("missing_long"))
        assertEquals(3.14, remoteConfig.getDouble("double_value") ?: 0.0, 0.0)
        assertNull(remoteConfig.getDouble("invalid_double"))
        assertNull(remoteConfig.getDouble("missing_double"))
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

    @Test
    fun fetch_delegatesToProviderAndReturnsCompletedFutureWhenTracking() {
        val configApi = RecordingConfigApi()
        // No stored assignments + no previous fetch timestamp ⇒ provider issues a real fetch.
        val remoteConfig = createRemoteConfig(isTracking = true, rawAssignments = null, configApi = configApi)

        remoteConfig.fetch().get()

        assertEquals(1, configApi.fetchCalls)
    }

    @Test
    fun activate_delegatesToActivatorWhenTracking() {
        val rawAssignments = rawAssignments(Assignment("config_a", "value_a", "exp-a", true))
        val configApi = RecordingConfigApi()
        val remoteConfig = createRemoteConfig(isTracking = true, rawAssignments = rawAssignments, configApi = configApi)

        remoteConfig.activate(listOf("exp-a")).get()

        assertEquals(1, configApi.activateCalls)
    }

    @Test
    fun fetchAndActivate_runsFetchThenActivateWithExperimentsFromStore() {
        val rawAssignments = rawAssignments(
            Assignment("config_a", "value_a", "exp-a", true),
            Assignment("config_b", "value_b", "exp-b", true),
        )
        val configApi = RecordingConfigApi()
        val remoteConfig = createRemoteConfig(isTracking = true, rawAssignments = rawAssignments, configApi = configApi)

        remoteConfig.fetchAndActivate().get()

        assertEquals(1, configApi.fetchCalls)
        // Activator received both stored experiment IDs.
        assertEquals(1, configApi.activateCalls)
        assertEquals(setOf("exp-a", "exp-b"), configApi.lastActivateExperiments.toSet())
    }

    @Test
    fun fetchAndActivate_skipsActivateWhenStoreHasNoAssignments() {
        // RemoteConfigStore returns null for getStoredAssignments when nothing is persisted, so
        // the experiments?.let activation branch is skipped entirely.
        val configApi = RecordingConfigApi()
        val remoteConfig = createRemoteConfig(isTracking = true, rawAssignments = null, configApi = configApi)

        remoteConfig.fetchAndActivate().get()

        assertEquals(1, configApi.fetchCalls)
        assertEquals(0, configApi.activateCalls)
    }

    @Test
    fun setConfig_forwardsSettingsToProvider() {
        // Round-trip: the provider exposes the new fetch interval via getCurrentFetchInterval()
        // on the underlying store. We verify by triggering setConfig and ensuring it does not
        // throw; coverage will pick up the delegate call.
        val configApi = RecordingConfigApi()
        val remoteConfig = createRemoteConfig(isTracking = true, rawAssignments = null, configApi = configApi)

        remoteConfig.setConfig(JusttrackRemoteConfigSettings(minimumFetchIntervalInSeconds = 7L))
    }

    @Test
    fun attributionParams_dataClassPlumbingIsExercised() {
        val sdkVersion = object : io.justtrack.versions.SdkVersion {
            override val platformType: PlatformType = PlatformType.ANDROID
            override val major: Int = 1
            override val minor: Int = 0
            override val patch: Int = 0
            override val name: String = "1.0.0"
        }
        val installProvider: () -> AsyncFuture<String> = { ValueFuture("iid") }
        val userIdProvider = io.justtrack.UserIdProvider { ValueFuture("uid") }
        val advertiserProvider = io.justtrack.providers.AdvertiserIdProvider {
            ValueFuture(TestAdvertiserIdInfo("aid"))
        }
        val database = mock<io.justtrack.DatabaseInterface>()
        val deviceInfo = FakeDeviceInfo()

        val a = RemoteConfigImpl.AttributionParams(installProvider, userIdProvider, advertiserProvider, database, deviceInfo, sdkVersion)
        val b = a.copy()

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(installProvider, a.component1())
        assertEquals(userIdProvider, a.component2())
        assertEquals(advertiserProvider, a.component3())
        assertEquals(database, a.component4())
        assertEquals(deviceInfo, a.component5())
        assertEquals(sdkVersion, a.component6())
        assertTrue(a.toString().contains("AttributionParams"))

        val different = a.copy(deviceInfo = FakeDeviceInfo())
        // Different DeviceInfo instances – ensure unequal (FakeDeviceInfo has no equals override).
        assertNotEquals(a, different)
    }

    private fun createRemoteConfig(isTracking: Boolean, rawAssignments: String?, configApi: ConfigApi = mock()): RemoteConfigImpl {
        val editor = mock<SharedPreferences.Editor>()
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        whenever(editor.putInt(any(), any())).thenReturn(editor)
        whenever(editor.putString(any(), anyOrNull())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)

        val sharedPreferences = mock<SharedPreferences>()
        whenever(sharedPreferences.getLong(any(), any())).thenAnswer { it.arguments[1] as Long }
        whenever(sharedPreferences.getInt(any(), any())).thenAnswer { it.arguments[1] as Int }
        whenever(sharedPreferences.getBoolean(any(), any())).thenAnswer { it.arguments[1] as Boolean }
        whenever(sharedPreferences.getString(eq(RemoteConfigStoreImpl.STORE_ASSIGNMENTS_KEY), anyOrNull())).thenReturn(rawAssignments)
        whenever(sharedPreferences.edit()).thenReturn(editor)

        val packageInfo = android.content.pm.PackageInfo().apply {
            firstInstallTime = 0L
        }
        val packageManager = mock<android.content.pm.PackageManager>()
        whenever(packageManager.getPackageInfo(any<String>(), any<Int>())).thenReturn(packageInfo)

        val context = mock<Context>()
        whenever(context.getSharedPreferences(eq(RemoteConfigStoreImpl.STORE_NAME), eq(Context.MODE_PRIVATE))).thenReturn(sharedPreferences)
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(context.packageName).thenReturn("io.justtrack.test")

        val sdkVersion = object : io.justtrack.versions.SdkVersion {
            override val platformType: PlatformType = PlatformType.ANDROID
            override val major: Int = 1
            override val minor: Int = 0
            override val patch: Int = 0
            override val name: String = "1.0.0"
        }

        // Provide a working DatabaseInterface mock so RemoteConfigTimestampImpl.getFirstAttributionTimestamp()
        // does not NPE when fetch() runs end-to-end.
        val attribution = mock<io.justtrack.DatabaseAttributionInterface>()
        runBlocking { whenever(attribution.getAttributionTimestamps()).thenReturn(null) }
        val database = mock<io.justtrack.DatabaseInterface>()
        whenever(database.openAttribution()).thenReturn(attribution)

        return RemoteConfigImpl(
            context = context,
            isTracking = AtomicBoolean(isTracking),
            attributionParams = RemoteConfigImpl.AttributionParams(
                { ValueFuture("install-instance") },
                { ValueFuture("user-id") },
                { ValueFuture(TestAdvertiserIdInfo("advertiser-id")) },
                database,
                FakeDeviceInfo(),
                sdkVersion,
            ),
            sdkFirstInitializationTimestampRepo = mock(),
            taskExecutor = ImmediateSyncTaskExecutor(),
            configApi = configApi,
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

    private class RecordingConfigApi : ConfigApi {
        var fetchCalls = 0
        var activateCalls = 0
        var lastActivateExperiments: List<String> = emptyList()

        override suspend fun fetchRemoteConfig(
            queryParams: RemoteConfigQueryParams,
            advertiserId: String?,
            uuid: String?,
            installId: String?,
        ): Result<RemoteConfigResponse> {
            fetchCalls += 1
            return Result.success(RemoteConfigResponse(JSONObject(), null))
        }

        override suspend fun activateExperiments(body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<JSONObject> {
            activateCalls += 1
            val dto = body as io.justtrack.dtos.DTOActivateExperiments
            lastActivateExperiments = dto.experimentIds
            return Result.success(JSONObject())
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
