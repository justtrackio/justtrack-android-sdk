package io.justtrack.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PrintWriter
import java.io.StringWriter

internal class LoggerFieldsBuilderTest {

    @Test
    fun `with String value`() {
        val builder = LoggerFieldsBuilder()
        builder.with("key", "value")
        assertEquals("value", builder.fields["key"])
    }

    @Test
    fun `with Char value`() {
        val builder = LoggerFieldsBuilder()
        builder.with("key", 'c')
        assertEquals("'c'", builder.fields["key"])
    }

    @Test
    fun `with Byte value`() {
        val builder = LoggerFieldsBuilder()
        val byteValue: Byte = 10
        builder.with("key", byteValue)
        assertEquals("10", builder.fields["key"])
    }

    @Test
    fun `with Short value`() {
        val builder = LoggerFieldsBuilder()
        val shortValue: Short = 100
        builder.with("key", shortValue)
        assertEquals("100", builder.fields["key"])
    }

    @Test
    fun `with Int value`() {
        val builder = LoggerFieldsBuilder()
        builder.with("key", 123)
        assertEquals("123", builder.fields["key"])
    }

    @Test
    fun `with Long value`() {
        val builder = LoggerFieldsBuilder()
        builder.with("key", 123L)
        assertEquals("123", builder.fields["key"])
    }

    @Test
    fun `with Boolean value`() {
        val builder = LoggerFieldsBuilder()
        builder.with("key", true)
        assertEquals("true", builder.fields["key"])
        builder.with("otherKey", false)
        assertEquals("false", builder.fields["otherKey"])
    }

    @Test
    fun `with Float value`() {
        val builder = LoggerFieldsBuilder()
        builder.with("key", 123.45f)
        assertEquals("123.45", builder.fields["key"])
    }

    @Test
    fun `with Double value`() {
        val builder = LoggerFieldsBuilder()
        builder.with("key", 123.456)
        assertEquals("123.456", builder.fields["key"])
    }

    @Test
    fun `with Throwable value - single exception`() {
        val builder = LoggerFieldsBuilder()
        val exception = IllegalArgumentException("Test Exception")
        builder.with("error", exception)

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        exception.printStackTrace(pw)
        val expectedStackTrace = sw.toString()

        assertEquals(expectedStackTrace, builder.fields["error"])
        assertEquals("java.lang.IllegalArgumentException: Test Exception", builder.fields["error_message"])
    }

    @Test
    fun `with Throwable value - nested exceptions`() {
        val builder = LoggerFieldsBuilder()
        val cause2 = NullPointerException("Cause 2")
        val cause1 = RuntimeException("Cause 1", cause2)
        val exception = IllegalArgumentException("Test Exception", cause1)
        builder.with("error", exception)

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        exception.printStackTrace(pw)
        val expectedStackTrace = sw.toString()

        assertEquals(expectedStackTrace, builder.fields["error"])
        assertEquals("java.lang.IllegalArgumentException: Test Exception", builder.fields["error_message"])
        assertEquals("java.lang.RuntimeException: Cause 1", builder.fields["error_cause_message"])
        assertEquals("java.lang.NullPointerException: Cause 2", builder.fields["error_cause_cause_message"])
    }

    @Test
    fun `with Throwable value - nested exceptions limited to 5 levels`() {
        val builder = LoggerFieldsBuilder()
        var currentCause: Throwable = IllegalStateException("Innermost cause")
        for (i in 1..6) { // Create 6 levels of nesting
            currentCause = RuntimeException("Cause level $i", currentCause)
        }
        val exception = IllegalArgumentException("Test Exception", currentCause)

        builder.with("error", exception)

        assertEquals("java.lang.IllegalArgumentException: Test Exception", builder.fields["error_message"])
        assertEquals("java.lang.RuntimeException: Cause level 6", builder.fields["error_cause_message"])
        assertEquals("java.lang.RuntimeException: Cause level 5", builder.fields["error_cause_cause_message"])
        assertEquals("java.lang.RuntimeException: Cause level 4", builder.fields["error_cause_cause_cause_message"])
        assertEquals("java.lang.RuntimeException: Cause level 3", builder.fields["error_cause_cause_cause_cause_message"])
        // The 6th level of cause (Innermost cause) should not be present
        assertTrue(builder.fields["error_cause_cause_cause_cause_cause_message"] == null)
    }

    @Test
    fun `with Throwable value - message truncation`() {
        val builder = LoggerFieldsBuilder()
        val longMessage = "a".repeat(300)
        val exception = IllegalArgumentException(longMessage)
        builder.with("error", exception)

        val expectedMessage = "java.lang.IllegalArgumentException: ${"a".repeat(255 - "java.lang.IllegalArgumentException: ".length)}..."
        assertEquals(expectedMessage, builder.fields["error_message"])
    }

    @Test
    fun `with Throwable value - null message`() {
        val builder = LoggerFieldsBuilder()
        val exception = NullPointerException(null) // Exception with a null message
        builder.with("error", exception)

        assertEquals("java.lang.NullPointerException", builder.fields["error_message"])
    }

    @Test
    fun `chaining with calls`() {
        val builder = LoggerFieldsBuilder()
        builder.with("key1", "value1")
            .with("key2", 123)
            .with("key3", true)

        assertEquals("value1", builder.fields["key1"])
        assertEquals("123", builder.fields["key2"])
        assertEquals("true", builder.fields["key3"])
    }

