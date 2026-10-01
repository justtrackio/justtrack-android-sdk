package io.justtrack.util

import android.content.Context
import android.os.Build
import io.justtrack.DeviceInfo
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder

internal class InstallerSourceIdProviderImpl(
    private val context: Context,
    private val deviceInfo: DeviceInfo,
    private val logger: Logger,
) : InstallerSourceIdProvider {
    override fun getInstallerSourceId(): String {
        var installer: String?
        try {
            val packageName = deviceInfo.getApplicationPackageName()
            val packageManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sourceInfo = packageManager.getInstallSourceInfo(packageName)
                installer = sourceInfo.installingPackageName
                if (installer == null) {
                    installer = sourceInfo.initiatingPackageName
                }
                logger.debug(
                    "Retrieved installer source data",
                    LoggerFieldsBuilder()
                        .with("installer", sourceInfo.installingPackageName ?: "unknown")
                        .with("initiator", sourceInfo.initiatingPackageName ?: "unknown"),
                )
            } else {
                @Suppress("DEPRECATION")
                installer = context.packageManager.getInstallerPackageName(packageName)
            }
        } catch (e: Throwable) {
            logger.warn("Failed to lookup installer package name", e)
            return "unknown"
        }
        return installer ?: "unknown"
    }
}
