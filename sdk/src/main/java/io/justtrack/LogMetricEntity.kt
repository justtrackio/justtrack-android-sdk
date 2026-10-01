package io.justtrack

import android.content.ContentValues
import io.justtrack.database.Database
import io.justtrack.database.DatabaseEntity
import io.justtrack.dtos.DTOLogMetric
import io.justtrack.log.Logger
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.util.Calendar
import org.jetbrains.annotations.VisibleForTesting

internal data class LogMetricEntity(
    @get:VisibleForTesting override var id: Long = -1,
    @get:VisibleForTesting internal val name: String,
    @get:VisibleForTesting internal val value: Double,
    @get:VisibleForTesting internal val dimensions: String,
    @get:VisibleForTesting internal val unit: String,
    @get:VisibleForTesting internal val timestamp: String,
    @get:VisibleForTesting internal val timestampInMS: Long,
    @get:VisibleForTesting internal var processingTimeInMS: Long = -1,
) : DatabaseEntity {
    internal constructor(data: LogStoreMetric, formatter: Formatter) : this(
        id = data.id,
        name = data.metric,
        value = data.value,
        dimensions = data.dimensions.toString(),
        unit = data.unit,
        timestamp = formatter.formatDateMilliseconds(data.timestamp),
        timestampInMS = data.timestamp.time,
    )

    @JvmName("transform")
    internal fun transform(formatter: Formatter, logger: Logger): LogStoreMetric {
        val fields = try {
            JSONObject(this.dimensions)
        } catch (exception: JSONException) {
            logger.warn(
                "Failed to transform dimensions for LogMetricEntity " +
                    "${exception.message} with ${this.dimensions}",
                exception,
            )
            JSONObject()
        }

        val date = try {
            formatter.parseDate(this.timestamp)
        } catch (exception: ParseException) {
            logger.warn(
                "Failed to parse date for LogMetricEntity " +
                    "${exception.message} with ${this.timestamp}",
                exception,
            )
            Calendar.getInstance().time
        }

        return LogStoreMetric(
            this.id,
            DTOLogMetric(
                this.name,
                fields,
                this.value,
                this.unit,
                date,
            ),
        )
    }

    override fun toContentValues(): ContentValues {
        val contentValues = ContentValues()
        if (id != -1L) {
            contentValues.put(Database.COMMON_ID, id)
        }
        contentValues.put(Database.METRIC_NAME, name)
        contentValues.put(Database.METRIC_VALUE, value)
        contentValues.put(Database.METRIC_DIMENSIONS, dimensions)
        contentValues.put(Database.METRIC_UNIT, unit)
        contentValues.put(Database.COMMON_TIMESTAMP, timestamp)
        contentValues.put(Database.COMMON_TIMESTAMP_IN_MS, timestampInMS)
        contentValues.put(Database.COMMON_PROCESSING_TIMESTAMP_IN_MS, processingTimeInMS)
        return contentValues
    }

    // This is require because this equal method ignore the id field in the database.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LogMetricEntity

        if (name != other.name) return false
        if (value != other.value) return false
        if (dimensions != other.dimensions) return false
        if (unit != other.unit) return false
        if (timestamp != other.timestamp) return false
        if (timestampInMS != other.timestampInMS) return false
        if (processingTimeInMS != other.processingTimeInMS) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + dimensions.hashCode()
        result = 31 * result + unit.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + timestampInMS.hashCode()
        result = 31 * result + processingTimeInMS.hashCode()
        return result
    }
}
