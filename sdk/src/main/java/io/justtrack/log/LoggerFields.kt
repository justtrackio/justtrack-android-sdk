package io.justtrack.log

/**
 * Type used to pass additional fields to a log message. A [Logger] should be able to encode the
 * fields into the log message.
 */
interface LoggerFields {
    /**
     * Return all fields as a map from the field name to the field value.
     *
     * @return A map of all fields.
     */
    val fields: Map<String, String>
}
