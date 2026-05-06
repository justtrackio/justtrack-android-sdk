package io.justtrack.config

import android.content.Context
import android.content.pm.PackageManager
import io.justtrack.DatabaseInterface
import io.justtrack.SdkFirstInitializationTimestampRepo
import io.justtrack.log.Logger

internal class RemoteConfigTimestampImpl internal constructor(
    private val context: Context,
    private val db: DatabaseInterface,
    private val sdkFirstInitializationTimestampRepo: SdkFirstInitializationTimestampRepo,
    private val logger: Logger,
) : RemoteConfigTimestamp {
    override fun getCurrentTimestamp(): Long {
        return (System.currentTimeMillis() / MILLIS_PER_SECOND)
    }

    override suspend fun getFirstAttributionTimestamp(): Long? {
        db.openAttribution().use {
            val attributionTimestamp = it.getAttributionTimestamps()
            return if (attributionTimestamp != null) {
                attributionTimestamp.getFirstAttributionAt() / MILLIS_PER_SECOND
            } else {
                null
            }
        }
    }

    override fun getFirstInitializedAtTimestamp(): Long {
        return sdkFirstInitializationTimestampRepo.getOrCreate() / MILLIS_PER_SECOND
    }

    override fun getInstalledAtTimestamp(): Long? {
        return try {
            val packageInfo = context.packageManager
                .getPackageInfo(context.packageName, 0)

            packageInfo.firstInstallTime / MILLIS_PER_SECOND
        } catch (exception: PackageManager.NameNotFoundException) {
            logger.warn("Unable to get installedAt timestamp", exception)
            null
        }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
    }
}
