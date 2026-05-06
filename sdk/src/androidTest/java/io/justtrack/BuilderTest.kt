package io.justtrack

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.database.Database.Companion.clearForTesting
import io.justtrack.publicInterface.SdkTest.Companion.API_TOKEN
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.MalformedURLException

@RunWith(AndroidJUnit4::class)
class BuilderTest {

    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val application
        get() = (ctx.applicationContext as Application)

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
}
