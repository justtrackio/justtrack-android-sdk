package io.justtrack

import io.justtrack.retargeting.PreliminaryRetargetingParameters
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

internal class IntentLaunchDeciderTest {
    @Test
    fun `maps preliminary parameters to attribution decision`() {
        val parameters: PreliminaryRetargetingParameters = mock()

        assertEquals(
            AttributionDecision.USE_STORED_ATTRIBUTION,
            IntentLaunchDecider(null).needsReAttribution(null),
        )
        assertEquals(
            AttributionDecision.FETCH_RETARGETING_ATTRIBUTION,
            IntentLaunchDecider(parameters).needsReAttribution(null),
        )
    }
}
