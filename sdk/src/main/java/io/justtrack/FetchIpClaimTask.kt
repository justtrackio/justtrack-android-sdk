package io.justtrack

import android.accounts.NetworkErrorException
import io.justtrack.api.AttributionApi
import io.justtrack.dtos.DTOSignIPResponse
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.providers.AdvertiserIdProvider

internal class FetchIpClaimTask(
    private val deviceInfo: DeviceInfo,
    private val advertiserIdProvider: AdvertiserIdProvider,
    private val attributionApi: AttributionApi,
    private val logger: Logger,
    private val protocol: IPProtocol,
) : Task<String> {
    override suspend fun execute(): String {
        val connectionType = deviceInfo.getConnectionType()
        val start = System.currentTimeMillis()
        val advertiserIdValue = advertiserIdProvider.provideAdvertiserId().await().advertiserId

        val result = attributionApi.getSignedIpClaim(
            protocol,
            advertiserIdValue,
        )

        if (result.isSuccess) {
            val response = result.getOrNull() ?: error("FetchIpClaimTask is successful with no result")
            val parsedResponse = DTOSignIPResponse(response)
            val millis = System.currentTimeMillis() - start
            logger.publishMetric(
                protocol.claimDurationMetric,
                millis.toDouble(),
                LoggerFieldsBuilder()
                    .with("Network", connectionType.toString()),
            )
            logger.debug(
                "Got IP claim",
                LoggerFieldsBuilder()
                    .with("ip", parsedResponse.ip)
                    .with("type", parsedResponse.type),
            )
            return parsedResponse.token
        } else {
            val claimException = result.exceptionOrNull()
            if (claimException != null) {
                if (FetchClaimErrorClassifier.isCriticalException(claimException)) {
                    logger.error(
                        claimException.message ?: "Getting claim failed",
                        claimException,
                    )
                }

                throw claimException
            } else {
                throw NetworkErrorException("FetchIpClaimTask failed with unknown exception")
            }
        }
    }
}
