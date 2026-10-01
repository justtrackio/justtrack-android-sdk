package io.justtrack.api

import io.justtrack.Environment
import io.justtrack.Formatter
import io.justtrack.HttpClient
import io.justtrack.JSONEncodable
import io.justtrack.log.Logger
import org.json.JSONObject

internal class LogApiImpl(
    private val httpClient: HttpClient,
    private val headerProvider: HeaderProvider,
    private val environment: Environment,
) : LogApi {
    override suspend fun sendLogs(logger: Logger, body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<Unit> {
        return runCatching {
            val jsonBody: JSONObject =
                body.toJSON(Formatter)

            val request = createPostRequest(
                environment.getUrl(Environment.Route.LOG),
                headerProvider.provideHeader(advertiserId, uuid, installId, logger),
                jsonBody,
            )

            httpClient.executeAsyncRequest(
                request,
                SEND_LOGS_REQUEST_NAME,
                logger,
            ).getOrThrow()
        }
    }

    internal companion object {
        const val SEND_LOGS_REQUEST_NAME = "SendLogs"
    }
}
internal interface LogApi {
    suspend fun sendLogs(logger: Logger, body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<Unit>
}
