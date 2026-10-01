package io.justtrack

import android.content.Intent
import io.justtrack.versions.ApplicationVersionImpl
import io.justtrack.versions.SdkVersion
import io.justtrack.versions.SdkVersionImpl
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

internal data class WorkerInputData(
    val apiToken: String,
    val env: Environment,
    val advertiseId: String,
    val trackingId: String,
    val trackingProvider: String,
    val userId: UUID,
    val installId: String,
    val installInstanceId: String,
    val sdkVersion: SdkVersion,
    val isLogEnabled: Boolean,
    val applicationPackageName: String,
    val applicationVersion: ApplicationVersion,
) {
    constructor(intent: Intent) : this(
        intent.getStringExtra(PARAM_API_TOKEN) ?: "",
        Environment(intent.getStringExtra(PARAM_SERVER_URL) ?: Environment.PROD_SERVER_URL),
        intent.getStringExtra(PARAM_ADVERTISER_ID) ?: "",
        intent.getStringExtra(PARAM_TRACKING_ID) ?: "",
        intent.getStringExtra(PARAM_TRACKING_PROVIDER) ?: "",
        UUID.fromString(intent.getStringExtra(PARAM_USER_ID) ?: ""),
        intent.getStringExtra(PARAM_INSTALL_ID) ?: "",
        intent.getStringExtra(PARAM_INSTALL_INSTANCE_ID) ?: "",
        sdkVersionFromIntent(intent),
        intent.getBooleanExtra(PARAM_IS_LOG_ENABLED, false),
        intent.getStringExtra(PARAM_APPLICATION_PACKAGE_NAME) ?: "",
        ApplicationVersionImpl(
            intent.getStringExtra(PARAM_APPLICATION_VERSION_NAME) ?: "",
            intent.getStringExtra(PARAM_APPLICATION_VERSION_CODE) ?: "",
        ),
    )

    fun appendDataToIntent(intent: Intent) {
        intent.putExtra(PARAM_SERVER_URL, env.serverUrl)
        intent.putExtra(PARAM_API_TOKEN, apiToken)
        intent.putExtra(PARAM_ADVERTISER_ID, advertiseId)
        intent.putExtra(PARAM_TRACKING_ID, trackingId)
        intent.putExtra(PARAM_TRACKING_PROVIDER, trackingProvider)
        intent.putExtra(PARAM_USER_ID, userId.toString())
        intent.putExtra(PARAM_INSTALL_ID, installId)
        intent.putExtra(PARAM_INSTALL_INSTANCE_ID, installInstanceId)
        intent.putExtra(PARAM_SDK_VERSION, sdkVersionToJson(sdkVersion))
        intent.putExtra(PARAM_IS_LOG_ENABLED, isLogEnabled)
        intent.putExtra(PARAM_APPLICATION_PACKAGE_NAME, applicationPackageName)
        intent.putExtra(PARAM_APPLICATION_VERSION_NAME, applicationVersion.getVersionName())
        intent.putExtra(PARAM_APPLICATION_VERSION_CODE, applicationVersion.getVersionCode())
    }

    internal companion object {
        internal const val PARAM_API_TOKEN = "PARAM_API_TOKEN"
        internal const val PARAM_SERVER_URL = "PARAM_SERVER_URL"
        internal const val PARAM_ADVERTISER_ID = "PARAM_ADVERTISER_ID"
        internal const val PARAM_TRACKING_ID = "PARAM_TRACKING_ID"
        internal const val PARAM_TRACKING_PROVIDER = "PARAM_TRACKING_PROVIDER"
        internal const val PARAM_USER_ID = "PARAM_USER_ID"
        internal const val PARAM_INSTALL_ID = "PARAM_INSTALL_ID"
        internal const val PARAM_INSTALL_INSTANCE_ID = "PARAM_INSTALL_INSTANCE_ID"
        internal const val PARAM_SDK_VERSION = "PARAM_SDK_VERSION"
        internal const val PARAM_IS_LOG_ENABLED = "PARAM_IS_LOG_ENABLED"
        internal const val PARAM_APPLICATION_PACKAGE_NAME = "PARAM_APPLICATION_PACKAGE_NAME"
        internal const val PARAM_APPLICATION_VERSION_NAME = "PARAM_APPLICATION_VERSION_NAME"
        internal const val PARAM_APPLICATION_VERSION_CODE = "PARAM_APPLICATION_VERSION_CODE"

        private fun sdkVersionToJson(version: SdkVersion): String {
            return JSONObject().apply {
                put("major", version.major)
                put("minor", version.minor)
                put("patch", version.patch)
                put("name", version.name)
                put("platformType", version.platformType.toString())
            }.toString()
        }

        private fun sdkVersionFromIntent(intent: Intent): SdkVersion {
            val sdkVersionJson = intent.getStringExtra(PARAM_SDK_VERSION)
            if (!sdkVersionJson.isNullOrEmpty()) {
                try {
                    val json = JSONObject(sdkVersionJson)
                    val platformType = json.optString("platformType").takeIf { it.isNotEmpty() }
                        ?.let { PlatformType.fromString(it) }
                        ?: platformTypeFromValues(
                            platform = json.optString("platform").ifEmpty { null },
                            wrapper = json.optString("wrapper").ifEmpty { null },
                        )

                    return SdkVersionImpl(
                        major = json.getInt("major"),
                        minor = json.getInt("minor"),
                        patch = json.getInt("patch"),
                        name = json.getString("name"),
                        platformType = platformType,
                    )
                } catch (_: JSONException) {
                    // fall back to defaults below
                }
            }

            val platformType = PlatformType.fromString(
                intent.getStringExtra(PARAM_PLATFORM_TYPE) ?: PlatformType.ANDROID.toString(),
            )
            return SdkVersionImpl(
                BuildConfig.VERSION_MAJOR,
                BuildConfig.VERSION_MINOR,
                BuildConfig.VERSION_PATCH,
                BuildConfig.VERSION_NAME,
                platformType,
            )
        }

        private fun platformTypeFromValues(platform: String?, wrapper: String?): PlatformType {
            return PlatformType.entries.firstOrNull {
                it.platform == platform && it.wrapper == wrapper
            } ?: PlatformType.ANDROID
        }

        private const val PARAM_PLATFORM_TYPE = "PARAM_PLATFORM_TYPE"
    }
}
