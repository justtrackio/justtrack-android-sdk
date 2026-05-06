package io.justtrack.log

import java.io.PrintWriter
import java.io.StringWriter

/**
 * A type used to construct a [LoggerFields] instance.
 */
class LoggerFieldsBuilder(
    override val fields: MutableMap<String, String> = mutableMapOf(),
) : LoggerFields {

    private companion object {
        private const val MAX_CAUSE_NESTING_DEPTH = 5
        private const val MAX_MESSAGE_LENGTH = 255
    }

    /**
     * Add a new field. Will overwrite existing fields.
     *
     * @param field The name of the field.
     * @param value The value of the field.
     * @return A value you can chain calls with.
     */
    fun with(field: String, value: String): LoggerFieldsBuilder {
        fields[field] = value
        return this
    }

    /**
     * Add a new field. Will overwrite existing fields.
     *
     *
     * The error will be written to the field with an additional field for the stacktrace.
     * Additionally the cause of the exception will also be recorded.
     *
     * @param field The name of the field.
     * @param value The value of the field.
     * @return A value you can chain calls with.
     */
    fun with(field: String, value: Throwable): LoggerFieldsBuilder {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        value.printStackTrace(pw)
        fields[field] = sw.toString()

        var current: Throwable? = value
        val currentField = StringBuilder(field)
        var causeNesting = 0
        while (current != null && causeNesting < MAX_CAUSE_NESTING_DEPTH) {
            val message = StringBuilder(current.javaClass.name)
            if (current.message != null) {
                message.append(": ").append(current.message)
            }

            if (message.length > MAX_MESSAGE_LENGTH) {
                message.setLength(MAX_MESSAGE_LENGTH)
                message.append("...")
            }

            fields[currentField.toString() + "_message"] = message.toString()

            current = current.cause
            currentField.append("_cause")
            causeNesting++
        }

        return this
    }

    /**
     * Add a new field. Will overwrite existing fields.
     *
     * @param field The name of the field.
     * @param value The value of the field. Will be converted to a string.
     * @return A value you can chain calls with.
     */
    fun with(field: String, value: Char): LoggerFieldsBuilder {
        return with(field, "'$value'")
    }

    /**
     * Add a new field. Will overwrite existing fields.
     *
     * @param field The name of the field.
     * @param value The value of the field. Will be converted to a string.
     * @return A value you can chain calls with.
     */
    fun with(field: String, value: Byte): LoggerFieldsBuilder {
        return with(field, value.toString())
    }

    /**
     * Add a new field. Will overwrite existing fields.
     *
     * @param field The name of the field.
     * @param value The value of the field. Will be converted to a string.
     * @return A value you can chain calls with.
     */
    fun with(field: String, value: Short): LoggerFieldsBuilder {
        return with(field, value.toString())
    }

    /**
     * Add a new field. Will overwrite existing fields.
     *
     * @param field The name of the field.
     * @param value The value of the field. Will be converted to a string.
     * @return A value you can chain calls with.
     */
    fun with(field: String, value: Int): LoggerFieldsBuilder {
        return with(field, value.toString())
    }

    /**
     * Add a new field. Will overwrite existing fields.
     *
     * @param field The name of the field.
     * @param value The value of the field. Will be converted to a string.
     * @return A value you can chain calls with.
     */
    fun with(field: String, value: Long): LoggerFieldsBuilder {
        return with(field, value.toString())
    }

    /**
     * Add a new field. Will overwrite existing fields.
     *
     * @param field The name of the field.
     * @param value The value of the field. Will be converted to a string.
     * @return A value you can chain calls with.
     */
    fun with(field: String, value: Boolean): LoggerFieldsBuilder {
        return with(field, value.toString())
    }

    /**
     * Add a new field. Will overwrite existing fields.
     *
     * @param field The name of the field.
     * @param value The value of the field. Will be converted to a string.
     * @return A value you can chain calls with.
     */
    fun with(field: String, value: Float): LoggerFieldsBuilder {
        return with(field, value.toString())
    }

    /**
     * Add a new field. Will overwrite existing fields.
     *
     * @param field The name of the field.
     * @param value The value of the field. Will be converted to a string.
     * @return A value you can chain calls with.
     */
    fun with(field: String, value: Double): LoggerFieldsBuilder {
        return with(field, value.toString())
    }
}
