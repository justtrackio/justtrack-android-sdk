package io.justtrack

import org.junit.Assert
import org.junit.Test
import java.text.ParseException
import java.util.Date

class FormatterTest {
    private val formatter = Formatter

    @Test
    fun formatDateSeconds() {
        Assert.assertEquals(
            "2020-06-15T13:46:59Z",
            formatter.formatDateSeconds(Date(1592228819000L)),
        )
    }

    @Test
    fun formatDateMilliseconds() {
        Assert.assertEquals(
            "2020-06-15T13:46:59.000Z",
            formatter.formatDateMilliseconds(Date(1592228819000L)),
        )
    }

    @Test
    @Throws(ParseException::class)
    fun parseDate() {
        Assert.assertEquals(Date(1592228819000L), formatter.parseDate("2020-06-15T13:46:59Z"))
        Assert.assertEquals(Date(1592228819123L), formatter.parseDate("2020-06-15T13:46:59.123Z"))
    }

    @Test
    @Throws(ParseException::class)
    fun formatAndParseSeconds() {
        val date = Date(System.currentTimeMillis() / 1000 * 1000)
        Assert.assertEquals(date, formatter.parseDate(formatter.formatDateSeconds(date)))
    }

    @Test
    @Throws(ParseException::class)
    fun formatAndParseMilliseconds() {
        val date = Date()
        Assert.assertEquals(date, formatter.parseDate(formatter.formatDateMilliseconds(date)))
    }
}
