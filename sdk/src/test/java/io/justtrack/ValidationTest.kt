package io.justtrack

import io.justtrack.events.Dimension
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ValidationTest {
    @Test
    fun `covers boundary and nullable dimension branches`() {
        Validation()

        assertTrue(Validation.validDimensionValue("dimension", null))
        assertTrue(Validation.validDimensionValue(Dimension.JT_TOKEN.toString(), "\u0000"))
        assertFalse(Validation.validDimensionValue("dimension", "x".repeat(4096)))
        assertFalse(Validation.validDimensionValue("dimension", "\u0100"))
        assertTrue(Validation.validCommonInput("abc", 4))
        assertFalse(Validation.validCommonInput("", 4))
        assertFalse(Validation.validCommonInput("\u0100", 4))
        assertFalse(Validation.validCommonInput("abcd", 4))
        assertTrue(Validation.validDimensionName("abc_123"))
        assertFalse(Validation.validDimensionName("ABC"))
    }
}
