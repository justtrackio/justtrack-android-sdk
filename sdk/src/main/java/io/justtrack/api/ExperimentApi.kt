package io.justtrack.api

import io.justtrack.Environment
import io.justtrack.Formatter
import io.justtrack.HttpClient
import io.justtrack.JSONEncodable
import io.justtrack.log.Logger
import org.json.JSONObject

internal class ExperimentApiImpl(
    private val httpClient: HttpClient,
    private val headerProvider: HeaderProvider,
    private val environment: Environment,
    private val logger: Logger,
) : ExperimentApi {
    override suspend fun setExperimentVariant(body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<JSONObject> =
        runCatching {
            val jsonBody: JSONObject = body.toJSON(Formatter)

            val request = createPostRequest(
                environment.getUrl(Environment.Route.AB_TEST_ASSIGNMENT),
                headerProvider.provideHeaderV2(advertiserId, uuid, installId, logger),
                jsonBody,
            )

            httpClient.executeAsyncRequest(
                request,
                SET_EXPERIMENT_VARIANT_REQUEST_NAME,
                logger,
            ).getOrThrow()
        }

    internal companion object {
        const val SET_EXPERIMENT_VARIANT_REQUEST_NAME = "SendTestGroup"
    }
}

internal interface ExperimentApi {
    suspend fun setExperimentVariant(body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<JSONObject>
}
