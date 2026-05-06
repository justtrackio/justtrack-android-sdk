package io.justtrack.exceptions

import java.util.Locale

/**
 * Thrown when an [io.justtrack.AppEvent] field fails validation (e.g., name too long,
 * invalid characters, non-finite value, or too many dimensions).
 */
class InvalidFieldException : Exception {
    constructor(fieldName: String, fieldValue: String, formatDescription: String) : super(buildMessage(fieldName, fieldValue, formatDescription))

    constructor(
        fieldName: String,
        fieldValue: String,
        maxFieldLength: Int,
        fieldEncoding: String,
    ) : super(buildMessage(fieldName, fieldValue, maxFieldLength, fieldEncoding))

    constructor(
        fieldName: String,
        fieldValue: String,
        minFieldLength: Int,
        maxFieldLength: Int,
        fieldEncoding: String,
    ) : super(buildMessage(fieldName, fieldValue, minFieldLength, maxFieldLength, fieldEncoding))

    constructor(fieldName: String, fieldValue: Double) : super(buildMessage(fieldName, fieldValue))

    constructor(fieldValue: String, limitAmount: Int, currentAmount: Int) : super(buildMessage(fieldValue, limitAmount, currentAmount))

    constructor(message: String) : super(message)

    internal companion object {
        private fun buildMessage(fieldName: String, fieldValue: Double): String {
            return String.format(
                Locale.ENGLISH,
                "Invalid %s value: %f. The field value needs to be finite.",
                fieldName,
                fieldValue,
            )
        }

        private fun buildMessage(fieldName: String, fieldValue: String, formatDescription: String): String {
            return String.format(
                Locale.ENGLISH,
                "Invalid %s value: %s. %s",
                fieldName,
                fieldValue,
                formatDescription,
            )
        }

        private fun buildMessage(fieldName: String, fieldValue: String, minFieldLength: Int, maxFieldLength: Int, fieldEncoding: String): String {
            return String.format(
                Locale.ENGLISH,
                "Invalid %s value: '%s'. It needs to be between %d and %d characters and only include %s characters.",
                fieldName,
                fieldValue,
                minFieldLength,
                maxFieldLength,
                fieldEncoding,
            )
        }

        private fun buildMessage(fieldName: String, fieldValue: String, maxFieldLength: Int, fieldEncoding: String): String {
            return String.format(
                Locale.ENGLISH,
                "Invalid %s value: '%s'. It needs to be shorter than %d characters and only include %s characters.",
                fieldName,
                fieldValue,
                maxFieldLength,
                fieldEncoding,
            )
        }

        private fun buildMessage(fieldValue: String, limitAmount: Int, currentAmount: Int): String {
            return String.format(
                Locale.ENGLISH,
                "Too many dimensions: '%s'. The number of dimensions must not exceed %d. Current amount: %d",
                fieldValue,
                limitAmount,
                currentAmount,
            )
        }
    }
}
