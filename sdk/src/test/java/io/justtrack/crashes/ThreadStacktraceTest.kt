package io.justtrack.crashes

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadStacktraceTest {

    @Test
    fun `constructor sets all properties correctly`() {
        val stacktrace = arrayOf(
            StackTraceElement("com.example.Foo", "bar", "Foo.kt", 10),
        )
        val thread = ThreadStacktrace(
            id = 42L,
            name = "TestThread",
            daemon = true,
            priority = 7,
            threadState = Thread.State.BLOCKED,
            stacktrace = stacktrace,
        )

        assertEquals(42L, thread.id)
        assertEquals("TestThread", thread.name)
        assertTrue(thread.daemon)
        assertEquals(7, thread.priority)
        assertEquals(Thread.State.BLOCKED, thread.threadState)
        assertArrayEquals(stacktrace, thread.stacktrace)
    }

    @Test
    fun `non-daemon thread`() {
        val thread = ThreadStacktrace(
            id = 1L,
            name = "Worker",
            daemon = false,
            priority = 5,
            threadState = Thread.State.RUNNABLE,
            stacktrace = emptyArray(),
        )

        assertFalse(thread.daemon)
    }

    @Test
    fun `data class equality works on same values`() {
        val trace = emptyArray<StackTraceElement>()
        val a = ThreadStacktrace(1L, "A", false, 5, Thread.State.NEW, trace)
        val b = ThreadStacktrace(1L, "A", false, 5, Thread.State.NEW, trace)

        // Data class equals uses referential equality for arrays, so same ref = equal
        assertEquals(a, b)
    }

    @Test
    fun `data class copy works`() {
        val original = ThreadStacktrace(1L, "Original", false, 5, Thread.State.WAITING, emptyArray())
        val copy = original.copy(name = "Copy", daemon = true)

        assertEquals("Copy", copy.name)
        assertTrue(copy.daemon)
        assertEquals(original.id, copy.id)
        assertEquals(original.priority, copy.priority)
        assertEquals(original.threadState, copy.threadState)
    }

    @Test
    fun `all thread states are supported`() {
        for (state in Thread.State.values()) {
            val thread = ThreadStacktrace(1L, "T", false, 5, state, emptyArray())
            assertEquals(state, thread.threadState)
        }
    }
}
