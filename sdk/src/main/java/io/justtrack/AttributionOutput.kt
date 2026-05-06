package io.justtrack

import io.justtrack.attribution.Attribution
import io.justtrack.retargeting.PreliminaryRetargetingParameters
import io.justtrack.retargeting.RetargetingParameters

internal data class AttributionOutput(
    private val attributionResponse: AttributionResponse,
    private val retargetingParameters: RetargetingParameters?,
    private val testGroup: Int?,
    private val sdkConfig: DTOAttributionOutputSdkConfig?,
    private val claimsTimedOut: Boolean,
) : PreliminaryRetargetingParameters.ValidateResult {
    @JvmName("getAttributionResponse")
    internal fun getAttributionResponse(): AttributionResponse {
        return attributionResponse
    }

    override val attribution: Attribution
        get() = AttributionImpl(attributionResponse)

    override val isValid: Boolean
        get() = retargetingParameters != null

    @JvmName("getRetargetingParameters")
    internal fun getRetargetingParameters(): RetargetingParameters? {
        return retargetingParameters
    }

    @JvmName("getTestGroup")
    internal fun getTestGroup(): Int? {
        return testGroup
    }

    @JvmName("didClaimsTimeOut")
    internal fun didClaimsTimeOut(): Boolean {
        return claimsTimedOut
    }

    override fun validParameters(): RetargetingParameters? {
        return retargetingParameters
    }

    @JvmName("getSdkConfig")
    internal fun getSdkConfig(): DTOAttributionOutputSdkConfig? {
        return sdkConfig
    }
}
