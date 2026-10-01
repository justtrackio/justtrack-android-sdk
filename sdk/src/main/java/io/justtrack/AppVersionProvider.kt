package io.justtrack

import io.justtrack.executor.TaskExecutor
import java.util.concurrent.Future

/**
 * Class to encapsulate getting an [AppVersionUpdateInfo] [Future]. Allows us to easily lock
 * this class without having to fear that we deadlock anything (we used to lock the [BaseJustTrackSdk]
 * for this - this lead to a problem when you needed to lock the SDK to process events, but another
 * thread had the SDK locked while blocking on publishing a new event as the blocking queue was full).
 */
internal class AppVersionProvider internal constructor(
    val taskExecutor: TaskExecutor,
    val currentVersion: ApplicationVersion,
    val databaseInterface: DatabaseInterface,
) {
    private var appVersionUpdateInfo: AsyncFuture<AppVersionUpdateInfo?>? = null

    // Deadlock-Safety: executeAsFuture is not locking anything (besides the executor maybe).
    @Synchronized
    @JvmName("providAppVersionUpdateInfo")
    internal fun provideAppVersionUpdateInfo(): AsyncFuture<AppVersionUpdateInfo?> {
        val currentAppVersionInfo = appVersionUpdateInfo

        return if (currentAppVersionInfo == null) {
            val result = taskExecutor.executeFuture(
                GetAppVersionUpdateInfoTask(
                    databaseInterface,
                    currentVersion,
                ),
            )
            appVersionUpdateInfo = result
            result
        } else {
            currentAppVersionInfo
        }
    }

    @JvmName("getApplicationVersionAtInstalled")
    internal fun getApplicationVersionAtInstalled(): AsyncFuture<ApplicationVersion?> {
        val task = object : Task<ApplicationVersion?> {
            override suspend fun execute(): ApplicationVersion? {
                val appVersionInfo = provideAppVersionUpdateInfo()
                val lastVersion = appVersionInfo.await() ?: return null
                return lastVersion.appLastVersion
            }
        }
        return taskExecutor.executeFuture(task)
    }

    internal class GetAppVersionUpdateInfoTask(
        private val databaseInterface: DatabaseInterface,
        private val currentVersion: ApplicationVersion,
    ) : Task<AppVersionUpdateInfo?> {
        override suspend fun execute(): AppVersionUpdateInfo? {
            databaseInterface.openAttribution().use {
                return it.getAppVersionUpdateInfo(currentVersion)
            }
        }
    }
}
