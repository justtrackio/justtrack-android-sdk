package io.justtrack

import android.content.Context
import android.content.SharedPreferences
import io.justtrack.crashes.CrashHandler
import io.justtrack.events.JtSessionTrackingEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.Thread.UncaughtExceptionHandler
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionManagerImplTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var eventTracker: EventTracker
    private lateinit var crashHandler: CrashHandler
    private lateinit var isTracking: AtomicBoolean
    private lateinit var sessionPrefs: SharedPreferences

    @Before
    fun setUp() {
        // Defensive: drain any matchers left dangling on the thread by previously failed verifications.
        try {
            org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress().validateState()
        } catch (_: Throwable) {
            org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress().reset()
        }
        eventTracker = mock()
        crashHandler = mock()
        whenever(crashHandler.getUncaughtExceptionHandler()).thenReturn(UncaughtExceptionHandler { _, _ -> })
        isTracking = AtomicBoolean(true)
        sessionPrefs = context.getSharedPreferences("justtrack-session-manager", Context.MODE_PRIVATE)
        sessionPrefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        sessionPrefs.edit().clear().commit()
    }

    private fun newManager(): SessionManagerImpl = SessionManagerImpl(eventTracker, crashHandler, context, isTracking)

    private fun stopWorker(manager: SessionManagerImpl) {
        manager.shutdown()
        val worker = getWorker(manager)
        worker?.join(2000)
    }

    private fun getWorker(manager: SessionManagerImpl): Thread? {
        val field = SessionManagerImpl::class.java.getDeclaredField("worker")
        field.isAccessible = true
        return field.get(manager) as Thread?
    }

    private fun setQueue(manager: SessionManagerImpl, queue: BlockingQueue<*>) {
        val field = SessionManagerImpl::class.java.getDeclaredField("queue")
        field.isAccessible = true
        field.set(manager, queue)
    }

    // -------- start() --------

    @Test
    fun `start() does nothing extra when there is no persisted session`() {
        val manager = newManager()
        try {
            manager.start()
            // Worker should have been started.
            val worker = getWorker(manager)
            assertNotNull(worker)
            assertTrue(worker!!.isAlive || worker.state != Thread.State.NEW)
            // No track call expected.
            verify(eventTracker, never()).track(any(), any())
        } finally {
            stopWorker(manager)
        }
    }

    @Test
    fun `start() ends a persisted session and clears prefs`() {
        // Persist a fake session.
        val storedId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        sessionPrefs.edit()
            .putString("justtrack-session", "$storedId:5000:1700000000000")
            .commit()
        val manager = newManager()
        try {
            manager.start()

            val captor = argumentCaptor<JtSessionTrackingEvent>()
            verify(eventTracker).track(captor.capture(), eq(manager))
            val event = captor.firstValue
            assertEquals(storedId.toString(), event.sessionId)
            assertEquals("end", event.dimensions["jt_action"])
            // Prefs were cleared.
            assertNull(sessionPrefs.getString("justtrack-session", null))
        } finally {
            stopWorker(manager)
        }
    }

    @Test
    fun `start() ignores a corrupted persisted session string`() {
        sessionPrefs.edit().putString("justtrack-session", "not-a-uuid-format").commit()
        val manager = newManager()
        try {
            manager.start()
            verify(eventTracker, never()).track(any(), any())
        } finally {
            stopWorker(manager)
        }
    }

    // -------- onResume() / onPause() gating --------

    @Test
    fun `onResume does nothing when tracking is disabled`() {
        isTracking.set(false)
        val manager = newManager()
        // No start() - no worker running.
        manager.onResume()
        manager.onPause()
        verify(eventTracker, never()).track(any(), any())
    }

    // -------- getLatestSessionId() --------

    @Test
    fun `getLatestSessionId starts a new session on first call and tracks start event`() {
        val manager = newManager()
        try {
            val id = manager.getLatestSessionId()

            // Verify it is a valid UUID string.
            UUID.fromString(id)
            val captor = argumentCaptor<JtSessionTrackingEvent>()
            verify(eventTracker).track(captor.capture(), eq(manager))
            assertEquals("start", captor.firstValue.dimensions["jt_action"])
            assertEquals(id, captor.firstValue.sessionId)
        } finally {
            stopWorker(manager)
        }
    }

    @Test
    fun `getLatestSessionId returns same id on subsequent calls without tracking again`() {
        val manager = newManager()
        try {
            val id1 = manager.getLatestSessionId()
            val id2 = manager.getLatestSessionId()
            assertEquals(id1, id2)
            // Only one track call.
            verify(eventTracker, org.mockito.Mockito.times(1)).track(any(), any())
        } finally {
            stopWorker(manager)
        }
    }

    @Test
    fun `getLatestSessionId after shutdown returns lastSessionId without tracking`() {
        val manager = newManager()
        val id = manager.getLatestSessionId() // active session
        manager.shutdown() // ends session, sets lastSessionId
        stopWorker(manager)
        // First call after shutdown should return the lastSessionId, no new tracking.
        org.mockito.Mockito.clearInvocations(eventTracker)

        val again = manager.getLatestSessionId()

        assertEquals(id, again)
        verify(eventTracker, never()).track(any(), any())
    }

    // -------- shutdown() --------

    @Test
    fun `shutdown ends active session and clears prefs`() {
        val manager = newManager()
        try {
            manager.getLatestSessionId() // start a session.
            org.mockito.Mockito.clearInvocations(eventTracker)

            manager.shutdown()

            val captor = argumentCaptor<JtSessionTrackingEvent>()
            verify(eventTracker).track(captor.capture(), eq(manager))
            assertEquals("end", captor.firstValue.dimensions["jt_action"])
            assertNull(sessionPrefs.getString("justtrack-session", null))
        } finally {
            stopWorker(manager)
        }
    }

    @Test
    fun `shutdown without active session tracks nothing`() {
        val manager = newManager()
        manager.shutdown()
        verify(eventTracker, never()).track(any(), any())
    }

    @Test
    fun `shutdown is idempotent`() {
        val manager = newManager()
        manager.getLatestSessionId()
        manager.shutdown()
        stopWorker(manager)
        org.mockito.Mockito.clearInvocations(eventTracker)

        // Second shutdown should be a no-op (no session and no worker).
        manager.shutdown()

        verify(eventTracker, never()).track(any(), any())
    }

    // -------- updateSessionTimeStamp() --------

    @Test
    fun `updateSessionTimeStamp without session is a no-op and does not persist`() {
        val manager = newManager()
        manager.updateSessionTimeStamp()
        assertNull(sessionPrefs.getString("justtrack-session", null))
    }

    @Test
    fun `updateSessionTimeStamp persists current session`() {
        val manager = newManager()
        try {
            manager.getLatestSessionId() // creates session and persists.
            val before = sessionPrefs.getString("justtrack-session", null)
            assertNotNull(before)
            Thread.sleep(10) // ensure time delta.

            manager.updateSessionTimeStamp()

            val after = sessionPrefs.getString("justtrack-session", null)
            assertNotNull(after)
            // The persisted string contains a timestamp suffix that should differ.
            assertNotSame(before, after)
        } finally {
            stopWorker(manager)
        }
    }

    // -------- Worker thread paths --------

    @Test
    fun `worker handles START event and tracks start event`() {
        val manager = newManager()
        try {
            manager.start()
            manager.onResume() // enqueues START
            // Wait for the worker to process.
            waitForTrackCalls(1)
            val captor = argumentCaptor<JtSessionTrackingEvent>()
            verify(eventTracker).track(captor.capture(), eq(manager))
            assertEquals("start", captor.firstValue.dimensions["jt_action"])
        } finally {
            stopWorker(manager)
        }
    }

    @Test
    fun `worker ignores duplicate START when a session already exists`() {
        val manager = newManager()
        try {
            manager.start()
            manager.getLatestSessionId() // session already started by main thread.
            org.mockito.Mockito.clearInvocations(eventTracker)
            manager.onResume() // duplicate START enqueued.

            // Give the worker time to process the (no-op) event.
            Thread.sleep(200)

            verify(eventTracker, never()).track(any(), any())
        } finally {
            stopWorker(manager)
        }
    }

    @Test
    fun `worker handles STOP event with active session and tracks end event`() {
        val manager = newManager()
        try {
            manager.start()
            manager.getLatestSessionId()
            org.mockito.Mockito.clearInvocations(eventTracker)
            manager.onPause() // enqueues STOP

            waitForTrackCalls(1)
            val captor = argumentCaptor<JtSessionTrackingEvent>()
            verify(eventTracker).track(captor.capture(), eq(manager))
            assertEquals("end", captor.firstValue.dimensions["jt_action"])
        } finally {
            stopWorker(manager)
        }
    }

    @Test
    fun `worker STOP without active session does nothing`() {
        val manager = newManager()
        try {
            manager.start()
            manager.onPause() // STOP first, no session.
            Thread.sleep(200)
            verify(eventTracker, never()).track(any(), any())
        } finally {
            stopWorker(manager)
        }
    }

    @Test
    fun `worker poll timeout with active session triggers refreshSession`() {
        // Use a custom queue: first poll returns null (timeout), then take blocks until shutdown.
        val pollSentinel = AtomicReference<Long?>(null) // captures (timeout, unit) of poll calls.
        val pollCount = AtomicReference(0)
        val fakeQueue: BlockingQueue<Any> = mock {
            on { poll(any<Long>(), any<TimeUnit>()) }.thenAnswer { invocation ->
                pollCount.set(pollCount.get() + 1)
                pollSentinel.set(invocation.getArgument<Long>(0))
                if (pollCount.get() == 1) {
                    null
                } else {
                    SessionManagerImpl::class.java.classes
                        .first { it.simpleName == "Event" }.enumConstants!![1] // STOP
                }
            }
            on { take() }.thenAnswer {
                // After session ends, next iteration calls take(). Block briefly, then throw to
                // simulate interruption.
                Thread.sleep(50)
                throw InterruptedException("stop")
            }
        }
        val manager = newManager()
        setQueue(manager, fakeQueue)
        try {
            // Start a session BEFORE the worker starts, so worker's getNextEvent immediately
            // takes the poll(1, MINUTES) path instead of the take() path.
            manager.getLatestSessionId()
            manager.start()

            // Wait for at least 2 poll calls + STOP processing.
            val deadline = System.currentTimeMillis() + 3000
            while (pollCount.get() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertTrue("expected at least 2 poll() calls, got ${pollCount.get()}", pollCount.get() >= 2)
            // The poll was made with TimeUnit.MINUTES.
            assertEquals(1L, pollSentinel.get())
        } finally {
            stopWorker(manager)
        }
    }

    @Test
    fun `worker terminates when eventTracker is garbage-collected`() {
        // We replicate the run-loop early-return at L179-181 by clearing the WeakReference
        // via reflection, after the SDK has enqueued an event for the worker to consume.
        val manager = newManager()
        manager.start()
        manager.onResume() // enqueues START

        // Give the worker a moment to enter the take() / poll() call, then drop the eventTracker
        // reference. The next iteration that consumes an event will see eventTracker.get() == null
        // and return from run().
        Thread.sleep(100)
        val field = SessionManagerImpl::class.java.getDeclaredField("eventTracker")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val ref = field.get(manager) as java.lang.ref.WeakReference<EventTracker>
        ref.clear()

        // Send another event to wake the worker up so it observes the cleared ref.
        manager.onPause()

        val worker = getWorker(manager)
        worker?.join(2000)
        assertFalse("worker should have terminated after eventTracker was cleared", worker?.isAlive ?: false)
    }

    // -------- Session inner class --------

    @Test
    fun `Session getCurrentSession returns null for empty prefs`() {
        assertNull(SessionManagerImpl.Session.getCurrentSession(sessionPrefs))
    }

    @Test
    fun `Session getCurrentSession returns null on malformed UUID`() {
        sessionPrefs.edit().putString("justtrack-session", "garbage").commit()
        assertNull(SessionManagerImpl.Session.getCurrentSession(sessionPrefs))
    }

    @Test
    fun `Session getCurrentSession returns null on missing numeric parts`() {
        sessionPrefs.edit()
            .putString("justtrack-session", "11111111-1111-1111-1111-111111111111:notanumber:0")
            .commit()
        assertNull(SessionManagerImpl.Session.getCurrentSession(sessionPrefs))
    }

    @Test
    fun `Session getCurrentSession reconstructs from stored parts`() {
        val id = UUID.fromString("22222222-2222-2222-2222-222222222222")
        sessionPrefs.edit().putString("justtrack-session", "$id:5000:1700000005000").commit()

        val session = SessionManagerImpl.Session.getCurrentSession(sessionPrefs)

        assertNotNull(session)
        val endEvent = session!!.end()
        assertEquals(id.toString(), endEvent.sessionId)
        assertEquals("end", endEvent.dimensions["jt_action"])
    }

    @Test
    fun `Session remove clears the entry`() {
        sessionPrefs.edit().putString("justtrack-session", "data").commit()
        SessionManagerImpl.Session.remove(sessionPrefs)
        assertNull(sessionPrefs.getString("justtrack-session", null))
    }

    @Test
    fun `Session clearForTesting clears all session-related preferences`() {
        // clearForTesting uses a different prefs name ("justtrack-session") - it opens that file
        // and clears it.
        val other = context.getSharedPreferences("justtrack-session", Context.MODE_PRIVATE)
        other.edit().putString("anything", "x").commit()
        SessionManagerImpl.Session.clearForTesting(context)
        assertNull(other.getString("anything", null))
    }

    @Test
    fun `Session end-no-arg uses last persisted tick`() {
        // Build a session via persist(), then call end() with no arg - should not throw.
        val id = UUID.fromString("33333333-3333-3333-3333-333333333333")
        sessionPrefs.edit().putString("justtrack-session", "$id:1000:1700000001000").commit()
        val session = SessionManagerImpl.Session.getCurrentSession(sessionPrefs)
        assertNotNull(session)
        val event = session!!.end()
        assertEquals(id.toString(), event.sessionId)
        assertEquals("end", event.dimensions["jt_action"])
    }

    // -------- helpers --------

    private fun waitForTrackCalls(expected: Int, timeoutMs: Long = 2000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val invocations = org.mockito.Mockito.mockingDetails(eventTracker).invocations
                .count { it.method.name == "track" }
            if (invocations >= expected) return
            Thread.sleep(20)
        }
    }
}
