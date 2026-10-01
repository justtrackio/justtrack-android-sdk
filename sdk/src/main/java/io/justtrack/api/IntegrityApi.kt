package io.justtrack.api

import io.justtrack.Environment
import io.justtrack.Formatter
import io.justtrack.HttpClient
import io.justtrack.JSONEncodable
import io.justtrack.log.Logger
import org.json.JSONObject

internal class IntegrityApiImpl(
    private val httpClient: HttpClient,
    private val headerProvider: HeaderProvider,
    private val environment: Environment,
    private val logger: Logger,
) : IntegrityApi {
    override suspend fun reportIntegrity(body: JSONEncodable, installId: String): Result<JSONObject> = runCatching {
        val jsonBody: JSONObject = body.toJSON(Formatter)

        val request = createPostRequest(
            environment.getUrl(Environment.Route.REPORT_INTEGRITY),
            headerProvider.provideHeader(null, null, installId, logger),
            jsonBody,
        )

        httpClient.executeAsyncRequest(
            request,
            SEND_INTEGRITY_TOKEN_NAME,
            logger,
        ).getOrThrow()
    }

    internal companion object {
        const val SEND_INTEGRITY_TOKEN_NAME = "SendIntegrityToken"
    }
}

internal interface IntegrityApi {
    suspend fun reportIntegrity(body: JSONEncodable, installId: String): Result<JSONObject>
}
