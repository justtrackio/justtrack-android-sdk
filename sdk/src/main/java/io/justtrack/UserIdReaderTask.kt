package io.justtrack

import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.log.Logger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale
import java.util.UUID
import kotlin.experimental.xor

internal class UserIdReaderTask(
    private val deviceInfo: DeviceInfo,
    private val logger: HttpLogger,
    private val attributionIdManager: AttributionIdManager,
    private val attributionParams: AttributionParams,
    private val applicationPackageName: String,
) : Task<UUID> {
    @Throws(NoSuchAlgorithmException::class, RuntimeException::class)
    override suspend fun execute(): UUID {
        try {
            val result = getOrCreateUserId(
                logger,
                attributionIdManager,
                attributionParams,
                applicationPackageName,
            )
            return UUID.fromString(result)
        } catch (e: Exception) {
            logger.error("Unable to create user id", e)
            throw e
        }
    }

    @Throws(NoSuchAlgorithmException::class, RuntimeException::class)
    private suspend fun getOrCreateUserId(
        logger: Logger,
        attributionIdManager: AttributionIdManager,
        attributionParams: AttributionParams,
        applicationPackageName: String,
    ): String {
        val cachedUserId: String? = attributionIdManager.getStoredUserId().await()?.toString()

        return if (cachedUserId != null) {
            cachedUserId
        } else {
            val userIdString = createUserId(
                logger,
                attributionParams.advertiserIdFuture.await().advertiserId,
                attributionParams.trackingId,
                deviceInfo.getAndroidIdOrDefault(""),
                applicationPackageName,
            )
            attributionIdManager.storeUserId(userIdString)
            userIdString
        }
    }

    @Suppress("MagicNumber") // UUID version/variant bitmasks per RFC 4122
    @Throws(NoSuchAlgorithmException::class, RuntimeException::class)
    private fun createUserId(logger: Logger, advertiserId: String?, trackingId: String?, deviceId: String?, applicationPackageName: String): String {
        val packageId = applicationPackageName.lowercase()

        val uniqueId: String = if (!advertiserId.isNullOrEmpty()) {
            advertiserId.lowercase()
        } else if (!trackingId.isNullOrEmpty()) {
            trackingId.lowercase()
        } else if (!deviceId.isNullOrEmpty()) {
            deviceId.lowercase()
        } else {
            throw RuntimeException("No unique user id found")
        }

        val input = String.format(Locale.US, "%s-%s-%s", BuildConfig.USER_ID_SECRET, packageId, uniqueId)
        val hashed: ByteArray = try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(input.toByteArray())
        } catch (e: NoSuchAlgorithmException) {
            logger.error("Unable to generate userId", e)
            throw e
        }
        val uuid = ByteArray(UUID_BYTE_LENGTH)
        for (i in 0 until UUID_BYTE_LENGTH) {
            uuid[i] = (hashed[i * 2] xor hashed[i * 2 + 1])
        }

        // Set UUID version 4 and variant (RFC 4122)
        uuid[6] = (uuid[6].toInt() and 0x0F or 0x40).toByte()
        uuid[8] = (uuid[8].toInt() and 0x3F or 0x80).toByte()
        return String.format(
            Locale.US,
            "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
            uuid[0],
            uuid[1],
            uuid[2],
            uuid[3],
            uuid[4],
            uuid[5],
            uuid[6],
            uuid[7],
            uuid[8],
            uuid[9],
            uuid[10],
            uuid[11],
            uuid[12],
            uuid[13],
            uuid[14],
            uuid[15],
        )
    }

    internal data class AttributionParams(
        internal val advertiserIdFuture: AsyncFuture<AdvertiserIdInfo>,
        internal val trackingId: String?,
    )

    private companion object {
        private const val UUID_BYTE_LENGTH = 16
    }
}
