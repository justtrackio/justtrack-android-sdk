package io.justtrack

import io.justtrack.retargeting.RetargetingParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.UUID

internal class AttributionOutputTest {
    @Test
    fun `exposes response attribution retargeting parameters and timeout flag`() {
        val response = response()
        val retargetingParameters: RetargetingParameters = mock()
        val output = AttributionOutput(response, retargetingParameters, claimsTimedOut = true)

        assertSame(response, output.getAttributionResponse())
        assertSame(retargetingParameters, output.getRetargetingParameters())
        assertSame(retargetingParameters, output.validParameters())
        assertTrue(output.isValid)
        assertTrue(output.didClaimsTimeOut())
        assertEquals(response.getUserType(), output.attribution.userType)
        assertEquals(response.getCampaign(), output.attribution.campaign)
    }

    @Test
    fun `is invalid when retargeting parameters are absent`() {
        val output = AttributionOutput(response(), retargetingParameters = null, claimsTimedOut = false)

        assertFalse(output.isValid)
        assertNull(output.getRetargetingParameters())
        assertNull(output.validParameters())
        assertFalse(output.didClaimsTimeOut())
    }

    @Test
    fun `supports data class equality copy hash code and string`() {
        val response = response()
        val retargetingParameters: RetargetingParameters = mock()
        val output = AttributionOutput(response, retargetingParameters, claimsTimedOut = false)
        val same = output.copy()

        assertEquals(output, same)
        assertEquals(output.hashCode(), same.hashCode())
        assertNotEquals(output, output.copy(attributionResponse = response(installId = "other-install-id")))
        assertNotEquals(output, output.copy(retargetingParameters = null))
        assertNotEquals(output, output.copy(claimsTimedOut = true))
        assertTrue(output.toString().contains("claimsTimedOut=false"))
    }

    private fun response(installId: String = "install-id"): AttributionResponse = TestAttributionResponse(installId)

    private class TestAttributionResponse(private val installId: String) : AttributionResponse {
        private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

        override fun getUserId(): UUID = userId
        override fun getInstallId(): String = installId
        override fun getUserType(): String = "acquisition"
        override fun getCampaign() = AttributionImpl.CampaignImpl("1", "campaign", "acquisition", false)
        override fun getChannel() = AttributionImpl.ChannelImpl(2, "channel", false)
        override fun getPartner() = AttributionImpl.PartnerImpl(3, "partner")
        override fun getSourceId(): String? = null
        override fun getSourceBundleId(): String? = null
        override fun getSourcePlacement(): String? = null
        override fun getAdsetId(): String? = null
        override fun getCreatedAt() = java.util.Date(0L)
        override fun getRedownload(): Boolean = false
    }
}
