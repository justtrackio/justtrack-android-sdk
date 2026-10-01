package io.justtrack

import androidx.annotation.VisibleForTesting
import io.justtrack.TextUtils.isNullOrEmpty
import io.justtrack.events.Unit
import io.justtrack.versions.SdkVersion
import java.util.Date

internal data class PublishableAppEvent(
    val name: String,
    val dimensions: Map<String?, String?>,
    val value: Double,
    val unit: Unit?,
    val currency: String?,
    val sessionId: String,
    val sdkVersion: SdkVersion,
    val happenedAt: Date,
) {

    override fun toString(): String {
        val formatter = Formatter
        val buffer = StringBuilder()
        buffer.append("[PublishableUserEvent ").append(name)
        if (!dimensions.isEmpty()) {
            buffer.append(", dimensions = [")
            var firstDimension = true
            for (entry in dimensions.entries) {
                if (firstDimension) {
                    firstDimension = false
                } else {
                    buffer.append(", ")
                }
                val dimension = entry.key
                val value = entry.value
                if (!isNullOrEmpty(value)) {
                    buffer.append(dimension).append(" = ").append(value)
                }
            }
            buffer.append("]")
        }

        buffer.append(", value = ").append(value).append(" ")
        if (unit != null) {
            buffer.append(unit)
        } else if (currency != null) {
            buffer.append(currency)
        } else {
            buffer.append("null")
        }
        buffer.append(", sessionId = ").append(sessionId)
        buffer.append(", sdkVersionMajor = ").append(sdkVersion.major)
        buffer.append(", sdkVersionMinor = ").append(sdkVersion.minor)
        buffer.append(", sdkVersionPatch = ").append(sdkVersion.patch)
        buffer.append(", sdkVersionName = ").append(sdkVersion.name)
        buffer.append(", happenedAt = ").append(formatter.formatDateMilliseconds(happenedAt))
        buffer.append("]")
        return buffer.toString()
    }

    @VisibleForTesting
    fun equalWithoutDate(obj: Any?): Boolean {
        if (obj !is PublishableAppEvent) {
            return false
        }
        val other = obj
        return name == other.name &&
            dimensions == other.dimensions &&
            value == other.value && unit == other.unit &&
            (if (currency == null) other.currency == null else (currency == other.currency)) &&
            sessionId == other.sessionId &&
            sdkVersion.major == other.sdkVersion.major &&
            sdkVersion.minor == other.sdkVersion.minor &&
            sdkVersion.patch == other.sdkVersion.patch &&
            sdkVersion.name == other.sdkVersion.name
    }
}
