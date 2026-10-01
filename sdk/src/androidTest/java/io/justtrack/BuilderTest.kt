package io.justtrack

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.database.Database.Companion.clearForTesting
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.log.Logger
import io.justtrack.publicInterface.SdkTest.Companion.API_TOKEN
import io.justtrack.versions.ApplicationVersionImpl
import org.junit.Assert
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import java.net.MalformedURLException

@Suppress("TooManyFunctions")
@RunWith(AndroidJUnit4::class)
class BuilderTest {

    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val application
        get() = (ctx.applicationContext as Application)

    private val intent = Intent("test-action")
    private val logger: Logger = TestLoggerImpl()
    private val integrationAdapter: IntegrationAdapter = mock()

    @Before
    fun setUp() {
        clearForTesting(ctx)
        DatabaseInterface.clearForTesting()
    }

    @Test
    fun testMalformedUrlThrowsException() {
        assertThrows(MalformedURLException::class.java) {
            JustTrackSdkBuilder(application, API_TOKEN)
                .setServerUrl("malformed url")
                .build()
        }
    }

    @Test
    fun testProperlyFormattedUrlPasses() {
        JustTrackSdkBuilder(application, API_TOKEN)
            .setServerUrl("https://proper.url")
            .build()
    }

    @Test(timeout = 10_000)
    fun testIfBuilderReturnsCachedResult() {
        val app = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application)

        val sdk1 = JustTrackSdkBuilder(app, API_TOKEN)
            .setReAttributionTimeFrame(14)
            .setInactivityTimeFrame(48)
            .setReFetchReAttributionDelaySeconds(10)
            .setAttributionRetryDelaySeconds(120)
            .setPlatformType(PlatformType.UNITY)
            .setFirebaseAppInstanceId("firebase app instance id")
            .setTrackingId("", "advertiserId")
            .setManualStart(true)
            .setLoggingEnabled(true)
            .build()

        val sdk2 = JustTrackSdkBuilder(app, API_TOKEN).build()

