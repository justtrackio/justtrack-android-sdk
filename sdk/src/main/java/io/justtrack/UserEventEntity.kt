package io.justtrack

import android.content.ContentValues
import androidx.annotation.VisibleForTesting
import io.justtrack.database.Database
import io.justtrack.database.DatabaseEntity
import io.justtrack.events.Unit
import io.justtrack.log.Logger
import io.justtrack.versions.SdkVersionImpl
import org.json.JSONObject
import java.text.ParseException
import java.util.Calendar
import java.util.TreeMap
import java.util.UUID

internal data class UserEventEntity(
    @get:VisibleForTesting override var id: Long = -1,
    @get:VisibleForTesting val eventId: String,
    @get:VisibleForTesting val eventName: String,
    @get:VisibleForTesting val dimensions: String,
    @get:VisibleForTesting val sessionId: String,
    @get:VisibleForTesting val value: Double,
    @get:VisibleForTesting val unit: String?,
    @get:VisibleForTesting val currency: String?,
    @get:VisibleForTesting val timestamp: String,
    @get:VisibleForTesting val timestampInMS: Long,
    @get:VisibleForTesting var processingTimeInMS: Long = -1,
    @get:VisibleForTesting var sequenceNumber: Long = -1,
    @get:VisibleForTesting val sdkVersionMajor: Long,
    @get:VisibleForTesting val sdkVersionMinor: Long,
    @get:VisibleForTesting val sdkVersionPatch: Long,
    @get:VisibleForTesting var sdkVersionName: String,
) : DatabaseEntity {
    internal constructor(data: StorableEvent, formatter: Formatter) : this(
        id = data.id,
        eventId = data.eventId.toString(),
        eventName = data.event.name,
        dimensions = JSONObject(data.event.dimensions.mapKeys { dimen -> dimen.key }).toString(),
        sessionId = data.event.sessionId,
        value = data.event.value,
        unit = data.event.unit?.name,
        currency = data.event.currency,
        timestamp = formatter.formatDateMilliseconds(data.getHappenedAt()),
        timestampInMS = data.getHappenedAt().time,
        sequenceNumber = data.sequenceNumber,
        sdkVersionMajor = data.event.sdkVersion.major.toLong(),
        sdkVersionMinor = data.event.sdkVersion.minor.toLong(),
        sdkVersionPatch = data.event.sdkVersion.patch.toLong(),
        sdkVersionName = data.event.sdkVersion.name,
    )

    @JvmName("transform")
    internal fun transform(formatter: Formatter, logger: Logger, platformType: PlatformType): PublishingEvent {
        val resultDimensionMap = TreeMap<String, String?>()
        try {
            val dimensionJson = JSONObject(this.dimensions)
            for (key in dimensionJson.keys()) {
                resultDimensionMap[key] = dimensionJson.get(key) as String?
            }
        } catch (exception: Exception) {
            logger.warn(
                "Failed to transform dimensions for UserEventEntity " +
                    "${exception.message} with ${this.dimensions}",
            )
        }

        val date = try {
            formatter.parseDate(this.timestamp)
        } catch (exception: ParseException) {
            logger.warn(
                "Failed to parse date for UserEventEntity " +
                    "${exception.message} with ${this.timestamp}",
            )
            Calendar.getInstance().time
        }

        return PublishingEvent(
            this.id,
            UUID.fromString(this.eventId),
            PublishableAppEvent(
                this.eventName,
                resultDimensionMap,
                this.value,
                enumValueOfOrNull<Unit>(this.unit ?: ""),
                this.currency,
                this.sessionId,
                SdkVersionImpl(
                    sdkVersionMajor.toInt(),
                    sdkVersionMinor.toInt(),
                    sdkVersionPatch.toInt(),
                    sdkVersionName,
                    platformType,
                ),
                date,
            ),
            sequenceNumber,
        )
    }

    override fun toContentValues(): ContentValues {
        val contentValues = ContentValues()
        if (id != -1L) {
            contentValues.put(Database.COMMON_ID, id)
        }
        contentValues.put(Database.EVENT_EVENT_ID, eventId)
        contentValues.put(Database.EVENT_NAME, eventName)
        contentValues.put(Database.EVENT_DIMENSIONS, dimensions)
        contentValues.put(Database.EVENT_VALUE, value)
        contentValues.put(Database.EVENT_UNIT, unit)
        contentValues.put(Database.EVENT_CURRENCY, currency)
        contentValues.put(Database.EVENT_SESSION_ID, sessionId)
        contentValues.put(Database.COMMON_TIMESTAMP, timestamp)
        contentValues.put(Database.COMMON_TIMESTAMP_IN_MS, timestampInMS)
        contentValues.put(Database.COMMON_PROCESSING_TIMESTAMP_IN_MS, processingTimeInMS)
        contentValues.put(Database.EVENT_SEQUENCE_NUMBER, sequenceNumber)
        contentValues.put(Database.EVENT_SDK_VERSION_MAJOR, sdkVersionMajor)
        contentValues.put(Database.EVENT_SDK_VERSION_MINOR, sdkVersionMinor)
        contentValues.put(Database.EVENT_SDK_VERSION_PATCH, sdkVersionPatch)
        contentValues.put(Database.EVENT_SDK_VERSION_NAME, sdkVersionName)
        return contentValues
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserEventEntity

        if (eventId != other.eventId) return false
        if (eventName != other.eventName) return false
        if (dimensions != other.dimensions) return false
        if (value != other.value) return false
        if (unit != other.unit) return false
        if (currency != other.currency) return false
        if (sessionId != other.sessionId) return false
        if (timestamp != other.timestamp) return false
        if (timestampInMS != other.timestampInMS) return false
        if (processingTimeInMS != other.processingTimeInMS) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (sdkVersionMajor != other.sdkVersionMajor) return false
        if (sdkVersionMinor != other.sdkVersionMinor) return false
        if (sdkVersionPatch != other.sdkVersionPatch) return false
        if (sdkVersionName != other.sdkVersionName) return false
        return true
    }

    override fun hashCode(): Int {
        var result = eventId.hashCode()
        result = 31 * result + eventName.hashCode()
        result = 31 * result + dimensions.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + (unit?.hashCode() ?: 0)
        result = 31 * result + (currency?.hashCode() ?: 0)
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + timestampInMS.hashCode()
        result = 31 * result + processingTimeInMS.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + sdkVersionMajor.hashCode()
        result = 31 * result + sdkVersionMinor.hashCode()
        result = 31 * result + sdkVersionPatch.hashCode()
        result = 31 * result + sdkVersionName.hashCode()
        return result
    }
}
