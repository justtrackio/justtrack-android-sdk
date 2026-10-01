package io.justtrack.publicInterface

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import io.justtrack.IntegrityTokenProvider
import io.justtrack.JustTrackSdk
import io.justtrack.JustTrackSdkBuilder
import io.justtrack.PlatformType
import io.justtrack.TestLoggerImpl
import io.justtrack.database.Database.Companion.clearForTesting
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.integrity.IntegrityToken
import io.justtrack.integrity.StandardTokenProviderTask
import io.justtrack.mockGoogleTokenProvider
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutionException

@RunWith(AndroidJUnit4::class)
class SdkTest {
    private var sdkInstance: JustTrackSdk? = null
    private val sdk: JustTrackSdk
        get() {
            val instance = sdkInstance
            if (instance != null) {
                return instance
            }

            throw AssertionError("sdk instance must not be null")
        }
    private val logger = TestLoggerImpl()

    @Before
    @Throws(InvalidFieldException::class)
    fun createSdk() {
        Assert.assertNull(sdkInstance)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        clearForTesting(context)

        // Mocking StandardIntegrityTokenProvider is required for running tests on an emulator without Google Play.
        val mockStandardTokenProvider = mock<StandardIntegrityTokenProvider>()
        val standardTokenProviderTask = StandardTokenProviderTask(tokenResult = IntegrityToken(UUID.randomUUID().toString()))
        whenever(mockStandardTokenProvider.request(any())).then {
            standardTokenProviderTask
        }
        IntegrityTokenProvider.mockGoogleTokenProvider(mockStandardTokenProvider)

        var builder: JustTrackSdkBuilder =
            JustTrackSdkBuilder((context.applicationContext as Application), API_TOKEN)
        builder = builder
            .setLogger(logger)
            .setReAttributionTimeFrame(14)
            .setInactivityTimeFrame(48)
            .setReFetchReAttributionDelaySeconds(10)
            .setAttributionRetryDelaySeconds(120)
            .setPlatformType(PlatformType.UNITY)
            .setFirebaseAppInstanceId("firebase app instance id")
            .setTrackingId("", "advertiserId")
            .setManualStart(true)
            .setLoggingEnabled(true)
        val sdk = builder.build()
        sdk.start()

        sdkInstance = sdk
    }

    @After
    fun destroySdk() {
        logger.assertHasError(false)
        sdk.shutdown()
        sdkInstance = null
    }

    @Test(timeout = 10_000)
    @Throws(Exception::class)
    fun validInstallInstanceId() {
        val installInstanceId = sdk.installInstanceId.get()

        // user id needs to be a lower-case UUID
        Assert.assertEquals(installInstanceId.lowercase(Locale.getDefault()), installInstanceId)
        Assert.assertEquals(36, installInstanceId.length)
        Assert.assertEquals(installInstanceId, UUID.fromString(installInstanceId).toString())
    }

    @Test(timeout = 10_000)
    fun checkVersion() {
        val sdkVersion = sdk.sdkVersion
        Assert.assertTrue(sdkVersion.major >= 4)
        Assert.assertTrue(sdkVersion.minor in 0..99)
    }

    @Test(timeout = 10_000)
    @Throws(ExecutionException::class, InterruptedException::class)
    fun testGetAdvertiserIdInfo() {
        val info = sdk.advertiserIdInfo.get()
        Assert.assertNotNull(info.advertiserId)
        Assert.assertEquals(info.advertiserId, UUID.fromString(info.advertiserId).toString())
        Assert.assertFalse(info.isLimitedAdTracking)
    }

    companion object {
        const val API_TOKEN =
            "mocked-api-token-value"
    }
}
