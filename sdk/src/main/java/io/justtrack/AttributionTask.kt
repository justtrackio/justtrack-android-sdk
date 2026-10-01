package io.justtrack

import android.content.Intent
import com.google.android.gms.appset.AppSetIdInfo
import io.justtrack.AttributionImpl.CampaignImpl
import io.justtrack.AttributionImpl.ChannelImpl
import io.justtrack.AttributionImpl.PartnerImpl
import io.justtrack.api.AttributionApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.dtos.DTOAttributionOutput
import io.justtrack.events.MetricUnit
import io.justtrack.installreferrer.api.ReferrerDetails
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.retargeting.RetargetingParameters
import io.justtrack.util.InstallerSourceIdProvider
import io.justtrack.versions.VersionBundle
import org.json.JSONObject
import java.util.UUID

internal class AttributionTask(
    private val intent: Intent?,
    private val databaseInterface: DatabaseInterface,
    private val attributionParams: AttributionParams,
    private val attributionApi: AttributionApi,
    private val logger: HttpLogger,
    private val versionBundle: VersionBundle,
    installerSourceIdProvider: InstallerSourceIdProvider,
) : Task<AttributionOutput> {
    override suspend fun execute(): AttributionOutput {
        val connectionType = attributionParams.deviceInfo.getConnectionType()
        val start = System.currentTimeMillis()
        attributionParams.claimProvider.refreshClaims()
        val details: ReferrerDetails? = try {
            attributionParams.referrerDetails.await()
        } catch (e: Throwable) {
            logger.warn("Failed to read install referrer", e)
            null
        }
        val advertiserIdInfo = attributionParams.advertiserId.await()
        val appSetIdInfo = attributionParams.appSetIdInfoFuture.await()
        val claims = attributionParams.claimProvider.provideClaims(attributionParams.claimTimeout)
        val userIdString = attributionParams.userIdProvider.provideUserIdFuture().await()
        val userId = UUID.fromString(userIdString)
        val installId = attributionParams.idManager.getOrCreateInstallId().await()
        val integritySecret = attributionParams.integritySecretFuture.await()

        val body: JSONEncodable = AttributionInputBuilder(
            versionBundle,
            attributionParams.deviceInfo,
            advertiserIdInfo.advertiserId,
            advertiserIdInfo.isLimitedAdTracking,
            attributionParams.trackingId,
            attributionParams.trackingProvider,
            claims,
            installSource,
            details,
            appSetIdInfo?.id,
            userId.toString(),
            installId,
            attributionParams.sdkConfig.userId,
            integritySecret,
        ).build()

        val result = attributionApi.sendAttributionRequest(
            body,
            advertiserIdInfo.advertiserId,
        )
        if (result.isSuccess) {
            try {
                val attributionOutput = parseResponse(result.getOrNull(), claims, userId)

                databaseInterface.openAttribution().use {
                    it.setAttributionFinished(
                        attributionOutput.getAttributionResponse(),
                    )
                }

                val millis = System.currentTimeMillis() - start
                val dimensions: LoggerFields = LoggerFieldsBuilder().with("Network", connectionType.toString())
                logger.publishMetric(
                    ATTRIBUTION_DURATION_METRIC,
                    millis.toDouble(),
                    dimensions,
                )
                return attributionOutput
            } catch (e: ParseAttributionException) {
                throw IllegalStateException("Parsing server response for attribution failed", e)
            }
        } else {
            val cause = result.exceptionOrNull() ?: IllegalStateException("AttributionTask failed with unknown exception")
            throw AttributionException(cause)
        }
    }

    private val installSource = installerSourceIdProvider.getInstallerSourceId()

    @Throws(ParseAttributionException::class)
    private fun parseResponse(response: JSONObject?, claims: ProvidedClaims, userId: UUID): AttributionOutput {
        if (response == null) {
            throw ParseAttributionException("Response was null")
        }
        return try {
            val output = DTOAttributionOutput(response, Formatter)
            logger.setUser(userId, output.user.installId)
            val attributionResponse: AttributionResponse = AttributionResponseImpl(
                userId,
                output.user.installId,
                output.user.type,
                CampaignImpl(
                    output.attribution.campaign.externalId,
                    output.attribution.campaign.name,
                    output.attribution.campaign.type,
                    output.attribution.campaign.organic,
                ),
                ChannelImpl(
                    output.attribution.channel.id,
                    output.attribution.channel.name,
                    output.attribution.channel.incent,
                ),
                PartnerImpl(
                    output.attribution.network.id,
                    output.attribution.network.name,
                ),
                output.attribution.sourceId,
                output.attribution.sourceBundleId,
                output.attribution.sourcePlacement,
                output.attribution.adsetId,
                output.attribution.attributedAt,
                output.user.redownload,
            )
            var retargetingParameters: RetargetingParameters? = null
            val retargeting = output.retargeting
            if (retargeting != null) {
                val appWasAlreadyInstalled = Intent.ACTION_VIEW == intent?.action && retargeting.url == intent.dataString
                if (appWasAlreadyInstalled) {
                    logger.debug("Detected retargeting app launch of already installed app")
                } else {
                    val fields =
                        LoggerFieldsBuilder()
                            .with("action", intent?.action ?: "")
                            .with("intentUrl", intent?.dataString ?: "")
                            .with("retargetingUrl", retargeting.url)
                    logger.debug("Retargeting app launch, but app was not yet installed", fields)
                }
                retargetingParameters =
                    RetargetingParametersImpl(
                        appWasAlreadyInstalled,
                        retargeting.url,
                        retargeting.attributes,
                    )
            }
            AttributionOutput(
                attributionResponse,
                retargetingParameters,
                claims.isTimedOut,
            )
        } catch (e: Exception) {
            throw ParseAttributionException("Failed to parse attribution", e)
        }
    }

    internal data class AttributionParams(
        internal val idManager: AttributionIdManager,
        internal val userIdProvider: UserIdProvider,
        internal val advertiserId: AsyncFuture<AdvertiserIdInfo>,
        internal val referrerDetails: AsyncFuture<ReferrerDetails?>,
        internal val appSetIdInfoFuture: AsyncFuture<AppSetIdInfo?>,
        internal val trackingId: String?,
        internal val trackingProvider: String,
        internal val claimProvider: ClaimProvider,
        internal val claimTimeout: Long,
        internal val sdkConfig: JustTrackSdkConfig,
        internal val deviceInfo: DeviceInfo,
        internal val integritySecretFuture: AsyncFuture<String>,
    )

    internal class ParseAttributionException : Exception {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }

    companion object {
        private val ATTRIBUTION_DURATION_METRIC =
            Metric(metric = "AttributionDuration", unit = MetricUnit.MILLISECONDS)
    }
}
