package io.justtrack.api

import io.justtrack.okhttp.Headers
import io.justtrack.okhttp.HttpUrl.Companion.toHttpUrlOrNull
import io.justtrack.okhttp.Request
import io.justtrack.okhttp.MediaType.Companion.toMediaType
import io.justtrack.okhttp.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

internal fun createPostRequest(url: String, headers: Headers, bodyJson: JSONObject?): Request {
    return Request.Builder().apply {
        this.url(url)
        this.headers(headers)

        val responseBody = bodyJson?.toString()?.toRequestBody(JSON_MEDIA_TYPE) ?: ByteArray(0).toRequestBody(JSON_MEDIA_TYPE)
        this.post(responseBody)
    }.build()
}

internal fun createGetRequest(url: String, headers: Headers, params: Map<String, String> = emptyMap()): Request {
    val baseUrl = url.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Invalid url: $url")
    val requestUrl = if (params.isEmpty()) {
        baseUrl
    } else {
        val builder = baseUrl.newBuilder()
        for ((key, value) in params) {
            builder.addQueryParameter(key, value)
        }
        builder.build()
    }

    return Request.Builder().apply {
        this.url(requestUrl)
        this.headers(headers)
        this.get()
    }.build()
}
