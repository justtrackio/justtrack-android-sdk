package io.justtrack

import io.justtrack.api.stripScheme

internal class Environment(
    val serverUrl: String = PROD_SERVER_URL,
) {

    private val baseUrl = stripScheme(serverUrl)

    fun getUrl(route: Route): String = "https://${route.domain}.${baseUrl}${route.path}"

    internal enum class Route(
        val path: String,
        val domain: String,
    ) {
        ATTRIBUTION("/v4/attribute", "attribution"),
        TRACK_EVENT("/v4/track", "sdk-api"),
        PUBLISH_CUSTOM_USER_ID("/v0/customUserId/publish", "sdk-api"),
        PUBLISH_FIREBASE_APP_INSTANCE_ID("/v0/firebase/instanceId/publish", "sdk-api"),
        LOG("/v2/log", "justtrack-logs"),
        SIGN_IP_V4("/v0/sign", "ipv4"),
        SIGN_IP_V6("/v0/sign", "ipv6"),
        REPORT_INTEGRITY("/v0/integrity", "fraud-detector"),
        ANONYMIZE("/v1/anonymize", "privacy"),
        AB_TEST_ASSIGNMENT("/ab-test/v0/assignment", "sdk-api"),
        REMOTE_CONFIG("/ab-test/v0/assignments", "sdk-api"),
    }

    companion object {
        const val PROD_SERVER_URL = "https://justtrack.io"
    }
}
