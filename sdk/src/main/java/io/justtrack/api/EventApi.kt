package io.justtrack.api

import io.justtrack.Environment
import io.justtrack.Formatter
import io.justtrack.HttpClient
import io.justtrack.dtos.DTOAppEvent
import io.justtrack.log.Logger
import org.json.JSONObject

internal class EventApiImpl(
    private val httpClient: HttpClient,
    private val headerProvider: HeaderProvider,
    private val environment: Environment,
    private val logger: Logger,
) : EventApi {
    override suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?> =
        runCatching {
            val jsonBody: JSONObject = body.toJSON(Formatter)

            val request = createPostRequest(
                environment.getUrl(Environment.Route.TRACK_EVENT),
                headerProvider.provideHeaderV2(advertiserId, uuid, installId, logger),
                jsonBody,
            )
            httpClient.executeAsyncRequest(
                request,
                SEND_USER_EVENTS_REQUEST_NAME,
                logger,
            ).getOrThrow()
        }

    internal companion object {
        const val SEND_USER_EVENTS_REQUEST_NAME = "SendUserEvents"
    }
}

internal interface EventApi {
    suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?>
}
