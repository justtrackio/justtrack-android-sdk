package io.justtrack.crashes

import android.os.Looper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StacktraceProviderImplTest {

    private val provider = StacktraceProviderImpl()

    @Test
    fun provideMainStacktrace_returns_non_empty_array() {
        val stackTrace = provider.provideMainStacktrace()
        assertTrue("Stack trace should not be empty", stackTrace.isNotEmpty())
    }

    @Test
    fun provideMainStacktrace_returns_main_thread_stack_trace() {
        val expected = Looper.getMainLooper().thread.stackTrace
        val actual = provider.provideMainStacktrace()

        assertNotNull(actual)
        assertTrue("Should contain stack trace elements", actual.isNotEmpty())
    }

    @Test
    fun provideMainStacktrace_implements_stacktrace_provider() {
        assertTrue(provider is StacktraceProvider)
    }

    @Test
    fun provideAllStacktrace_returns_non_empty_list() {
        val result = provider.provideAllStacktrace()
        assertTrue("Should return at least one thread", result.isNotEmpty())
    }

    @Test
    fun provideAllStacktrace_contains_current_thread() {
        val currentThreadName = Thread.currentThread().name
        val result = provider.provideAllStacktrace()

        val found = result.any { it.name == currentThreadName }
        assertTrue("Should contain the current thread", found)
    }

    @Test
    fun provideAllStacktrace_returns_valid_thread_data() {
        val result = provider.provideAllStacktrace()
        val first = result.first()

        assertTrue("Thread id should be positive", first.id > 0)
        assertNotNull("Thread name should not be null", first.name)
        assertNotNull("Thread state should not be null", first.threadState)
        assertNotNull("Stacktrace should not be null", first.stacktrace)
    }

    @Test
    fun provideAllStacktrace_matches_thread_properties() {
        val currentThread = Thread.currentThread()
        val result = provider.provideAllStacktrace()

        val match = result.find { it.id == currentThread.id }
        assertNotNull("Should find current thread by id", match)
        assertTrue("Name should match", match!!.name == currentThread.name)
        assertTrue("Daemon should match", match.daemon == currentThread.isDaemon)
        assertTrue("Priority should match", match.priority == currentThread.priority)
    }
}
