package io.justtrack

import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdClient
import com.google.android.gms.appset.AppSetIdInfo
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.google.android.gms.tasks.Task as GmsTask

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppSetIdReaderTaskTest {

    private val context = RuntimeEnvironment.getApplication()

    private class RecordingLogger : Logger {
        override val fallback: Logger get() = this
        val warnings = mutableListOf<Pair<String, Throwable?>>()
        override fun debug(message: String, vararg fields: LoggerFields) = Unit
        override fun info(message: String, vararg fields: LoggerFields) = Unit
        override fun warn(message: String, vararg fields: LoggerFields) {
            warnings += message to null
        }
        override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) {
            warnings += message to exception
        }
        override fun error(message: String, vararg fields: LoggerFields) = Unit
        override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit
        override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) = Unit
    }

    /** Synchronous fake GmsTask that calls registered listeners immediately. */
    private class FakeGmsTask<T>(
        private val value: T? = null,
        private val error: Exception? = null,
    ) : GmsTask<T>() {
        override fun isComplete(): Boolean = true
        override fun isSuccessful(): Boolean = error == null
        override fun isCanceled(): Boolean = false

        @Suppress("UNCHECKED_CAST")
        override fun getResult(): T = value as T
        override fun <X : Throwable> getResult(exceptionType: Class<X>): T = result
        override fun getException(): Exception? = error

        override fun addOnSuccessListener(listener: OnSuccessListener<in T>): GmsTask<T> {
            if (error == null) listener.onSuccess(value)
            return this
        }
        override fun addOnSuccessListener(executor: java.util.concurrent.Executor, listener: OnSuccessListener<in T>): GmsTask<T> =
            addOnSuccessListener(listener)
        override fun addOnSuccessListener(activity: android.app.Activity, listener: OnSuccessListener<in T>): GmsTask<T> =
            addOnSuccessListener(listener)

        override fun addOnFailureListener(listener: OnFailureListener): GmsTask<T> {
            if (error != null) listener.onFailure(error)
            return this
        }
        override fun addOnFailureListener(executor: java.util.concurrent.Executor, listener: OnFailureListener): GmsTask<T> =
            addOnFailureListener(listener)
        override fun addOnFailureListener(activity: android.app.Activity, listener: OnFailureListener): GmsTask<T> = addOnFailureListener(listener)
    }

    private inline fun withMockedAppSet(client: AppSetIdClient, block: () -> Unit) {
        val mocked: MockedStatic<AppSet> = Mockito.mockStatic(AppSet::class.java)
        try {
            mocked.`when`<AppSetIdClient> { AppSet.getClient(any()) }.thenReturn(client)
            block()
        } finally {
            mocked.close()
        }
    }

    @Test
    fun `resumes with AppSetIdInfo on success`() = runBlocking<Unit> {
        val info = mock<AppSetIdInfo>()
        val client = mock<AppSetIdClient>()
        whenever(client.appSetIdInfo).thenReturn(FakeGmsTask(value = info))
        val logger = RecordingLogger()

        withMockedAppSet(client) {
            val result = runBlocking { AppSetIdReaderTask(context, logger).execute() }
            assertSame(info, result)
        }
        // No warnings on success path.
        assert(logger.warnings.isEmpty()) { "expected no warnings, got: ${logger.warnings}" }
    }

    @Test
    fun `resumes with null and logs warning when GMS task fails`() = runBlocking<Unit> {
        val failure = RuntimeException("gms boom")
        val client = mock<AppSetIdClient>()
        whenever(client.appSetIdInfo).thenReturn(FakeGmsTask<AppSetIdInfo>(error = failure))
        val logger = RecordingLogger()

        withMockedAppSet(client) {
            val result = runBlocking { AppSetIdReaderTask(context, logger).execute() }
            assertNull(result)
        }
        assert(logger.warnings.size == 1) { "expected exactly one warning, got: ${logger.warnings}" }
        val (msg, throwable) = logger.warnings.single()
        assert(msg == "Failed to read appSet id")
        assertSame(failure, throwable)
    }

    @Test
    fun `resumes with null and logs warning when AppSet getClient throws`() = runBlocking<Unit> {
        val mocked: MockedStatic<AppSet> = Mockito.mockStatic(AppSet::class.java)
        val logger = RecordingLogger()
        val boom = IllegalStateException("no play services")
        try {
            mocked.`when`<AppSetIdClient> { AppSet.getClient(any()) }.thenThrow(boom)

            val result = AppSetIdReaderTask(context, logger).execute()

            assertNull(result)
        } finally {
            mocked.close()
        }
        assert(logger.warnings.size == 1) { "expected exactly one warning, got: ${logger.warnings}" }
        val (msg, throwable) = logger.warnings.single()
        assert(msg == "Failed to read appSet id")
        assertSame(boom, throwable)
    }

    @Test
    fun `success listener resumes with null when AppSet returns null info`() = runBlocking<Unit> {
        val client = mock<AppSetIdClient>()
        whenever(client.appSetIdInfo).thenReturn(FakeGmsTask<AppSetIdInfo>(value = null))
        val logger = RecordingLogger()

        withMockedAppSet(client) {
            val result = runBlocking { AppSetIdReaderTask(context, logger).execute() }
            assertNull(result)
        }
    }

    @Test
    fun `client property throws is caught and resumes null`() = runBlocking<Unit> {
        val client = mock<AppSetIdClient>()
        val boom = RuntimeException("getter blew up")
        whenever(client.appSetIdInfo).thenThrow(boom)
        val logger = RecordingLogger()

        withMockedAppSet(client) {
            val result = runBlocking { AppSetIdReaderTask(context, logger).execute() }
            assertNull(result)
        }
        // Caught by the outer try/catch.
        assert(logger.warnings.any { it.first == "Failed to read appSet id" && it.second === boom }) {
            "expected warning with thrown cause, got: ${logger.warnings}"
        }
    }

    @Test
    fun `duplicate success-listener invocations resume only once`() = runBlocking<Unit> {
        val info = mock<AppSetIdInfo>()
        val task: GmsTask<AppSetIdInfo> = object : GmsTask<AppSetIdInfo>() {
            override fun isComplete() = true
            override fun isSuccessful() = true
            override fun isCanceled() = false
            override fun getResult(): AppSetIdInfo = info
            override fun <X : Throwable> getResult(exceptionType: Class<X>): AppSetIdInfo = info
            override fun getException(): Exception? = null
            override fun addOnSuccessListener(listener: OnSuccessListener<in AppSetIdInfo>): GmsTask<AppSetIdInfo> {
                // Fire success twice; AtomicBoolean must drop the second resume attempt.
                listener.onSuccess(info)
                listener.onSuccess(info)
                return this
            }
            override fun addOnSuccessListener(executor: java.util.concurrent.Executor, listener: OnSuccessListener<in AppSetIdInfo>) =
                addOnSuccessListener(listener)
            override fun addOnSuccessListener(activity: android.app.Activity, listener: OnSuccessListener<in AppSetIdInfo>) =
                addOnSuccessListener(listener)
            override fun addOnFailureListener(listener: OnFailureListener) = this
            override fun addOnFailureListener(executor: java.util.concurrent.Executor, listener: OnFailureListener) = this
            override fun addOnFailureListener(activity: android.app.Activity, listener: OnFailureListener) = this
        }
        val client = mock<AppSetIdClient>()
        whenever(client.appSetIdInfo).thenReturn(task)
        val logger = RecordingLogger()

        withMockedAppSet(client) {
            val result = runBlocking { AppSetIdReaderTask(context, logger).execute() }
            assertSame(info, result)
        }
    }

    @Test
    fun `if both success and failure listeners are invoked only the first resume wins`() = runBlocking<Unit> {
        // FakeGmsTask configured with BOTH a value AND an error: success listener fires (value path),
        // failure listener fires only when error != null. To exercise the AtomicBoolean guard properly,
        // we build a custom Task that invokes both listeners in sequence.
        val info = mock<AppSetIdInfo>()
        val task: GmsTask<AppSetIdInfo> = object : GmsTask<AppSetIdInfo>() {
            override fun isComplete() = true
            override fun isSuccessful() = true
            override fun isCanceled() = false
            override fun getResult(): AppSetIdInfo = info
            override fun <X : Throwable> getResult(exceptionType: Class<X>): AppSetIdInfo = info
            override fun getException(): Exception? = null
            private var successListener: OnSuccessListener<in AppSetIdInfo>? = null
            private var failureListener: OnFailureListener? = null
            override fun addOnSuccessListener(listener: OnSuccessListener<in AppSetIdInfo>): GmsTask<AppSetIdInfo> {
                successListener = listener
                maybeFire()
                return this
            }
            override fun addOnSuccessListener(executor: java.util.concurrent.Executor, listener: OnSuccessListener<in AppSetIdInfo>) =
                addOnSuccessListener(listener)
            override fun addOnSuccessListener(activity: android.app.Activity, listener: OnSuccessListener<in AppSetIdInfo>) =
                addOnSuccessListener(listener)
            override fun addOnFailureListener(listener: OnFailureListener): GmsTask<AppSetIdInfo> {
                failureListener = listener
                maybeFire()
                return this
            }
            override fun addOnFailureListener(executor: java.util.concurrent.Executor, listener: OnFailureListener) = addOnFailureListener(listener)
            override fun addOnFailureListener(activity: android.app.Activity, listener: OnFailureListener) = addOnFailureListener(listener)
            private fun maybeFire() {
                val s = successListener
                val f = failureListener
                if (s != null && f != null) {
                    // Fire success first, then a stale failure: AtomicBoolean must prevent double resume.
                    s.onSuccess(info)
                    f.onFailure(RuntimeException("late failure"))
                }
            }
        }
        val client = mock<AppSetIdClient>()
        whenever(client.appSetIdInfo).thenReturn(task)
        val logger = RecordingLogger()

        withMockedAppSet(client) {
            val result = runBlocking { AppSetIdReaderTask(context, logger).execute() }
            assertSame(info, result)
        }
        // The late failure listener still logs (logger.warn precedes the AtomicBoolean check), but no
        // second `continuation.resume(...)` happens — coroutine resumed exactly once with `info`.
        verify(client).appSetIdInfo
    }
}
