package io.justtrack.events

import io.justtrack.exceptions.InvalidFieldException
import java.util.Locale

/**
 * A [Money] instance represents a monetary amount together with a currency.
 *
 * @property value The monetary amount. Needs to be a finite value.
 * @property currency The currency for the amount. Must be a 3-letter ISO 4217 code.
 */
class Money(
    /**
     * Get the value stored in this instance.
     *
     * @return The monetary value.
     */
    val value: Double,
    /**
     * Get the currency stored in this instance.
     *
     * @return The currency value.
     */
    val currency: String,
) {
    /**
     * Validate the information stored in this instance.
     *
     *
     * The monetary value must be a finite value (thus, neither NaN or ±Infinity). The currency code
     * needs to be an uppercase 3-letter ISO 4217 string.
     *
     *
     * You don't need to call this method normally, it will automatically be called once you submit
     * a [AppEvent] to [JusttrackSdk.publishEvent].
     *
     * @throws InvalidFieldException If any field has an invalid value.
     */
    @Throws(InvalidFieldException::class)
    fun validate() {
        if (!java.lang.Double.isFinite(value)) {
            throw InvalidFieldException("value", value)
        }

        if (currency.length != ISO_4217_CURRENCY_CODE_LENGTH || currency != currency.uppercase(Locale.getDefault())) {
            throw InvalidFieldException("currency", currency, "The value needs to be an uppercase 3-letter ISO 4217 string.")
        }
    }

    override fun toString(): String {
        return (
            "Money{" +
                "value=" + value +
                ", currency='" + currency + '\'' +
                '}'
            )
    }

    private companion object {
        private const val ISO_4217_CURRENCY_CODE_LENGTH = 3
    }
}
