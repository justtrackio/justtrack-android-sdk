package io.justtrack

import android.os.Build
import io.justtrack.events.Dimension
import io.justtrack.events.Money
import io.justtrack.events.Unit
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.versions.SdkVersion
import org.json.JSONObject
import java.util.Date
import java.util.TreeMap

/**
 * This class is a builder for app events you can publish to the backend. Call
 * [JustTrackSdk.publishEvent] to publish it:
 * <pre>`AppEvent event = new AppEvent("namespace_module_action");
 * sdk.publishEvent(event);
`</pre> *
 *
 *
 * You can also include additional information about the event, either by passing additional parameters
 * to the constructor or by calling the setters afterwards:
 *
 * <pre>`AppEvent event = new AppEvent(
 * "namespace_module_action",
 * 5,
 * Unit.COUNT
 * );
 * sdk.publishEvent(event);
 * event = new AppEvent("namespace_module_action")
 * .addDimension("key1", "some value")
 * .addDimension("some other key", "some other value")
 * .setValue(5, Unit.COUNT);
 * sdk.publishEvent(event);
`</pre> *
 */
open class AppEvent private constructor(
    @get:JvmName("getName")
    internal val name: String,
    @get:JvmName("getDimensions")
    internal val dimensions: MutableMap<String?, String?> = TreeMap(),
    pValue: Double = 0.0,
    pUnit: Unit? = null,
    pCurrency: String? = null,
    @set:JvmName("setSessionId")
    internal var sessionId: String? = null,
    pHappenedAt: Date? = null,
) {
    private var value: Double = pValue
    private var unit: Unit? = pUnit
    private var currency: String? = pCurrency
    private val happenedAt: Date? = pHappenedAt

    constructor(name: String, dimensions: Map<String, String>) : this(
        name = name,
        dimensions = dimensions.toMutableMap(),
        pUnit = null,
    )

    /**
     * Create a new event with only the name specified.
     *
     * @param name The name of the event.
     */
    constructor(name: String) : this(
        name = name,
        dimensions = emptyMap(),
    )

    /**
     * Create a new event with a value and unit but without any dimensions set.
     *
     * @param name  The name of the event.
     * @param value The value of the event. Needs to be a finite value.
     * @param unit  The unit of value.
     */
    constructor(name: String, value: Double, unit: Unit) : this(
        name = name,
        dimensions = TreeMap(),
        pValue = if (unit == Unit.SECONDS) value * SECONDS_TO_MILLIS else value,
        pUnit = if (unit == Unit.SECONDS) Unit.MILLISECONDS else unit,
        pCurrency = null,
        sessionId = null,
        pHappenedAt = null,
    )

    /**
     * Create a new event with a value and currency but without any dimensions set.
     *
     * @param name  The name of the event.
     * @param money The value of the event with a currency. Needs to be a finite value.
     */
    constructor(name: String, money: Money) : this(
        name = name,
        dimensions = TreeMap(),
        pValue = money.value,
        pUnit = null,
        pCurrency = money.currency,
        sessionId = null,
        pHappenedAt = null,
    )

    /**
     * Create a new event with all dimensions and the unit and value set.
     * This constructor also allows you to specify the time the event happened. Internal use only.
     *
     * @param name       The name of the event.
     * @param value      The value of the event. Needs to be a finite value.
     * @param unit       The unit of value.
     * @param currency   The currency of the value. Should only be set if unit is null.
     * @param happenedAt The time the event happened at.
     */
    internal constructor(name: String, value: Double, unit: Unit?, currency: String?, happenedAt: Date?) : this(
        name = name,
        dimensions = TreeMap(),
        pValue = if (unit == Unit.SECONDS) value * SECONDS_TO_MILLIS else value,
        pUnit = if (unit == Unit.SECONDS) Unit.MILLISECONDS else unit,
        pCurrency = currency,
        sessionId = null,
        pHappenedAt = happenedAt,
    )

    /**
     * Copy an app event for modification before publishing it.
     *
     * @param event The event to initialize the event from.
     */
    internal constructor(event: AppEvent) : this(
        name = event.name,
        dimensions = TreeMap(event.dimensions),
        pValue = event.value,
        pUnit = event.unit,
        pCurrency = event.currency,
        sessionId = event.sessionId,
        pHappenedAt = event.happenedAt,
    )

    /**
     * Set the value and unit of the event to the given values.
     *
     * @param value The new value of the event. Needs to be a finite value.
     * @param unit  The [Unit] of value.
     * @return The modified app event for chaining.
     */
    fun setValue(value: Double, unit: Unit): AppEvent {
        this.value = if (unit == Unit.SECONDS) value * SECONDS_TO_MILLIS else value
        this.unit = if (unit == Unit.SECONDS) Unit.MILLISECONDS else unit
        this.currency = null

        return this
    }

    /**
     * Set the value and currency of the event to the given values.
     *
     * @param money The new value of the event with a currency. Needs to be a finite value.
     * @return The modified app event for chaining.
     */
    fun setValue(money: Money): AppEvent {
        this.value = money.value
        this.unit = null
        this.currency = money.currency

        return this
    }

    /**
     * Convenience method for [.setValue] with unit set to [Unit.COUNT].
     *
     * @param count The new count for the event. Needs to be a finite value.
     * @return The modified app event for chaining.
     */
    fun setCount(count: Double): AppEvent {
        setValue(count, Unit.COUNT)

        return this
    }

    /**
     * Convenience method for [.setValue] with unit set to [Unit.SECONDS].
     *
     * @param seconds The new seconds for the event. Needs to be a finite value.
     * @return The modified app event for chaining.
     */
    fun setSeconds(seconds: Double): AppEvent {
        setValue(seconds, Unit.SECONDS)

        return this
    }

    /**
     * Convenience method for [.setValue] with unit set to [Unit.MILLISECONDS].
     *
     * @param milliseconds The new milliseconds for the event. Needs to be a finite value.
     * @return The modified app event for chaining.
     */
    fun setMilliseconds(milliseconds: Double): AppEvent {
        setValue(milliseconds, Unit.MILLISECONDS)

        return this
    }

    /**
     * Add a dimension with the given name and value.
     *
     *
     * See [AppEvent.validate] to learn more about permissible dimension values.
     *
     * @param name  A unique name to be added to dimension's map
     * @param value A value that associate to the given name.
     * @return The modified app event for chaining.
     */
    fun addDimension(name: String, value: String?): AppEvent {
        if (!TextUtils.isNullOrEmpty(name)) {
            if (TextUtils.isNullOrEmpty(value)) {
                dimensions.remove(name)
            } else {
                dimensions[name] = value
            }
        }

        return this
    }

    /**
     * @see .addDimension
     */
    fun addDimension(name: Dimension, value: String?): AppEvent {
        return addDimension(name.toString(), value)
    }

    /**
     * Remove a dimension with the given name form an event again.
     *
     *
     * See [AppEvent.validate] to learn more about permissible dimension values.
     *
     * @param name The name of the dimension to be removed again.
     * @return The modified app event for chaining.
     */
    fun removeDimension(name: String): AppEvent {
        if (!TextUtils.isNullOrEmpty(name)) {
            dimensions.remove(name)
        }

        return this
    }

    /**
     * @see .removeDimension
     */
    fun removeDimension(name: Dimension): AppEvent {
        return removeDimension(name.toString())
    }

    /**
     * Validate the name and fields of the app event, throwing an error if any field is invalid.
     *
     *
     * The name of the event is must not exceed 256 characters and consist of only printable ISO 8859-1
     * characters (U+0020 to U+007E as well as U+00A0 to U+00FF). Any dimension value must be shorter
     * than 4096 characters and consist of only printable ISO 8859-1 characters (U+0020 to U+007E as
     * well as U+00A0 to U+00FF). The value of the event must be a finite value (thus, neither NaN or
     * ±Infinity).
     *
     *
     * You don't need to call this method normally, it will automatically be called once you submit
     * a [AppEvent] to [JustTrackSdk.publishEvent].
     *
     * @throws InvalidFieldException If any field has an invalid value.
     */
    @Throws(InvalidFieldException::class)
    fun validate() {
        if (name.isEmpty() || !Validation.validEventName(name)) {
            throw InvalidFieldException("name", name, 1, MAX_EVENT_NAME_LENGTH, "ISO 8859-1")
        }

        for ((key, value1) in dimensions) {
            if (!Validation.validDimensionName(key!!)) {
                throw InvalidFieldException("dimensions", key, MAX_EVENT_NAME_LENGTH, "[a-z0-9_]+")
            }
            if (!Validation.validDimensionValue(key, value1)) {
                throw InvalidFieldException("dimensions.$key", value1!!, MAX_DIMENSION_VALUE_LENGTH, "ISO 8859-1")
            }
        }

        if (dimensions.size > MAX_DIMENSION_SIZE) {
            throw InvalidFieldException(JSONObject(dimensions as Map<*, *>).toString(), MAX_DIMENSION_SIZE, dimensions.size)
        }

        if (!java.lang.Double.isFinite(value)) {
            throw InvalidFieldException("value", value)
        }

        if (currency != null) {
            Money(value, currency!!).validate()
        }
    }

    internal fun build(sessionId: String, sdkVersion: SdkVersion): PublishableAppEvent {
        return PublishableAppEvent(
            name,
            dimensions,
            value,
            unit,
            currency,
            (if (this.sessionId == null) sessionId else this.sessionId)!!,
            sdkVersion,
            happenedAt ?: Date(),
        )
    }

    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append("[AppEvent ").append(name)
        if (!dimensions.isEmpty()) {
            buffer.append(", dimensions = [")
            var firstDimension = true
            for ((dimension, value) in dimensions) {
                if (firstDimension) {
                    firstDimension = false
                } else {
                    buffer.append(", ")
                }
                if (!TextUtils.isNullOrEmpty(value.toString())) {
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
        if (sessionId != null) {
            buffer.append(", sessionId = ").append(sessionId)
        }
        buffer.append(", happenedAt = ").append(happenedAt?.toString() ?: "now")
        buffer.append("]")
        return buffer.toString()
    }

    override fun equals(obj: Any?): Boolean {
        if (obj !is AppEvent) {
            return false
        }
        val other = obj
        return name == other.name &&
            dimensions == other.dimensions &&
            value == other.value && unit == other.unit && (if (currency == null) other.currency == null else (currency == other.currency)) &&
            (if (sessionId == null) other.sessionId == null else (sessionId == other.sessionId)) &&
            (if (happenedAt == null) other.happenedAt == null else (happenedAt == other.happenedAt))
    }

    override fun hashCode(): Int {
        val valueHash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            java.lang.Double.hashCode(value)
        } else {
            value.toInt()
        }

        var hashCode = name.hashCode()
        hashCode = 31 * hashCode + dimensions.hashCode()
        hashCode = 31 * hashCode + valueHash
        hashCode = 31 * hashCode + (if (unit == null) 0 else unit.hashCode())
        hashCode = 31 * hashCode + (if (currency == null) 0 else currency.hashCode())
        hashCode = 31 * hashCode + (if (sessionId == null) 0 else sessionId.hashCode())
        hashCode = 31 * hashCode + (happenedAt?.hashCode() ?: 0)

        return hashCode
    }

    /** Constants for [AppEvent] validation limits. */
    companion object {
        /** Maximum length in characters for an event name. */
        const val MAX_EVENT_NAME_LENGTH = 256

        /** Maximum length in characters for a single dimension value. */
        const val MAX_DIMENSION_VALUE_LENGTH = 4096

        /** Maximum number of dimensions an event may carry. */
        const val MAX_DIMENSION_SIZE = 10
        private const val SECONDS_TO_MILLIS = 1000
    }
}
