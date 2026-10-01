package io.justtrack

import android.content.Context
import android.os.Bundle
import android.os.RemoteException
import io.justtrack.installreferrer.api.InstallReferrerClient
import io.justtrack.installreferrer.api.InstallReferrerStateListener
import io.justtrack.installreferrer.api.ReferrerDetails
import io.justtrack.log.LoggerFields
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InstallReferrerReaderTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private class RecordingHttpLogger : HttpLogger {
        val warnings = mutableListOf<Pair<String, Throwable?>>()
        val debugs = mutableListOf<String>()
        override val fallback: io.justtrack.log.Logger get() = this
        override fun setAdvertiserId(advertiserId: String) = Unit
        override fun setUser(userId: AsyncFuture<java.util.UUID?>, installInstanceId: AsyncFuture<String?>) = Unit
        override fun setUser(userId: java.util.UUID?, installId: String) = Unit
        override fun sendToServer() = Unit
        override fun setBreadCrumbReporter(reporter: BreadCrumbReporter?) = Unit
        override fun close() = Unit
        override fun debug(message: String, vararg fields: LoggerFields) {
            debugs += message
        }
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

    private fun newBundle(referrer: String = "utm_source=test"): Bundle {
        val b = Bundle()
        b.putString("install_referrer", referrer)
        b.putLong("referrer_click_timestamp_seconds", 1L)
        b.putLong("install_begin_timestamp_seconds", 2L)
        return b
    }

    /** Stubs the static newBuilder so that `InstallReferrerClient.newBuilder(context).build()` returns [client]. */
    private inline fun <T> withMockedClient(client: InstallReferrerClient, block: () -> T): T {
        val builder: InstallReferrerClient.Builder = mock {
            on { build() } doReturn client
        }
        val mocked: MockedStatic<InstallReferrerClient> = Mockito.mockStatic(InstallReferrerClient::class.java)
        try {
            mocked.`when`<InstallReferrerClient.Builder> { InstallReferrerClient.newBuilder(any()) }.thenReturn(builder)
            return block()
        } finally {
            mocked.close()
        }
    }

    @Test
    fun `bundle path returns ReferrerDetails directly without touching client`() = runBlocking<Unit> {
        val logger = RecordingHttpLogger()
        val bundle = newBundle("utm_source=cached")
        val reader = InstallReferrerReader(context, bundle, logger)

        // No mockStatic — we should never call into InstallReferrerClient.
        val result = reader.newTask().execute()

        assertNotNull(result)
        assertEquals("utm_source=cached", result!!.installReferrer)
        // No log lines on the bundle path.
        assertTrue("expected no debug logs, got: ${logger.debugs}", logger.debugs.isEmpty())
        assertTrue("expected no warnings, got: ${logger.warnings}", logger.warnings.isEmpty())
    }

    @Test
    fun `OK response returns referrer details from client and ends connection`() = runBlocking<Unit> {
        val logger = RecordingHttpLogger()
        val details = ReferrerDetails(newBundle("utm_source=play"))
        val client: InstallReferrerClient = mock {
            on { installReferrer } doReturn details
            on { isReady } doReturn true
        }
        val listenerCaptor = argumentCaptor<InstallReferrerStateListener>()
        doAnswer {
            // Drive the listener as the real service would.
            listenerCaptor.firstValue.onInstallReferrerSetupFinished(InstallReferrerClient.InstallReferrerResponse.OK)
            null
        }.whenever(client).startConnection(listenerCaptor.capture())

        val result = withResult(logger) { reader ->
            withMockedClient(client) {
                runBlocking { reader.newTask().execute() }
            }
        }
        assertSame(details, result)
        verify(client).endConnection()
        assertTrue(
            "expected success debug log, got: ${logger.debugs}",
            logger.debugs.any { it == "Retrieved Install Referrer" },
        )
    }

    @Test
    fun `non-OK response returns null and skips endConnection when client is not ready`() = runBlocking<Unit> {
        val logger = RecordingHttpLogger()
        val client: InstallReferrerClient = mock {
            on { isReady } doReturn false
        }
        val captor = argumentCaptor<InstallReferrerStateListener>()
        doAnswer {
            captor.firstValue.onInstallReferrerSetupFinished(
                InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE,
            )
            null
        }.whenever(client).startConnection(captor.capture())

        val result = withResult(logger) { reader ->
            withMockedClient(client) {
                runBlocking { reader.newTask().execute() }
            }
        }
        assertNull(result)
        verify(client, Mockito.never()).endConnection()
        // Failure-debug log emitted.
        assertTrue(
            "expected failure debug log, got: ${logger.debugs}",
            logger.debugs.any { it.contains("Failed to retrieve Install Referrer") },
        )
    }

    @Test
    fun `RemoteException from getInstallReferrer is wrapped and rethrown after endConnection`() = runBlocking<Unit> {
        val logger = RecordingHttpLogger()
        val boom = RemoteException("service down")
        val client: InstallReferrerClient = mock {
            on { installReferrer } doThrow boom
            on { isReady } doReturn true
        }
        val captor = argumentCaptor<InstallReferrerStateListener>()
        doAnswer {
            captor.firstValue.onInstallReferrerSetupFinished(InstallReferrerClient.InstallReferrerResponse.OK)
            null
        }.whenever(client).startConnection(captor.capture())

        try {
            withResult(logger) { reader ->
                withMockedClient(client) {
                    runBlocking { reader.newTask().execute() }
                }
            }
            fail("expected RemoteException to propagate")
        } catch (e: RemoteException) {
            assertSame(boom, e)
        }
        verify(client).endConnection()
        assertTrue(
            "expected error log with cause",
            logger.warnings.any { it.first == "Failed to retrieve Install Referrer with error" && it.second === boom },
        )
    }

    @Test
    fun `RemoteException - endConnection IllegalArgumentException is suppressed and logged`() = runBlocking<Unit> {
        val logger = RecordingHttpLogger()
        val remote = RemoteException("svc down")
        val endProblem = IllegalArgumentException("already closed")
        val client: InstallReferrerClient = mock {
            on { installReferrer } doThrow remote
            on { isReady } doReturn true
            on { endConnection() } doThrow endProblem
        }
        val captor = argumentCaptor<InstallReferrerStateListener>()
        doAnswer {
            captor.firstValue.onInstallReferrerSetupFinished(InstallReferrerClient.InstallReferrerResponse.OK)
            null
        }.whenever(client).startConnection(captor.capture())

        try {
            withResult(logger) { reader ->
                withMockedClient(client) {
                    runBlocking { reader.newTask().execute() }
                }
            }
            fail("expected RemoteException to propagate")
        } catch (e: RemoteException) {
            assertSame(remote, e)
            assertTrue("endConnection problem must be suppressed", e.suppressed.toList().contains(endProblem))
        }
        assertTrue(
            "expected endConnection warning",
            logger.warnings.any {
                it.first == "InstallReferrer: Unable to endConnection, the connection is probably closed already" &&
                    it.second === endProblem
            },
        )
    }

    @Test
    fun `IllegalArgumentException from getInstallReferrer is rethrown`() = runBlocking<Unit> {
        val logger = RecordingHttpLogger()
        val boom = IllegalArgumentException("bad state")
        val client: InstallReferrerClient = mock {
            on { installReferrer } doThrow boom
            // isReady is not consulted on this path because the IAE is thrown before the isReady check.
        }
        val captor = argumentCaptor<InstallReferrerStateListener>()
        doAnswer {
            captor.firstValue.onInstallReferrerSetupFinished(InstallReferrerClient.InstallReferrerResponse.OK)
            null
        }.whenever(client).startConnection(captor.capture())

        try {
            withResult(logger) { reader ->
                withMockedClient(client) {
                    runBlocking { reader.newTask().execute() }
                }
            }
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertSame(boom, e)
        }
        assertTrue(
            logger.warnings.any { it.first == "Failed to retrieve Install Referrer with error" && it.second === boom },
        )
    }

    @Test
    fun `service-disconnected listener resumes with null and ends connection`() = runBlocking<Unit> {
        val logger = RecordingHttpLogger()
        val client: InstallReferrerClient = mock()
        val captor = argumentCaptor<InstallReferrerStateListener>()
        doAnswer {
            captor.firstValue.onInstallReferrerServiceDisconnected()
            null
        }.whenever(client).startConnection(captor.capture())

        val result = withResult(logger) { reader ->
            withMockedClient(client) {
                runBlocking { reader.newTask().execute() }
            }
        }
        assertNull(result)
        verify(client).endConnection()
    }

    @Test
    fun `service-disconnected with endConnection failure is logged and still resumes null`() = runBlocking<Unit> {
        val logger = RecordingHttpLogger()
        val endProblem = IllegalArgumentException("closed")
        val client: InstallReferrerClient = mock {
            on { endConnection() } doThrow endProblem
        }
        val captor = argumentCaptor<InstallReferrerStateListener>()
        doAnswer {
            captor.firstValue.onInstallReferrerServiceDisconnected()
            null
        }.whenever(client).startConnection(captor.capture())

        val result = withResult(logger) { reader ->
            withMockedClient(client) {
                runBlocking { reader.newTask().execute() }
            }
        }
        assertNull(result)
        assertTrue(
            logger.warnings.any {
                it.first == "InstallReferrer: Unable to endConnection, the connection is probably closed already" &&
                    it.second === endProblem
            },
        )
    }

    @Test
    fun `duplicate service-disconnected callbacks resume only once`() = runBlocking<Unit> {
        val logger = RecordingHttpLogger()
        val client: InstallReferrerClient = mock()
        val captor = argumentCaptor<InstallReferrerStateListener>()
        doAnswer {
            val l = captor.firstValue
            l.onInstallReferrerServiceDisconnected()
            l.onInstallReferrerServiceDisconnected() // second call must be a no-op for resume.
            null
        }.whenever(client).startConnection(captor.capture())

        val result = withResult(logger) { reader ->
            withMockedClient(client) {
                runBlocking { reader.newTask().execute() }
            }
        }
        assertNull(result)
        // endConnection called twice (no guard around it), but resume happens at most once - if it
        // happened twice, the coroutine would have thrown IllegalStateException and result would not be returned.
        verify(client, Mockito.times(2)).endConnection()
    }

    @Test
    fun `late disconnect after setup-finished does not double-resume`() = runBlocking<Unit> {
        val logger = RecordingHttpLogger()
        val details = ReferrerDetails(newBundle())
        val client: InstallReferrerClient = mock {
            on { installReferrer } doReturn details
            on { isReady } doReturn true
        }
        val captor = argumentCaptor<InstallReferrerStateListener>()
        doAnswer {
            val l = captor.firstValue
            l.onInstallReferrerSetupFinished(InstallReferrerClient.InstallReferrerResponse.OK)
            // Synchronously call disconnected after setup-finished. The setup-finished branch
            // launches an IO coroutine, so the disconnected branch may actually beat it on a
            // different thread. Either way, AtomicBoolean must ensure exactly one resume.
            l.onInstallReferrerServiceDisconnected()
            null
        }.whenever(client).startConnection(captor.capture())

        val result = withResult(logger) { reader ->
            withMockedClient(client) {
                runBlocking { reader.newTask().execute() }
            }
        }
        // The result depends on the race: either `details` (setup-finished won) or `null`
        // (disconnected won). Both are valid outcomes; the key invariant is "no double resume".
        assertTrue(
            "expected either details or null, got: $result",
            result == null || result === details,
        )
    }

    /** Tiny helper that wires up the reader and returns the awaited value. */
    private fun <T> withResult(logger: RecordingHttpLogger = RecordingHttpLogger(), block: (InstallReferrerReader) -> T): T {
        val reader = InstallReferrerReader(context, null, logger)
        return block(reader)
    }
}
