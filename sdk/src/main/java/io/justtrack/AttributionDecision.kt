package io.justtrack

import kotlin.math.max

internal data class AttributionDecision(
    val claimsTimeout: Long,
    private val isRetargetingAttribution: Boolean,
    private val isRefresh: Boolean,
    val shouldFetchAttribution: Boolean,
) {
    val isFetchRetargetingAttribution = isRetargetingAttribution && !isRefresh

    fun withSlowClaimTimeout(): AttributionDecision {
        return AttributionDecision(
            ClaimProviderImpl.CLAIM_TIMEOUT_SLOW_MS,
            isRetargetingAttribution,
            isRefresh,
            true,
        )
    }

    fun shouldUseReferrerDetails(hasStoredResponse: Boolean): Boolean {
        return !isRetargetingAttribution || (!isRefresh && !hasStoredResponse)
    }

    val isFastClaimsTimeout: Boolean
        get() = claimsTimeout == ClaimProviderImpl.CLAIM_TIMEOUT_FAST_MS

    fun merge(other: AttributionDecision): AttributionDecision {
        return AttributionDecision(
            max(claimsTimeout.toDouble(), other.claimsTimeout.toDouble()).toLong(),
            if (shouldFetchAttribution) {
                isRetargetingAttribution && (!other.shouldFetchAttribution || other.isRetargetingAttribution)
            } else {
                other.isRetargetingAttribution
            },
            isRefresh && other.isRefresh,
            shouldFetchAttribution || other.shouldFetchAttribution,
        )
    }

    companion object {
        @JvmField
        val FETCH_FIRST_ATTRIBUTION: AttributionDecision = AttributionDecision(
            ClaimProviderImpl.CLAIM_TIMEOUT_FAST_MS,
            false,
            false,
            true,
        )

        @JvmField
        val FETCH_RETARGETING_ATTRIBUTION: AttributionDecision = AttributionDecision(
            ClaimProviderImpl.CLAIM_TIMEOUT_FAST_MS,
            true,
            false,
            true,
        )

        @JvmField
        val FETCH_RETARGETING_ATTRIBUTION_DELAYED: AttributionDecision = AttributionDecision(
            ClaimProviderImpl.CLAIM_TIMEOUT_FAST_MS,
            true,
            true,
            true,
        )

        @JvmField
        val USE_STORED_ATTRIBUTION: AttributionDecision = AttributionDecision(
            ClaimProviderImpl.CLAIM_TIMEOUT_FAST_MS,
            false,
            false,
            false,
        )
    }
}
