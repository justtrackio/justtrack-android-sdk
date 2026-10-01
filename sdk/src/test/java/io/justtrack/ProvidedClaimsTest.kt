package io.justtrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ProvidedClaimsTest {
    @Test
    fun `iterates two claims and rejects overflow and exhausted reads`() {
        val claims = ProvidedClaims()
        val emptyIterator = claims.iterator()
        assertFalse(emptyIterator.hasNext())
        assertThrows(NoSuchElementException::class.java) { emptyIterator.next() }

        claims.addClaim("first")
        claims.addClaim("second")
        assertThrows(IllegalStateException::class.java) { claims.addClaim("third") }

        val iterator = claims.iterator()
        assertTrue(iterator.hasNext())
        assertEquals("first", iterator.next())
        assertTrue(iterator.hasNext())
        assertEquals("second", iterator.next())
        assertFalse(iterator.hasNext())
        assertThrows(NoSuchElementException::class.java) { iterator.next() }

        val oneClaimIterator = ProvidedClaims().also { it.addClaim("only") }.iterator()
        oneClaimIterator.next()
        assertThrows(NoSuchElementException::class.java) { oneClaimIterator.next() }
    }

    @Test
    fun `records timeout flag`() {
        val claims = ProvidedClaims()
        assertFalse(claims.isTimedOut)

        claims.setTimedOut()

        assertTrue(claims.isTimedOut)
    }
}
