package io.justtrack

import io.justtrack.versions.ApplicationVersionImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class AppVersionProviderTest {
    @Test
    fun `reuses future and exposes nullable installed version`() {
        val executor = ImmediateSyncTaskExecutor()
        val currentVersion = ApplicationVersionImpl("2.0", "20")
        val appLastVersion = ApplicationVersionImpl("1.0", "10")
        val databaseInterface: DatabaseInterface = mock()
        val attributionInterface = appVersionAttributionInterface(
            AppVersionUpdateInfo(currentVersion, appLastVersion, AppVersionUpdateKind.UPDATED_APP),
        )
        whenever(databaseInterface.openAttribution()).thenReturn(attributionInterface)
        val provider = AppVersionProvider(executor, currentVersion, databaseInterface)

        val first = provider.provideAppVersionUpdateInfo()
        val second = provider.provideAppVersionUpdateInfo()

        assertSame(first, second)
        assertSame(executor, provider.taskExecutor)
        assertSame(currentVersion, provider.currentVersion)
        assertSame(databaseInterface, provider.databaseInterface)
        assertEquals(appLastVersion, provider.getApplicationVersionAtInstalled().get())
        verify(attributionInterface).close()
        assertEquals(2, executor.executeCount.get())
    }

    @Test
    fun `returns null installed version when update info is absent`() {
        val databaseInterface: DatabaseInterface = mock()
        val attributionInterface = appVersionAttributionInterface(null)
        whenever(databaseInterface.openAttribution()).thenReturn(attributionInterface)
        val provider = AppVersionProvider(
            ImmediateSyncTaskExecutor(),
            ApplicationVersionImpl("2.0", "20"),
            databaseInterface,
        )

        assertNull(provider.getApplicationVersionAtInstalled().get())
    }

    private fun appVersionAttributionInterface(appVersionUpdateInfo: AppVersionUpdateInfo?): DatabaseAttributionInterface {
        return mock<DatabaseAttributionInterface>().also { attributionInterface ->
            runBlocking {
                whenever(attributionInterface.getAppVersionUpdateInfo(any())).thenReturn(appVersionUpdateInfo)
            }
        }
    }
}