        assertSame(sdk1, sdk2)
    }

    // region Constructor Tests

    @Test
    fun constructor_withApplicationAndToken_setsDefaults() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        Assert.assertEquals("test-token", builder.apiToken)
        Assert.assertEquals(application, builder.application)
        Assert.assertNull(builder.intent)
        Assert.assertFalse(builder.isManualStart)
        Assert.assertTrue(builder.isEnableUncaughtExceptionHandler)
        Assert.assertFalse(builder.isLogEnabled)
        Assert.assertFalse(builder.runCallbacksSerially)
        Assert.assertEquals(0L, builder.attributionRetryDelaySeconds)
        Assert.assertEquals("https://justtrack.io", builder.environment.serverUrl)
        Assert.assertEquals(PlatformType.ANDROID, builder.sdkVersion.platformType)
        Assert.assertTrue(builder.integrationAdapters.isEmpty())
        Assert.assertNull(builder.installReferrerDetailBundle)
    }

    @Test
    fun constructor_withApplicationIntentAndToken_storesIntent() {
        val builder = JustTrackSdkBuilder(application, intent, "test-token")

        Assert.assertEquals(application, builder.application)
        Assert.assertEquals(intent, builder.intent)
        Assert.assertEquals("test-token", builder.apiToken)
    }

    // endregion

    // region API Token Tests

    @Test
    fun apiToken_stripsSandboxPrefix() {
        val builder = JustTrackSdkBuilder(application, "sandbox-abc123")

        Assert.assertEquals("abc123", builder.apiToken)
    }

    @Test
    fun apiToken_stripsProdPrefix() {
        val builder = JustTrackSdkBuilder(application, "prod-abc123")

        Assert.assertEquals("abc123", builder.apiToken)
    }

    @Test
    fun apiToken_keepsPlainToken() {
        val builder = JustTrackSdkBuilder(application, "plain-token")

        Assert.assertEquals("plain-token", builder.apiToken)
    }

    @Test
    fun apiToken_stripsBothPrefixes() {
        val builder = JustTrackSdkBuilder(application, "sandbox-prod-abc123")

        Assert.assertEquals("abc123", builder.apiToken)
    }

    // endregion

    // region Setter Tests

    @Test
    fun setPackageName_updatesPackageName() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setPackageName("com.example.app")

        Assert.assertSame(builder, result)
        Assert.assertEquals("com.example.app", builder.packageName)
    }

    @Test
    fun setApplicationVersion_updatesApplicationVersion() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setApplicationVersion("1.2.3", "123")

        Assert.assertSame(builder, result)
        val version = builder.applicationVersion
        Assert.assertEquals("1.2.3", version.getVersionName())
        Assert.assertEquals("123", version.getVersionCode())
    }

    @Test
    fun setManualStart_updatesIsManualStart() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setManualStart(true)

        Assert.assertSame(builder, result)
        Assert.assertTrue(builder.isManualStart)
    }

    @Test
    fun setLogger_updatesCustomLogger() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setLogger(logger)

        Assert.assertSame(builder, result)
        Assert.assertNotNull(builder.logger.value)
    }

    @Test
    fun setTrackingId_withValidValues_updatesConfig() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setTrackingId("tracking-id", "provider")

        Assert.assertSame(builder, result)
        val config = builder.startConfigBuilder.build()
        Assert.assertEquals("tracking-id", config.trackingId)
        Assert.assertEquals("provider", config.trackingIdProvider)
    }

    @Test(expected = InvalidFieldException::class)
    fun setTrackingId_withTooLongTrackingId_throws() {
        val builder = JustTrackSdkBuilder(application, "test-token")
        val longTrackingId = "a".repeat(4096)

        builder.setTrackingId(longTrackingId, "provider")
    }

    @Test(expected = InvalidFieldException::class)
    fun setTrackingId_withNonAsciiTrackingId_throws() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        builder.setTrackingId("tracking\u0001id", "provider")
    }

    @Test(expected = InvalidFieldException::class)
    fun setTrackingId_withTooLongProvider_throws() {
        val builder = JustTrackSdkBuilder(application, "test-token")
        val longProvider = "a".repeat(4096)

        builder.setTrackingId("tracking-id", longProvider)
    }

    @Test(expected = InvalidFieldException::class)
    fun setTrackingId_withNonAsciiProvider_throws() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        builder.setTrackingId("tracking-id", "prov\u0001ider")
    }

    @Test
    fun setAutomaticInAppPurchaseTracking_updatesConfig() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setAutomaticInAppPurchaseTracking(false)

        Assert.assertSame(builder, result)
        val config = builder.startConfigBuilder.build()
        Assert.assertFalse(config.automaticIAPTracking)
    }

    @Test
    fun setUserId_withValidValue_updatesConfig() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setUserId("user-123")

        Assert.assertSame(builder, result)
        val config = builder.startConfigBuilder.build()
        Assert.assertEquals("user-123", config.userId)
    }

    @Test(expected = InvalidFieldException::class)
    fun setUserId_withEmptyString_throws() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        builder.setUserId("")
    }

    @Test(expected = InvalidFieldException::class)
    fun setUserId_withTooLongString_throws() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        builder.setUserId("a".repeat(4096))
    }

    @Test(expected = InvalidFieldException::class)
    fun setUserId_withNonAsciiString_throws() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        builder.setUserId("user\u0001id")
    }

    @Test
    fun setFirebaseAppInstanceId_withValidValue_updatesConfig() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setFirebaseAppInstanceId("firebase-id-123")

        Assert.assertSame(builder, result)
        val config = builder.startConfigBuilder.build()
        Assert.assertEquals("firebase-id-123", config.firebaseAppInstanceId)
    }

    @Test(expected = InvalidFieldException::class)
    fun setFirebaseAppInstanceId_withTooShortString_throws() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        builder.setFirebaseAppInstanceId("short")
    }

    @Test(expected = InvalidFieldException::class)
    fun setFirebaseAppInstanceId_withTooLongString_throws() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        builder.setFirebaseAppInstanceId("a".repeat(256))
    }

    @Test(expected = InvalidFieldException::class)
    fun setFirebaseAppInstanceId_withNonAsciiString_throws() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        builder.setFirebaseAppInstanceId("firebase\u0001id")
    }

    @Test
    fun setInactivityTimeFrame_updatesReAttributionConfig() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setInactivityTimeFrame(72)

        Assert.assertSame(builder, result)
        val decision = builder.reAttributionConfig.needsReAttribution(null)
        Assert.assertEquals(AttributionDecision.FETCH_FIRST_ATTRIBUTION, decision)
    }

    @Test
    fun setReAttributionTimeFrame_updatesReAttributionConfig() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setReAttributionTimeFrame(30)

        Assert.assertSame(builder, result)
        val decision = builder.reAttributionConfig.needsReAttribution(null)
        Assert.assertEquals(AttributionDecision.FETCH_FIRST_ATTRIBUTION, decision)
    }

    @Test
    fun setReFetchReAttributionDelaySeconds_updatesReAttributionConfig() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setReFetchReAttributionDelaySeconds(10)

        Assert.assertSame(builder, result)
        Assert.assertEquals(10L, builder.reAttributionConfig.reFetchReAttributionDelaySeconds)
    }

    @Test
    fun setAttributionRetryDelaySeconds_updatesProperty() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setAttributionRetryDelaySeconds(30)

        Assert.assertSame(builder, result)
        Assert.assertEquals(30L, builder.attributionRetryDelaySeconds)
    }

    @Test
    fun setInstallUncaughtExceptionHandler_updatesProperty() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setInstallUncaughtExceptionHandler(false)

        Assert.assertSame(builder, result)
        Assert.assertFalse(builder.isEnableUncaughtExceptionHandler)
    }

    @Test
    fun addIntegrationAdapters_addsToList() {
        val builder = JustTrackSdkBuilder(application, "test-token")
        val adapters = listOf(integrationAdapter)

        val result = builder.addIntegrationAdapters(adapters)

        Assert.assertSame(builder, result)
        Assert.assertEquals(1, builder.integrationAdapters.size)
        Assert.assertSame(integrationAdapter, builder.integrationAdapters[0])
    }

    @Test
    fun setLoggingEnabled_updatesProperty() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setLoggingEnabled(true)

        Assert.assertSame(builder, result)
        Assert.assertTrue(builder.isLogEnabled)
    }

    @Test
    fun setServerUrl_updatesEnvironment() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setServerUrl("https://custom.justtrack.io")

        Assert.assertSame(builder, result)
        Assert.assertEquals("https://custom.justtrack.io", builder.environment.serverUrl)
    }

    @Test
    fun setPlatformType_withNonNull_updatesSdkVersion() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setPlatformType(PlatformType.UNITY)

        Assert.assertSame(builder, result)
        Assert.assertEquals(PlatformType.UNITY, builder.sdkVersion.platformType)
    }

    @Test
    fun setPlatformType_withNull_defaultsToAndroid() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.setPlatformType(null)

        Assert.assertSame(builder, result)
        Assert.assertEquals(PlatformType.ANDROID, builder.sdkVersion.platformType)
    }

    @Test
    fun runCallbacksSerially_updatesProperty() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder.runCallbacksSerially()

        Assert.assertSame(builder, result)
        Assert.assertTrue(builder.runCallbacksSerially)
    }

    // endregion

    // region Chaining and Complex Tests

    @Test
    fun settersSupportChaining() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        val result = builder
            .setPackageName("com.example")
            .setApplicationVersion("1.0", "1")
            .setManualStart(true)
            .setLogger(logger)
            .setAutomaticInAppPurchaseTracking(false)
            .setInactivityTimeFrame(72)
            .setReAttributionTimeFrame(30)
            .setReFetchReAttributionDelaySeconds(10)
            .setAttributionRetryDelaySeconds(30)
            .setInstallUncaughtExceptionHandler(false)
            .setLoggingEnabled(true)
            .setServerUrl("https://custom.justtrack.io")
            .setPlatformType(PlatformType.REACT_NATIVE)
            .runCallbacksSerially()

        Assert.assertSame(builder, result)
        Assert.assertEquals("com.example", builder.packageName)
        Assert.assertEquals("1.0", builder.applicationVersion.getVersionName())
        Assert.assertEquals("1", builder.applicationVersion.getVersionCode())
        Assert.assertTrue(builder.isManualStart)
        Assert.assertFalse(builder.startConfigBuilder.build().automaticIAPTracking)
        Assert.assertEquals(10L, builder.reAttributionConfig.reFetchReAttributionDelaySeconds)
        Assert.assertEquals(30L, builder.attributionRetryDelaySeconds)
        Assert.assertFalse(builder.isEnableUncaughtExceptionHandler)
        Assert.assertTrue(builder.isLogEnabled)
        Assert.assertEquals("https://custom.justtrack.io", builder.environment.serverUrl)
        Assert.assertEquals(PlatformType.REACT_NATIVE, builder.sdkVersion.platformType)
        Assert.assertTrue(builder.runCallbacksSerially)
    }

    // endregion

    // region Default Value Tests

    @Test
    fun defaultApplicationVersion_comesFromDeviceInfo() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        Assert.assertNotNull(builder.applicationVersion)
    }

    @Test
    fun defaultPackageName_comesFromDeviceInfo() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        Assert.assertNotNull(builder.packageName)
    }

    @Test
    fun installReferrerDetailBundle_canBeSet() {
        val builder = JustTrackSdkBuilder(application, "test-token")
        val bundle = Bundle()
        bundle.putString("key", "value")

        builder.installReferrerDetailBundle = bundle

        Assert.assertSame(bundle, builder.installReferrerDetailBundle)
    }

    @Test
    fun sdkVersion_hasCorrectBuildConfigValues() {
        val builder = JustTrackSdkBuilder(application, "test-token")
        val version = builder.sdkVersion

        Assert.assertEquals(BuildConfig.VERSION_MAJOR, version.major)
        Assert.assertEquals(BuildConfig.VERSION_MINOR, version.minor)
        Assert.assertEquals(BuildConfig.VERSION_PATCH, version.patch)
        Assert.assertEquals(BuildConfig.VERSION_NAME, version.name)
        Assert.assertEquals(PlatformType.ANDROID, version.platformType)
    }

    @Test
    fun setApplicationVersion_createsApplicationVersionImpl() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        builder.setApplicationVersion("2.5.0", "250")

        val version = builder.applicationVersion
        Assert.assertTrue(version is ApplicationVersionImpl)
        Assert.assertEquals("2.5.0", version.getVersionName())
        Assert.assertEquals("250", version.getVersionCode())
    }

    @Test
    fun setPlatformType_preservesBuildConfigVersionNumbers() {
        val builder = JustTrackSdkBuilder(application, "test-token")
        val originalVersion = builder.sdkVersion

        builder.setPlatformType(PlatformType.FLUTTER)

        val newVersion = builder.sdkVersion
        Assert.assertEquals(originalVersion.major, newVersion.major)
        Assert.assertEquals(originalVersion.minor, newVersion.minor)
        Assert.assertEquals(originalVersion.patch, newVersion.patch)
        Assert.assertEquals(originalVersion.name, newVersion.name)
        Assert.assertEquals(PlatformType.FLUTTER, newVersion.platformType)
    }

    @Test
    fun addIntegrationAdapters_appendsToExistingList() {
        val builder = JustTrackSdkBuilder(application, "test-token")
        val adapter1: IntegrationAdapter = mock()
        val adapter2: IntegrationAdapter = mock()

        builder.addIntegrationAdapters(listOf(adapter1))
        builder.addIntegrationAdapters(listOf(adapter2))

        Assert.assertEquals(2, builder.integrationAdapters.size)
        Assert.assertSame(adapter1, builder.integrationAdapters[0])
        Assert.assertSame(adapter2, builder.integrationAdapters[1])
    }

    @Test
    fun reAttributionConfig_defaultValues() {
        val builder = JustTrackSdkBuilder(application, "test-token")
        val config = builder.reAttributionConfig

        // Default: 48h inactivity, 14 days re-attribution, 5s refetch delay
        Assert.assertEquals(5L, config.reFetchReAttributionDelaySeconds)
    }

    @Test
    fun environment_defaultIsProd() {
        val builder = JustTrackSdkBuilder(application, "test-token")

        Assert.assertEquals("https://justtrack.io", builder.environment.serverUrl)
    }

    @Test
    fun startConfigBuilder_defaultValues() {
        val builder = JustTrackSdkBuilder(application, "test-token")
        val config = builder.startConfigBuilder.build()

        Assert.assertNull(config.userId)
        Assert.assertNull(config.trackingId)
        Assert.assertEquals("advertiserId", config.trackingIdProvider)
        Assert.assertNull(config.firebaseAppInstanceId)
        Assert.assertTrue(config.automaticIAPTracking)
    }

    // endregion
}
