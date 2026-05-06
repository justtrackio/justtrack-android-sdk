package io.justtrack

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// a SimpleDateFormat is not thread safe, so we try to have a fresh instance every time we need
// one
internal object Formatter {

    @JvmName("formatDateSeconds")
    internal fun formatDateSeconds(date: Date): String {
        return getBackendDateFormatSeconds().format(date)
    }

    @JvmName("formatDateMilliseconds")
    internal fun formatDateMilliseconds(date: Date): String {
        return getBackendDateFormatMilliseconds().format(date)
    }

    @Throws(ParseException::class)
    @JvmName("parseDate")
    internal fun parseDate(date: String?): Date {
        if (date == null) throw ParseException("failed to parse, date is null", 0)

        val parsed: Date? = try {
            getBackendDateFormatMilliseconds().parse(date)
        } catch (e: ParseException) {
            null
        }

        val result: Date = parsed
            ?: try {
                getBackendDateFormatSeconds().parse(date)
            } catch (e: ParseException) {
                throw ParseException("failed to parse date '$date'", 0)
            }

        return result
    }

    private fun getBackendDateFormatSeconds(): SimpleDateFormat {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private fun getBackendDateFormatMilliseconds(): SimpleDateFormat {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
