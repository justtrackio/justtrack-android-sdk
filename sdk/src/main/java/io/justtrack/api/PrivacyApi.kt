package io.justtrack.api

import io.justtrack.Environment
import io.justtrack.Formatter
import io.justtrack.HttpClient
import io.justtrack.JSONEncodable
import io.justtrack.log.Logger
import org.json.JSONObject

internal class PrivacyApiImpl(
    private val httpClient: HttpClient,
    private val headerProvider: HeaderProvider,
    private val environment: Environment,
    private val logger: Logger,
) : PrivacyApi {
    override suspend fun anonymizeUser(advertiserId: String?, uuid: String?, installId: String?, body: JSONEncodable): Result<JSONObject> =
        runCatching {
            val jsonBody: JSONObject =
                body.toJSON(Formatter)

            val request = createPostRequest(
                environment.getUrl(Environment.Route.ANONYMIZE),
                headerProvider.provideHeaderV2(advertiserId, uuid, installId, logger),
                jsonBody,
            )

            httpClient.executeAsyncRequest(
                request,
                SEND_ANONYMIZE_REQUEST_NAME,
                logger,
            ).getOrThrow()
        }

    internal companion object {
        const val SEND_ANONYMIZE_REQUEST_NAME = "SendAnonymize"
    }
}
internal interface PrivacyApi {
    suspend fun anonymizeUser(advertiserId: String?, uuid: String?, installId: String?, body: JSONEncodable): Result<JSONObject>
}
