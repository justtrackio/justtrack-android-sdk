package io.justtrack.config

import io.justtrack.okhttp.Headers
import org.json.JSONObject

internal data class RemoteConfigResponse(
    val body: JSONObject,
    val headers: Headers?,
    val retryAfterSeconds: Int? = parseRetryAfterSeconds(headers),
)

private fun parseRetryAfterSeconds(headers: Headers?): Int? {
    return if (headers == null) {
        null
    } else {
        val retryAfterSeconds = headers["Retry-After"]?.toIntOrNull()
        return retryAfterSeconds?.takeIf { it >= 0 }
    }
}
