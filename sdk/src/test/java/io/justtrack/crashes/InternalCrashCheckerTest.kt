package io.justtrack.crashes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalCrashCheckerTest {

    // region null / empty stacktrace

    @Test
    fun `isInternalCrash returns true when stacktrace is null`() {
        assertTrue(InternalCrashChecker.isInternalCrash(null))
    }

    @Test
    fun `isInternalCrash returns true when stacktrace is empty string`() {
        assertTrue(InternalCrashChecker.isInternalCrash(""))
    }

    // endregion

    // region io.justtrack package (JVM crashes)

    @Test
    fun `isInternalCrash returns true when stacktrace contains io_justtrack package`() {
        assertTrue(
            InternalCrashChecker.isInternalCrash(
                "io.justtrack.SomeClass.someMethod(SomeClass.kt:42)",
            ),
        )
    }

    @Test
    fun `isInternalCrash returns true when io_justtrack appears anywhere in stacktrace`() {
        val stacktrace = """
            com.external.LibClass.doSomething(LibClass.java:10)
            io.justtrack.sdk.InternalClass.crash(InternalClass.kt:5)
            android.app.ActivityThread.main(ActivityThread.java:7)
        """.trimIndent()
        assertTrue(InternalCrashChecker.isInternalCrash(stacktrace))
    }

    // endregion

    // region io_justtrack package (native crashes)

    @Test
    fun `isInternalCrash returns true when stacktrace contains io_justtrack (native format)`() {
        assertTrue(
            InternalCrashChecker.isInternalCrash(
                "io_justtrack_SomeNativeClass_nativeMethod",
            ),
        )
    }

    @Test
    fun `isInternalCrash returns true when io_justtrack appears anywhere in native stacktrace`() {
        val stacktrace = """
            #00 pc 0x000abc  /system/lib/libc.so
            #01 pc 0x001234  /data/app/com.example/lib/arm64/libapp.so (io_justtrack_crash_handler+0x20)
        """.trimIndent()
        assertTrue(InternalCrashChecker.isInternalCrash(stacktrace))
    }

    // endregion

    // region external crashes (should be dropped)

    @Test
    fun `isInternalCrash returns false when stacktrace is from external package only`() {
        assertFalse(
            InternalCrashChecker.isInternalCrash(
                "com.external.ThirdPartyLib.method(ThirdPartyLib.java:100)",
            ),
        )
    }

    @Test
    fun `isInternalCrash returns false for android framework stacktrace without justtrack`() {
        val stacktrace = """
            android.view.View.performClick(View.java:7441)
            android.view.View.performClickInternal(View.java:7418)
            android.os.Handler.handleCallback(Handler.java:938)
        """.trimIndent()
        assertFalse(InternalCrashChecker.isInternalCrash(stacktrace))
    }

    @Test
    fun `isInternalCrash returns false for stacktrace containing only whitespace`() {
        // non-empty but contains no justtrack identifier
        assertFalse(InternalCrashChecker.isInternalCrash("   "))
    }

    // endregion
}
