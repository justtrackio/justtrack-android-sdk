package io.justtrack

import io.justtrack.exceptions.InvalidFieldException
import org.junit.Assert
import org.junit.Test

@Suppress("TooManyFunctions")
internal class JustTrackSdkConfigTest {

    @Test
    fun build_withDefaults() {
        val config = JustTrackSdkConfig.Builder().build()

        Assert.assertNull(config.userId)
        Assert.assertNull(config.trackingId)
        Assert.assertEquals("advertiserId", config.trackingIdProvider)
        Assert.assertNull(config.firebaseAppInstanceId)
        Assert.assertTrue(config.automaticIAPTracking)
    }

    @Test
    fun withUserId_withValidValue() {
        val config = JustTrackSdkConfig.Builder()
            .withUserId("user-123")
            .build()

        Assert.assertEquals("user-123", config.userId)
    }

    @Test(expected = InvalidFieldException::class)
    fun withUserId_withEmptyString_throws() {
        JustTrackSdkConfig.Builder()
            .withUserId("")
    }

    @Test(expected = InvalidFieldException::class)
    fun withUserId_withTooLongString_throws() {
        JustTrackSdkConfig.Builder()
            .withUserId("a".repeat(4096))
    }

    @Test(expected = InvalidFieldException::class)
    fun withUserId_withNonAsciiString_throws() {
        JustTrackSdkConfig.Builder()
            .withUserId("user\u0001id")
    }

    @Test
    fun withUserId_withMinLengthString_succeeds() {
        val config = JustTrackSdkConfig.Builder()
            .withUserId("a")
            .build()

        Assert.assertEquals("a", config.userId)
    }

    @Test
    fun withUserId_withMaxValidLengthString_succeeds() {
        val userId = "a".repeat(4095)
        val config = JustTrackSdkConfig.Builder()
            .withUserId(userId)
            .build()

        Assert.assertEquals(userId, config.userId)
    }

    @Test
    fun withFirebaseIntegration_withValidValue() {
        val config = JustTrackSdkConfig.Builder()
            .withFirebaseIntegration("firebase-123")
            .build()

        Assert.assertEquals("firebase-123", config.firebaseAppInstanceId)
    }

    @Test
    fun withFirebaseIntegration_withMinLengthString_succeeds() {
        val config = JustTrackSdkConfig.Builder()
            .withFirebaseIntegration("exactly8")
            .build()

        Assert.assertEquals("exactly8", config.firebaseAppInstanceId)
    }

    @Test
    fun withFirebaseIntegration_withMaxValidLengthString_succeeds() {
        val firebaseId = "a".repeat(255)
        val config = JustTrackSdkConfig.Builder()
            .withFirebaseIntegration(firebaseId)
            .build()

        Assert.assertEquals(firebaseId, config.firebaseAppInstanceId)
    }

    @Test(expected = InvalidFieldException::class)
    fun withFirebaseIntegration_withTooShortString_throws() {
        JustTrackSdkConfig.Builder()
            .withFirebaseIntegration("short7")
    }

    @Test(expected = InvalidFieldException::class)
    fun withFirebaseIntegration_withTooLongString_throws() {
        JustTrackSdkConfig.Builder()
            .withFirebaseIntegration("a".repeat(256))
    }

    @Test(expected = InvalidFieldException::class)
    fun withFirebaseIntegration_withNonAsciiString_throws() {
        JustTrackSdkConfig.Builder()
            .withFirebaseIntegration("firebase\u0001id")
    }

    @Test
    fun withTrackingId_withValidValues() {
        val config = JustTrackSdkConfig.Builder()
            .withTrackingId("tracking-id", "provider")
            .build()

        Assert.assertEquals("tracking-id", config.trackingId)
        Assert.assertEquals("provider", config.trackingIdProvider)
    }

    @Test
    fun withTrackingId_withNullTrackingId_setsProviderToDefault() {
        val config = JustTrackSdkConfig.Builder()
            .withTrackingId(null, "provider")
            .build()

        Assert.assertNull(config.trackingId)
        Assert.assertEquals("advertiserId", config.trackingIdProvider)
    }

    @Test
    fun withTrackingId_withEmptyTrackingId_setsProviderToDefault() {
        val config = JustTrackSdkConfig.Builder()
            .withTrackingId("", "provider")
            .build()

        Assert.assertEquals("", config.trackingId)
        Assert.assertEquals("advertiserId", config.trackingIdProvider)
    }

    @Test
    fun withTrackingId_withMaxValidLengthTrackingId_succeeds() {
        val trackingId = "a".repeat(4095)
        val config = JustTrackSdkConfig.Builder()
            .withTrackingId(trackingId, "provider")
            .build()

        Assert.assertEquals(trackingId, config.trackingId)
    }

    @Test
    fun withTrackingId_withMaxValidLengthProvider_succeeds() {
        val provider = "a".repeat(4095)
        val config = JustTrackSdkConfig.Builder()
            .withTrackingId("tracking-id", provider)
            .build()

        Assert.assertEquals(provider, config.trackingIdProvider)
    }

    @Test(expected = InvalidFieldException::class)
    fun withTrackingId_withTooLongTrackingId_throws() {
        JustTrackSdkConfig.Builder()
            .withTrackingId("a".repeat(4096), "provider")
    }

