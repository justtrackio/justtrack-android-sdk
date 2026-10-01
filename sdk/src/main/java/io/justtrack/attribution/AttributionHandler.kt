package io.justtrack.attribution

import io.justtrack.AsyncFuture
import io.justtrack.AttributionDecision
import io.justtrack.AttributionOutput
import io.justtrack.AttributionResponse
import io.justtrack.AttributionTimestamps

internal interface AttributionHandler {
    fun handleAttribution(
        attributionFuture: AsyncFuture<AttributionOutput>,
        storedResponse: AttributionResponse?,
        attributionTimestamps: AttributionTimestamps?,
        forcedDecision: AttributionDecision?,
        attributionDecision: AttributionDecision,
    )
}
