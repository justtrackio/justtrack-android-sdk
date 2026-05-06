package io.justtrack

import io.justtrack.attribution.AdvertiserIdInfo

internal class TestGroupIdReaderTask(
    private val logger: HttpLogger,
    private val advertiserIdInfoFuture: AsyncFuture<AdvertiserIdInfo>,
    private val databaseInterface: DatabaseInterface,
) : Task<Int?> {
    override suspend fun execute(): Int? {
        return try {
            getOrCreateTestGroupId(
                advertiserIdInfoFuture,
                databaseInterface,
            )
        } catch (e: Exception) {
            logger.warn("Unable to get or create testGroupId", e)

            null
        }
    }

    private suspend fun getOrCreateTestGroupId(advertiserIdInfoFuture: AsyncFuture<AdvertiserIdInfo>, databaseInterface: DatabaseInterface): Int? {
        val cacheTestGroupId = databaseInterface.openAttribution().use {
            it.getTestGroupId()
        }

        return if (cacheTestGroupId != null) {
            cacheTestGroupId.id
        } else {
            val testGroupId = createTestGroupId(advertiserIdInfoFuture.await().advertiserId)
            databaseInterface.openAttribution().use {
                it.setTestGroupId(testGroupId)
            }
            testGroupId
        }
    }

    private fun createTestGroupId(advertiserId: String?): Int? {
        val id: String = if (advertiserId.isNullOrEmpty()) {
            return null
        } else if (advertiserId.endsWith(FIXED_DEBUG_GAID_SUFFIX)) {
            // User with network tracing enabled
            return null
        } else {
            advertiserId
        }

        val lastThreeChars = id.takeLast(3)
        val n = lastThreeChars.toIntOrNull(HEX_RADIX) ?: return null

        return (n % TEST_GROUP_COUNT) + 1
    }

    internal data class TestGroupId(val id: Int? = null)

    private companion object {
        private const val FIXED_DEBUG_GAID_SUFFIX = "10ca1ad1abe1"
        private const val HEX_RADIX = 16
        private const val TEST_GROUP_COUNT = 3
    }
}