    @Test
    fun `override existing field`() {
        val builder = LoggerFieldsBuilder()
        builder.with("key", "initialValue")
        builder.with("key", "newValue")
        assertEquals("newValue", builder.fields["key"])
    }

    @Test
    fun `initial fields constructor`() {
        val initialFields = mutableMapOf("initialKey" to "initialValue")
        val builder = LoggerFieldsBuilder(initialFields)
        builder.with("newKey", "newValue")

        assertEquals("initialValue", builder.fields["initialKey"])
        assertEquals("newValue", builder.fields["newKey"])
        // Ensure the original map is not modified if the builder modifies its internal map
        // (though in this implementation it uses the passed map directly)
        assertEquals("initialValue", initialFields["initialKey"])
    }

    @Test
    fun withString() {
        val lf: LoggerFields = LoggerFieldsBuilder().with("myField", "value")
        val fields = lf.fields
        assertEquals("value", fields["myField"])
        assertEquals(1, fields.size.toLong())
    }

    @Test
    fun withException() {
        val exception = Exception("value", Exception("cause"))
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        exception.printStackTrace(pw)
        val lf: LoggerFields = LoggerFieldsBuilder().with("myField", exception)
        val fields = lf.fields
        assertEquals(sw.toString(), fields["myField"])
        assertEquals("java.lang.Exception: value", fields["myField_message"])
        assertEquals("java.lang.Exception: cause", fields["myField_cause_message"])
        assertEquals(3, fields.size.toLong())
    }

    @Test
    fun withChar() {
        val lf: LoggerFields = LoggerFieldsBuilder().with("myField", 'a')
        val fields = lf.fields
        assertEquals("'a'", fields["myField"])
        assertEquals(1, fields.size.toLong())
    }

    @Test
    fun withByte() {
        val lf: LoggerFields = LoggerFieldsBuilder().with("myField", 1.toByte())
        val fields = lf.fields
        assertEquals("1", fields["myField"])
        assertEquals(1, fields.size.toLong())
    }

    @Test
    fun withShort() {
        val lf: LoggerFields = LoggerFieldsBuilder().with("myField", 1.toShort())
        val fields = lf.fields
        assertEquals("1", fields["myField"])
        assertEquals(1, fields.size.toLong())
    }

    @Test
    fun withInt() {
        val lf: LoggerFields = LoggerFieldsBuilder().with("myField", 1)
        val fields = lf.fields
        assertEquals("1", fields["myField"])
        assertEquals(1, fields.size.toLong())
    }

    @Test
    fun withLong() {
        val lf: LoggerFields = LoggerFieldsBuilder().with("myField", 1L)
        val fields = lf.fields
        assertEquals("1", fields["myField"])
        assertEquals(1, fields.size.toLong())
    }

    @Test
    fun withBoolean() {
        val lf: LoggerFields = LoggerFieldsBuilder().with("myField", true)
        val fields = lf.fields
        assertEquals("true", fields["myField"])
        assertEquals(1, fields.size.toLong())
    }

    @Test
    fun withFloat() {
        val lf: LoggerFields = LoggerFieldsBuilder().with("myField", 1.25f)
        val fields = lf.fields
        assertEquals("1.25", fields["myField"])
        assertEquals(1, fields.size.toLong())
    }

    @Test
    fun withDouble() {
        val lf: LoggerFields = LoggerFieldsBuilder().with("myField", 1.5)
        val fields = lf.fields
        assertEquals("1.5", fields["myField"])
        assertEquals(1, fields.size.toLong())
    }

    @Test
    fun withAll() {
        val exception = Exception("error value", Exception("cause"))
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        exception.printStackTrace(pw)

        val lf: LoggerFields = LoggerFieldsBuilder()
            .with("myString", "bad value")
            .with("myString", "string value")
            .with("myError", Exception("bad value", Exception("bad cause")))
            .with("myError", exception)
            .with("myChar", 'a')
            .with("myChar", 'b')
            .with("myByte", 0.toByte())
            .with("myByte", 1.toByte())
            .with("myShort", 1.toShort())
            .with("myShort", 2.toShort())
            .with("myInt", 3)
            .with("myInt", 4)
            .with("myLong", 7L)
            .with("myLong", 8L)
            .with("myBool", false)
            .with("myBool", true)
            .with("myFloat", 3.25f)
            .with("myFloat", 1.25f)
            .with("myDouble", 1.25)
            .with("myDouble", 1.5)
        val fields = lf.fields

        assertEquals(12, fields.size.toLong())
        assertEquals("string value", fields["myString"])
        assertEquals(sw.toString(), fields["myError"])
        assertEquals("java.lang.Exception: error value", fields["myError_message"])
        assertEquals("java.lang.Exception: cause", fields["myError_cause_message"])
        assertEquals("'b'", fields["myChar"])
        assertEquals("1", fields["myByte"])
        assertEquals("2", fields["myShort"])
        assertEquals("4", fields["myInt"])
        assertEquals("8", fields["myLong"])
        assertEquals("true", fields["myBool"])
        assertEquals("1.25", fields["myFloat"])
        assertEquals("1.5", fields["myDouble"])
    }
}
