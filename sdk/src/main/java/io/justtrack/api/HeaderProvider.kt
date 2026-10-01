package io.justtrack.api

import android.net.Uri
import io.justtrack.DeviceInfo
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.okhttp.Headers
import io.justtrack.versions.VersionBundle
import java.util.Locale

internal class HeaderProviderImpl constructor(
    private val apiToken: String,
    private val applicationPackageName: String,
    private val deviceInfo: DeviceInfo,
    private val versionBundle: VersionBundle,
) : HeaderProvider {

    private var userAgent: String? = null

    override fun provideHeader(advertiserId: String?, uuid: String?, installId: String?, logger: Logger): Headers {
        return Headers.Builder().apply {
            this.add("X-CLIENT-ID", applicationPackageName)
            this.add("X-CLIENT-TOKEN", apiToken)
            this.add("X-ADVERTISER-ID", advertiserId ?: "missing")

            // Accept-Encoding is automatically added by default. Do not add them manually. Content-Encoding also added in interceptor.

            if (uuid != null) {
                this.add("X-USER-ID", uuid)
            }
            if (installId != null) {
                this.add("X-INSTALL-ID", installId)
            }
            this.add("User-Agent", getUserAgent(logger))
        }.build()
    }

    override fun provideHeaderV2(advertiserId: String?, uuid: String?, installId: String?, logger: Logger): Headers {
        return Headers.Builder().apply {
            this.add("X-APP-BUNDLE-ID", applicationPackageName)
            this.add("X-APP-TOKEN", apiToken)
            this.add("X-ADVERTISER-ID", advertiserId ?: "missing")

            // Accept-Encoding is automatically added by default. Do not add them manually. Content-Encoding also added in interceptor.

            if (uuid != null) {
                this.add("X-USER-ID", uuid)
            }
            if (installId != null) {
                this.add("X-INSTALL-ID", installId)
            }
            this.add("User-Agent", getUserAgent(logger))
        }.build()
    }

    // Deadlock-Safety: This doesn't take any additional locks and just updates the userAgent string.
    @Synchronized
    private fun getUserAgent(logger: Logger): String {
        userAgent?.let {
            return it
        }

        val sdkVersionName = versionBundle.sdkVersion.name
        val product = deviceInfo.deviceProduct
        val device = escapeField(deviceInfo.deviceModel)
        val cpu = deviceInfo.cpuArch
        val osVersion = deviceInfo.osVersion
        val locale = Locale.getDefault().toString()
        val build = deviceInfo.build
        val appName = deviceInfo.getAppName()?.let { escapeField(it) }

        val appVersionName = escapeField(
            versionBundle.applicationVersion.getVersionName().ifEmpty {
                versionBundle.applicationVersion.getVersionCode()
            },
        )

        val platformType = versionBundle.sdkVersion.platformType.toString()
        // be careful with the format - the backend parses this to extract some information
        val userAgent = "JustTrackSDK/" + sdkVersionName +
            " (" + product + "; " + device + "; " + (if (cpu != null) "$cpu CPU; " else "") +
            "Android " + osVersion + "; " + locale + "; Build/" + build + ") " +
            (if (appName != null) "$appName/$appVersionName ($platformType)" else "")

        this.userAgent = userAgent

        logger.debug(
            "Initialized user agent",
            LoggerFieldsBuilder().with(
                "userAgent",
                userAgent,
            ),
        )
        return userAgent
    }

    /**
     * Percent-escapes a single user agent field so the resulting header stays valid and parseable.
     *
     * The space is explicitly allowed through, so values that are already plain ASCII stay byte for
     * byte identical (`Pixel 7` stays `Pixel 7`) and the header remains readable.
     */
    private fun escapeField(value: String): String = Uri.encode(value, " ")
}

internal interface HeaderProvider {
    fun provideHeader(advertiserId: String?, uuid: String?, installId: String?, logger: Logger): Headers
    fun provideHeaderV2(advertiserId: String?, uuid: String?, installId: String?, logger: Logger): Headers
}
