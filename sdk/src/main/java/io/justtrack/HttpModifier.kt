package io.justtrack

internal interface HttpModifier {
    fun modifyRequest(url: String, headers: Map<String, String>, body: String?): ModifiedRequest {
        return ModifiedRequest(url, headers, body)
    }

    fun modifyResponse(url: String, body: String?, code: Int?, message: String?): ModifiedResponse {
        return ModifiedResponse(code, message, body)
    }

    data class ModifiedRequest(val url: String, val headers: Map<String, String>, val body: String?)
    data class ModifiedResponse(val code: Int? = null, val message: String?, val body: String?)
}