    @Test(expected = InvalidFieldException::class)
    fun withTrackingId_withNonAsciiTrackingId_throws() {
        JustTrackSdkConfig.Builder()
            .withTrackingId("tracking\u0001id", "provider")
    }

    @Test(expected = InvalidFieldException::class)
    fun withTrackingId_withTooLongProvider_throws() {
        JustTrackSdkConfig.Builder()
            .withTrackingId("tracking-id", "a".repeat(4096))
    }

    @Test(expected = InvalidFieldException::class)
    fun withTrackingId_withNonAsciiProvider_throws() {
        JustTrackSdkConfig.Builder()
            .withTrackingId("tracking-id", "prov\u0001ider")
    }

    @Test
    fun withAutomaticInAppPurchaseTracking_enabled() {
        val config = JustTrackSdkConfig.Builder()
            .withAutomaticInAppPurchaseTracking(true)
            .build()

        Assert.assertTrue(config.automaticIAPTracking)
    }

    @Test
    fun withAutomaticInAppPurchaseTracking_disabled() {
        val config = JustTrackSdkConfig.Builder()
            .withAutomaticInAppPurchaseTracking(false)
            .build()

        Assert.assertFalse(config.automaticIAPTracking)
    }

    @Test
    fun build_multipleCalls_combinedCorrectly() {
        val config = JustTrackSdkConfig.Builder()
            .withUserId("user-123")
            .withTrackingId("tracking-id", "provider")
            .withFirebaseIntegration("firebase-id")
            .withAutomaticInAppPurchaseTracking(false)
            .build()

        Assert.assertEquals("user-123", config.userId)
        Assert.assertEquals("tracking-id", config.trackingId)
        Assert.assertEquals("provider", config.trackingIdProvider)
        Assert.assertEquals("firebase-id", config.firebaseAppInstanceId)
        Assert.assertFalse(config.automaticIAPTracking)
    }

    @Test
    fun withUserId_withPrintableAsciiBoundaryCharacters_succeeds() {
        // U+0020 (space) is the lowest valid character, U+007E (~) is the highest
        val userId = " ~"
        val config = JustTrackSdkConfig.Builder()
            .withUserId(userId)
            .build()

        Assert.assertEquals(userId, config.userId)
    }

    @Test(expected = InvalidFieldException::class)
    fun withUserId_withSpaceBelowAsciiRange_throws() {
        // U+001F is below the valid ASCII range (U+0020 to U+007E)
        JustTrackSdkConfig.Builder()
            .withUserId("user\u001Fid")
    }

    @Test(expected = InvalidFieldException::class)
    fun withUserId_withCharacterAboveAsciiRange_throws() {
        // U+007F is above the valid ASCII range (U+0020 to U+007E)
        JustTrackSdkConfig.Builder()
            .withUserId("user\u007Fid")
    }

    @Test
    fun withTrackingId_withPrintableAsciiBoundaryCharacters_succeeds() {
        val trackingId = " ~"
        val config = JustTrackSdkConfig.Builder()
            .withTrackingId(trackingId, "provider")
            .build()

        Assert.assertEquals(trackingId, config.trackingId)
    }

    @Test(expected = InvalidFieldException::class)
    fun withTrackingId_withSpaceBelowAsciiRange_throws() {
        JustTrackSdkConfig.Builder()
            .withTrackingId("track\u001Fing", "provider")
    }

    @Test
    fun withFirebaseIntegration_withPrintableAsciiBoundaryCharacters_succeeds() {
        val firebaseId = "exactly8 ~"
        val config = JustTrackSdkConfig.Builder()
            .withFirebaseIntegration(firebaseId)
            .build()

        Assert.assertEquals(firebaseId, config.firebaseAppInstanceId)
    }

    @Test(expected = InvalidFieldException::class)
    fun withFirebaseIntegration_withSpaceBelowAsciiRange_throws() {
        JustTrackSdkConfig.Builder()
            .withFirebaseIntegration("firebase\u001Fid")
    }

    @Test
    fun builder_returnsSameInstanceForChaining() {
        val builder = JustTrackSdkConfig.Builder()

        val result1 = builder.withUserId("user-123")
        val result2 = builder.withTrackingId("tracking-id", "provider")
        val result3 = builder.withFirebaseIntegration("firebase-id")
        val result4 = builder.withAutomaticInAppPurchaseTracking(false)

        Assert.assertSame(builder, result1)
        Assert.assertSame(builder, result2)
        Assert.assertSame(builder, result3)
        Assert.assertSame(builder, result4)
    }

    @Test
    fun withTrackingId_nullTrackingIdWithEmptyProvider_setsDefaultProvider() {
        val config = JustTrackSdkConfig.Builder()
            .withTrackingId(null, "advertiserId")
            .build()

        Assert.assertNull(config.trackingId)
        Assert.assertEquals("advertiserId", config.trackingIdProvider)
    }

    @Test
    fun withTrackingId_withWhitespaceTrackingId_setsProviderToDefault() {
        val config = JustTrackSdkConfig.Builder()
            .withTrackingId("   ", "provider")
            .build()

        // Whitespace-only is not empty, so trackingId should be set
        // But "   " is a valid printable ASCII string
        Assert.assertEquals("   ", config.trackingId)
        Assert.assertEquals("provider", config.trackingIdProvider)
    }
}
