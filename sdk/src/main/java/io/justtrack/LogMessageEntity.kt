package io.justtrack

import android.content.ContentValues
import androidx.annotation.VisibleForTesting
import io.justtrack.database.Database
import io.justtrack.database.DatabaseEntity
import io.justtrack.log.Logger
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.util.Calendar

internal data class LogMessageEntity(
    @get:VisibleForTesting override var id: Long = -1,
    @get:VisibleForTesting val level: String,
    @get:VisibleForTesting val message: String,
    @get:VisibleForTesting val fields: String,
    @get:VisibleForTesting val timestamp: String,
    @get:VisibleForTesting val timestampInMS: Long,
    @get:VisibleForTesting var processingTimeInMS: Long = -1,
) : DatabaseEntity {
    internal constructor(data: LogStoreMessage, formatter: Formatter) : this(
        id = data.id,
        level = data.level.name,
        message = data.message,
        fields = data.fields.toString(),
        timestamp = formatter.formatDateMilliseconds(data.timestamp),
        timestampInMS = data.timestamp.time,
    )

    @JvmName("transform")
    internal fun transform(formatter: Formatter, logger: Logger): LogStoreMessage {
        val fields = try {
            JSONObject(this.fields)
        } catch (exception: JSONException) {
            logger.warn(
                "Failed to transform dimensions for LogMessageEntity " +
                    "${exception.message} with ${this.fields}",
                exception,
            )
            JSONObject()
        }

        val date = try {
            formatter.parseDate(this.timestamp)
        } catch (exception: ParseException) {
            logger.warn(
                "Failed to parse date for LogMessageEvent " +
                    "${exception.message} with ${this.timestamp}",
                exception,
            )
            Calendar.getInstance().time
        }

        return LogStoreMessage(
            this.id,
            DTOLogMessage(
                enumValueOfOrNull<LogLevel>(this.level) ?: LogLevel.DEBUG,
                this.message,
                fields,
                date,
            ),
        )
    }

    override fun toContentValues(): ContentValues {
        val contentValues = ContentValues()
        if (id != -1L) {
            contentValues.put(Database.COMMON_ID, id)
        }

        contentValues.put(Database.MESSAGE_LEVEL, level)
        contentValues.put(Database.MESSAGE_MESSAGE, message)
        contentValues.put(Database.MESSAGE_FIELDS, fields)
        contentValues.put(Database.COMMON_TIMESTAMP, timestamp)
        contentValues.put(Database.COMMON_TIMESTAMP_IN_MS, timestampInMS)
        contentValues.put(Database.COMMON_PROCESSING_TIMESTAMP_IN_MS, processingTimeInMS)
        return contentValues
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LogMessageEntity

        if (level != other.level) return false
        if (message != other.message) return false
        if (fields != other.fields) return false
        if (timestamp != other.timestamp) return false
        if (timestampInMS != other.timestampInMS) return false
        if (processingTimeInMS != other.processingTimeInMS) return false

        return true
    }

    override fun hashCode(): Int {
        var result = level.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + fields.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + timestampInMS.hashCode()
        result = 31 * result + processingTimeInMS.hashCode()
        return result
    }
}
