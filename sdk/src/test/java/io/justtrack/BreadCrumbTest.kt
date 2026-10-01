package io.justtrack

import io.justtrack.dtos.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Date

internal class BreadCrumbTest {
    @Test
    fun `exposes fields and json`() {
        val breadCrumb = BreadCrumb("message", "category", LogLevel.ERROR, Date(0L))

        assertEquals("message", breadCrumb.message)
        assertEquals("category", breadCrumb.category)
        assertEquals(LogLevel.ERROR, breadCrumb.level)
        assertEquals(Date(0L), breadCrumb.timeStamp)

        val same = breadCrumb.copy()
        assertEquals(breadCrumb, same)
        assertEquals(breadCrumb.hashCode(), same.hashCode())
        assertNotEquals(breadCrumb, breadCrumb.copy(message = "other"))
    }
}
