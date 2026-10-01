package io.justtrack.config

import android.content.Context
import android.content.pm.PackageManager
import io.justtrack.AttributionTimestamps
import io.justtrack.DatabaseAttributionInterface
import io.justtrack.DatabaseInterface
import io.justtrack.SdkFirstInitializationTimestampRepo
import io.justtrack.log.Logger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
internal class RemoteConfigTimestampImplTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val database: DatabaseInterface = mock()
    private val attribution: DatabaseAttributionInterface = mock()
    private val firstInitRepo: SdkFirstInitializationTimestampRepo = mock()
    private val logger: Logger = mock()

    private val subject get() = RemoteConfigTimestampImpl(context, database, firstInitRepo, logger)

    // region getCurrentTimestamp

    @Test
    fun getCurrentTimestamp_returnsCurrentTimeInSeconds() {
        val before = System.currentTimeMillis() / 1000
        val result = subject.getCurrentTimestamp()
        val after = System.currentTimeMillis() / 1000

        assertTrue("Expected $result between $before and $after", result in before..after)
    }

    // endregion

    // region getFirstAttributionTimestamp

    @Test
    fun getFirstAttributionTimestamp_returnsTimestampInSecondsWhenPresent() = runBlocking {
        whenever(database.openAttribution()).thenReturn(attribution)
        val timestamps = AttributionTimestamps(
            5_000L,
            6_000L,
            7_000L,
        )
        whenever(attribution.getAttributionTimestamps()).thenReturn(timestamps)

        val result = subject.getFirstAttributionTimestamp()

        assertEquals(5L, result)
        verify(attribution).close()
    }

    @Test
    fun getFirstAttributionTimestamp_returnsNullWhenAbsent() = runBlocking {
        whenever(database.openAttribution()).thenReturn(attribution)
        whenever(attribution.getAttributionTimestamps()).thenReturn(null)

        val result = subject.getFirstAttributionTimestamp()

        assertNull(result)
        verify(attribution).close()
    }

    // endregion

    // region getFirstInitializedAtTimestamp

    @Test
    fun getFirstInitializedAtTimestamp_delegatesToRepoConvertingMillisToSeconds() {
        whenever(firstInitRepo.getOrCreate()).thenReturn(15_500L)

        assertEquals(15L, subject.getFirstInitializedAtTimestamp())
    }

    // endregion

    // region getInstalledAtTimestamp

    @Test
    fun getInstalledAtTimestamp_returnsFirstInstallTimeInSeconds() {
        // Robolectric provides a fake PackageManager that returns 0 for firstInstallTime.
        val result = subject.getInstalledAtTimestamp()

        // Should be non-null and equal to firstInstallTime / 1000.
        assertEquals(0L, result)
    }

    @Test
    fun getInstalledAtTimestamp_returnsNullAndLogsWhenPackageNotFound() {
        val packageManager: PackageManager = mock()
        whenever(packageManager.getPackageInfo(any<String>(), eq(0)))
            .thenThrow(PackageManager.NameNotFoundException("missing"))
        val spyContext = object : android.content.ContextWrapper(context) {
            override fun getPackageManager(): PackageManager = packageManager
            override fun getPackageName(): String = "io.justtrack.test"
        }

        val result = RemoteConfigTimestampImpl(spyContext, database, firstInitRepo, logger).getInstalledAtTimestamp()

        assertNull(result)
        val messageCaptor = argumentCaptor<String>()
        verify(logger).warn(messageCaptor.capture(), any<Throwable>())
        assertTrue(messageCaptor.firstValue.contains("installedAt"))
    }

    // endregion
}
