package io.justtrack

import android.content.Context

internal interface HttpClientBuilder {
    fun build(context: Context, apiToken: String, environment: Environment, platformType: PlatformType): HttpClient
}
