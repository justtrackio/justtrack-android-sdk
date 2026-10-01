package io.justtrack.dtos

import org.junit.Assert
import org.junit.Test

class LogLevelTest {
    @Test
    fun toStringReturnsLowercaseLevel() {
        Assert.assertEquals("debug", LogLevel.DEBUG.toString())
        Assert.assertEquals("info", LogLevel.INFO.toString())
        Assert.assertEquals("warn", LogLevel.WARN.toString())
        Assert.assertEquals("error", LogLevel.ERROR.toString())
    }

    @Test
    fun valueOfParsesUppercaseNames() {
        Assert.assertEquals(LogLevel.DEBUG, LogLevel.valueOf("DEBUG"))
        Assert.assertEquals(LogLevel.INFO, LogLevel.valueOf("INFO"))
        Assert.assertEquals(LogLevel.WARN, LogLevel.valueOf("WARN"))
        Assert.assertEquals(LogLevel.ERROR, LogLevel.valueOf("ERROR"))
    }

    @Test
    fun allValuesArePresent() {
        val entries = LogLevel.entries
        Assert.assertTrue(entries.contains(LogLevel.DEBUG))
        Assert.assertTrue(entries.contains(LogLevel.INFO))
        Assert.assertTrue(entries.contains(LogLevel.WARN))
        Assert.assertTrue(entries.contains(LogLevel.ERROR))
        Assert.assertEquals(4, entries.size)
    }
}
