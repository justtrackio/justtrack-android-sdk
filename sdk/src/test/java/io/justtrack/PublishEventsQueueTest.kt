package io.justtrack

import android.database.sqlite.SQLiteException
import io.justtrack.events.JtSessionTrackingEvent
import io.justtrack.events.PublishEventTaskExecutor
import io.justtrack.events.Unit
import io.justtrack.exceptions.SdkNotTrackingException
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.versions.SdkVersion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.SQLException
import java.util.Date
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext

internal class PublishEventsQueueTest {
    private val sdkVersion = TestSdkVersion()

    @Test
    fun track_returnsNotTrackingErrorWhenTrackingDisabled() {
        val queue = newQueue(isTracking = AtomicBoolean(false))

        val future = queue.track(AppEvent("valid_event"), TestSessionManager())

        assertFutureFailsWith(future, SdkNotTrackingException::class.java)
    }

    @Test
    fun track_returnsInvalidFieldErrorForInvalidEvent() {
        val queue = newQueue()

        val future = queue.track(AppEvent(""), TestSessionManager())

        assertFutureFailsWith(future, io.justtrack.exceptions.InvalidFieldException::class.java)
    }

    @Test
    fun publishEvent_returnsShutdownErrorAfterClose() {
        val queue = newQueue()
        queue.close()

        val future = queue.publishEvent(testPublishableEvent("closed_event"))

        assertFutureFailsWith(future, IllegalStateException::class.java)
    }

    @Test
    fun publishEvent_returnsQueueFullErrorWhenOfferFails() {
        val queue = newQueue()

        @Suppress("UNCHECKED_CAST")
        val eventQueue = queue.getPrivateField("eventQueue") as LinkedBlockingQueue<EventQueueMessage>
        while (eventQueue.poll() != null) {
            // Drop the initial retry message.
        }
        val rejectingQueue = object : LinkedBlockingQueue<EventQueueMessage>() {
            override fun offer(e: EventQueueMessage): Boolean = false
        }
        queue.setPrivateField("eventQueue", rejectingQueue)

        val future = queue.publishEvent(testPublishableEvent("full_queue_event"))

        assertFutureFailsWith(future, IllegalStateException::class.java)
    }

    @Test
    fun defaultRunDelayAndMaxBatchGetterAreCovered() {
        val queue = PublishEventsQueue(
            PublishEventTaskExecutor { events, _ -> ValueFuture(events) },
            TestLogger(),
            NetworkErrorLogger(),
            FakeEventRepository(),
            isTracking = AtomicBoolean(true),
            sdkVersion = sdkVersion,
            globalDimensionsRepo = GlobalDimensionsRepo(EmptySharedPreferences()),
            connectivityProvider = CapturingConnectivityProvider(),
        )

        assertEquals(100, queue.maxBatchSize)
    }

    @Test
    fun coroutineExceptionHandlerDelegatesToHandleException() {
        val logger = CapturingLogger()
        val queue = newQueue(logger = logger)

        @Suppress("UNCHECKED_CAST")
        val handler = queue.getPrivateField("coroutineCancelExceptionHandler") as kotlinx.coroutines.CoroutineExceptionHandler

        handler.handleException(EmptyCoroutineContext, kotlinx.coroutines.CancellationException("cancelled"))

        assertTrue(logger.warnMessages.any { it.contains("PublishEventQueue coroutine cancelled") })
    }

    @Test
    fun runTaskCatchesTaskExceptions() {
        val logger = CapturingLogger()
        val queue = newQueue(logger = logger)
        val throwingQueue = object : LinkedBlockingQueue<EventQueueMessage>() {
            override fun take(): EventQueueMessage {
                error("take failed")
            }
        }
        queue.setPrivateField("eventQueue", throwingQueue)

        queue.start(null)
        waitUntil { logger.warnMessages.any { it.contains("failed to run queue task") } }
        @Suppress("UNCHECKED_CAST")
        (queue.getPrivateField("eventChannel") as Channel<EventChannelMessage>).close()
        @Suppress("UNCHECKED_CAST")
        (queue.getPrivateField("sendChannel") as Channel<Pair<List<StorableEvent>, SdkVersion>>).close()
        queue.close()
        runBlocking { queue.waitClosed() }
    }

    @Test
    fun close_isIdempotentAndOnlyEnqueuesOneCancelMessage() {
        val queue = newQueue()

        @Suppress("UNCHECKED_CAST")
        val eventQueue = queue.getPrivateField("eventQueue") as LinkedBlockingQueue<EventQueueMessage>
        while (eventQueue.poll() != null) {
            // Drop the initial retry message.
        }

        queue.close()
        queue.close()

        assertSame(EventQueueMessage.CancelMessage, eventQueue.poll(1, TimeUnit.SECONDS))
        assertNull(eventQueue.poll())
    }

    @Test
    fun connectivityReconnectEnqueuesRetryOnlyWhenConnected() {
        val provider = CapturingConnectivityProvider()
        val queue = newQueue()
        queue.start(provider)
        @Suppress("UNCHECKED_CAST")
        val eventQueue = queue.getPrivateField("eventQueue") as LinkedBlockingQueue<EventQueueMessage>
        while (eventQueue.poll() != null) {
            // Drop the initial retry message before exercising reconnect callbacks.
        }

        provider.callback!!.onConnectivityChange(false)
        assertNull(eventQueue.poll())

        provider.callback!!.onConnectivityChange(true)
        assertEquals(EventQueueMessage.RescheduleMessages("reconnect"), eventQueue.poll(1, TimeUnit.SECONDS))

        queue.close()
        runBlocking { queue.waitClosed() }
    }

    @Test
    fun startCleanupRejectsPendingFuturesAndClosesRepository() {
        val repository = FakeEventRepository()
        val queue = newQueue(repository = repository, executor = PublishEventTaskExecutor { _, _ -> ValueFuture(emptyList()) })
        queue.start(null)

        val future = queue.publishEvent(testPublishableEvent("pending_event"))
        queue.close()
        runBlocking { queue.waitClosed() }

        assertFutureFailsWith(future, Throwable::class.java)
        assertTrue(repository.isClosed)
    }

    @Test
    fun publishedEventsResolveFutureDeleteEventsAndRetryStoredEvents() {
        val repository = FakeEventRepository()
        val executorEvents = AtomicReference<List<StorableEvent>>()
        val queue = newQueue(
            repository = repository,
            executor = PublishEventTaskExecutor { events, _ ->
                executorEvents.set(events)
                ValueFuture(events)
            },
            runDelayMS = 20,
        )
        queue.start(null)

        val future = queue.publishEvent(testPublishableEvent("published_event"))
        val result = future.get(3, TimeUnit.SECONDS)
        queue.close()
        runBlocking { queue.waitClosed() }

        assertNull(result)
        assertTrue(repository.deletedEvents.map { it.id }.contains(executorEvents.get()!!.single().id))
        assertTrue(repository.events.isEmpty())
        assertTrue(repository.fetchCalls >= 2)
    }

    @Test
    fun failedPublishingUnmarksEventsAndLeavesFuturePendingUntilShutdown() {
        val repository = FakeEventRepository()
        val queue = newQueue(
            repository = repository,
            executor = PublishEventTaskExecutor { _, _ -> ErrorFuture(RuntimeException("publish failed")) },
            runDelayMS = 20,
        )
        queue.start(null)

        val future = queue.publishEvent(testPublishableEvent("failed_event"))
        waitUntil { repository.unmarkedIds.isNotEmpty() }
        assertFutureTimesOut(future)

        queue.close()
        runBlocking { queue.waitClosed() }
        assertFutureFailsWith(future, Throwable::class.java)
    }

    @Test
    fun storeFailureRejectsEventFuture() {
        val queue = newQueue(repository = FakeEventRepository(storeException = RuntimeException("store failed")))
        queue.start(null)

        val future = queue.publishEvent(testPublishableEvent("store_failed_event"))

        assertFutureFailsWith(future, Throwable::class.java)
        queue.close()
        runBlocking { queue.waitClosed() }
    }

    @Test
    fun rescheduleSkipsInFlightEventsAndSchedulesRetryForStoredEvents() {
        val repository = FakeEventRepository()
        val heldFuture = ResolvableFuture<List<StorableEvent>>()
        val queue = newQueue(repository = repository, executor = PublishEventTaskExecutor { _, _ -> heldFuture })
        queue.start(null)

        queue.publishEvent(testPublishableEvent("in_flight_event"))
        waitUntil { repository.storedEvents.isNotEmpty() }
        val inFlightId = repository.storedEvents.single().eventId
        repository.events[20L] = testStorableEvent(20L, eventId = inFlightId, name = "duplicate_stored")
        repository.events[21L] = testStorableEvent(21L, name = "stored_event")
        @Suppress("UNCHECKED_CAST")
        val eventQueue = queue.getPrivateField("eventQueue") as LinkedBlockingQueue<EventQueueMessage>
        eventQueue.offer(EventQueueMessage.RescheduleMessages("manual"))

        waitUntil { repository.markedIds.contains(21L) }
        assertTrue(repository.markedIds.contains(20L))

        queue.close()
        heldFuture.resolve(emptyList())
        runBlocking { queue.waitClosed() }
    }

    @Test
    fun maxBatchSizeFlushDrainsAdditionalEventMessages() {
        val repository = FakeEventRepository()
        val capturedBatch = AtomicReference<List<StorableEvent>>()
        val queue = newQueue(
            repository = repository,
            executor = PublishEventTaskExecutor { events, _ ->
                capturedBatch.set(events)
                ValueFuture(events)
            },
            runDelayMS = 5_000,
        )
        queue.maxBatchSize = 3
        queue.start(null)

        queue.publishEvent(testPublishableEvent("batch_1"))
        queue.publishEvent(testPublishableEvent("batch_2"))
        queue.publishEvent(testPublishableEvent("batch_3"))

        waitUntil { capturedBatch.get()?.size == 3 }
        queue.close()
        runBlocking { queue.waitClosed() }

        assertEquals(listOf("batch_1", "batch_2", "batch_3"), capturedBatch.get().map { it.event.name })
        assertEquals(listOf(1L, 2L, 3L), repository.deletedEvents.map { it.id })
    }

    @Test
    fun sessionEndEventFlushesImmediately() {
        val capturedBatch = AtomicReference<List<StorableEvent>>()
        val queue = newQueue(
            executor = PublishEventTaskExecutor { events, _ ->
                capturedBatch.set(events)
                ValueFuture(events)
            },
            runDelayMS = 5_000,
        )
        queue.start(null)

        queue.publishEvent(testPublishableEvent("ordinary_event"))
        queue.publishEvent(testPublishableEvent(JtSessionTrackingEvent.NAME, dimensions = mapOf("jt_action" to "end")))

        waitUntil { capturedBatch.get()?.size == 2 }
        queue.close()
        runBlocking { queue.waitClosed() }

        assertEquals(listOf("ordinary_event", JtSessionTrackingEvent.NAME), capturedBatch.get().map { it.event.name })
    }

    @Test
    fun sessionEndFlushDrainsAlreadyQueuedEventMessages() {
        val repository = FakeEventRepository()
        val capturedBatch = AtomicReference<List<StorableEvent>>()
        val queue = newQueue(
            repository = repository,
            executor = PublishEventTaskExecutor { events, _ ->
                capturedBatch.set(events)
                ValueFuture(events)
            },
            runDelayMS = 5_000,
        )

        @Suppress("UNCHECKED_CAST")
        val eventChannel = queue.getPrivateField("eventChannel") as Channel<EventChannelMessage>
        eventChannel.trySend(
            EventChannelMessage.EventMessage(
                testStorableEvent(1L, name = JtSessionTrackingEvent.NAME, dimensions = mapOf("jt_action" to "end")),
            ),
        )
        eventChannel.trySend(EventChannelMessage.EventMessage(testStorableEvent(2L, name = "queued_after_end")))

        queue.start(null)
        waitUntil { capturedBatch.get()?.size == 2 }
        queue.close()
        runBlocking { queue.waitClosed() }

        assertEquals(listOf(JtSessionTrackingEvent.NAME, "queued_after_end"), capturedBatch.get().map { it.event.name })
        assertTrue(repository.markedHistory.containsAll(listOf(1L, 2L)))
    }

    @Test
    fun timeoutFlushesPendingEvents() {
        val capturedBatch = AtomicReference<List<StorableEvent>>()
        val queue = newQueue(
            executor = PublishEventTaskExecutor { events, _ ->
                capturedBatch.set(events)
                ValueFuture(events)
            },
            runDelayMS = 25,
        )
        queue.maxBatchSize = 10
        queue.start(null)

        queue.publishEvent(testPublishableEvent("timeout_event"))

        waitUntil { capturedBatch.get()?.singleOrNull()?.event?.name == "timeout_event" }
        queue.close()
        runBlocking { queue.waitClosed() }
    }

    @Test
    fun closeSendsLastPendingBatch() {
        val repository = FakeEventRepository()
        val capturedBatch = AtomicReference<List<StorableEvent>>()
        val queue = newQueue(
            repository = repository,
            executor = PublishEventTaskExecutor { events, _ ->
                capturedBatch.set(events)
                ValueFuture(events)
            },
            runDelayMS = 5_000,
        )
        queue.maxBatchSize = 10
        queue.start(null)

        queue.publishEvent(testPublishableEvent("last_batch_event"))
        waitUntil { repository.markedIds.contains(1L) }
        queue.close()
        runBlocking { queue.waitClosed() }

        assertEquals("last_batch_event", capturedBatch.get().single().event.name)
    }

    @Test
    fun emptyBatchFromClosedEventChannelIsIgnoredByWorker() {
        val executorCalls = AtomicBoolean(false)
        val queue = newQueue(
            executor = PublishEventTaskExecutor { _, _ ->
                executorCalls.set(true)
                ValueFuture(emptyList())
            },
        )

        @Suppress("UNCHECKED_CAST")
        val sendChannel = queue.getPrivateField("sendChannel") as Channel<Pair<List<StorableEvent>, SdkVersion>>
        sendChannel.trySend(Pair(emptyList(), sdkVersion))
        queue.start(null)

        queue.close()
        runBlocking { queue.waitClosed() }

        assertFalse(executorCalls.get())
    }

    @Test
    fun debugDisabledCoversDebugBranchComplements() {
        val previousDebug = setBuildConfigDebug(false)
        try {
            val repository = FakeEventRepository()
            repository.events[10L] = testStorableEvent(10L, name = "stored_debug_false")
            val capturedBatch = AtomicReference<List<StorableEvent>>()
            val queue = newQueue(
                repository = repository,
                executor = PublishEventTaskExecutor { events, _ ->
                    capturedBatch.set(events)
                    ValueFuture(events)
                },
                runDelayMS = 20,
            )

            @Suppress("UNCHECKED_CAST")
            val eventChannel = queue.getPrivateField("eventChannel") as Channel<EventChannelMessage>
            eventChannel.trySend(
                EventChannelMessage.EventMessage(
                    testStorableEvent(1L, name = JtSessionTrackingEvent.NAME, dimensions = mapOf("jt_action" to "end")),
                ),
            )
            eventChannel.trySend(EventChannelMessage.EventMessage(testStorableEvent(2L, name = "debug_false_extra")))

            queue.start(null)
            waitUntil { capturedBatch.get()?.isNotEmpty() == true }
            queue.publishEvent(testPublishableEvent("debug_false_publish"))
            waitUntil { repository.storedEvents.isNotEmpty() }
            queue.close()
            runBlocking { queue.waitClosed() }
        } finally {
            setBuildConfigDebug(previousDebug)
        }
    }

    @Test
    fun repositoryExceptionsAreHandled() {
        val logger = CapturingLogger()
        val queue = newQueue(
            logger = logger,
            repository = FakeEventRepository(
                fetchException = RuntimeException("fetch failed"),
                markException = RuntimeException("mark failed"),
                deleteException = RuntimeException("delete failed"),
                unmarkException = RuntimeException("unmark failed"),
            ),
            executor = PublishEventTaskExecutor { events, _ -> ValueFuture(events) },
            runDelayMS = 20,
        )
        queue.start(null)

        queue.publishEvent(testPublishableEvent("exception_event"))
        Thread.sleep(100)
        queue.close()
        runBlocking { queue.waitClosed() }

        assertTrue(logger.warnMessages.any { it.contains("Unable to retrieve events") })
        assertTrue(logger.warnMessages.any { it.contains("Unable to mark event") })
        assertTrue(logger.warnMessages.any { it.contains("Unable to delete events") })
    }

    @Test
    fun failedPublishingHandlesUnmarkException() {
        val logger = CapturingLogger()
        val queue = newQueue(
            logger = logger,
            repository = FakeEventRepository(unmarkException = RuntimeException("unmark failed")),
            executor = PublishEventTaskExecutor { _, _ -> ErrorFuture(RuntimeException("publish failed")) },
            runDelayMS = 20,
        )
        queue.start(null)

        queue.publishEvent(testPublishableEvent("unmark_exception_event"))
        waitUntil { logger.warnMessages.any { it.contains("Unable to unmark events") } }
        queue.close()
        runBlocking { queue.waitClosed() }
    }

    @Test
    fun handleExceptionLogsSqlAndCancellationSeparately() {
        val logger = CapturingLogger()
        val queue = newQueue(logger = logger)

        queue.invokePrivate("handleException", "sqlite", SQLiteException("db"))
        queue.invokePrivate("handleException", "sql", SQLException("sql"))
        queue.invokePrivate("handleException", "cancel", kotlinx.coroutines.CancellationException("cancel"))

        assertTrue(logger.warnMessages.any { it.contains("sqlite") && it.contains("SQL error") })
        assertTrue(logger.warnMessages.any { it.contains("sql") && it.contains("SQL error") })
        assertTrue(logger.warnMessages.any { it.contains("cancel") && it.contains("got cancel") })
    }

    @Test
    fun channelUndeliveredHandlersLogWarnings() {
        val logger = CapturingLogger()
        val queue = newQueue(logger = logger)

        @Suppress("UNCHECKED_CAST")
        val eventChannel = queue.getPrivateField("eventChannel") as Channel<EventChannelMessage>

        @Suppress("UNCHECKED_CAST")
        val sendChannel = queue.getPrivateField("sendChannel") as Channel<Pair<List<StorableEvent>, SdkVersion>>

        eventChannel.trySend(EventChannelMessage.TimeoutMessage)
        sendChannel.trySend(Pair(emptyList(), sdkVersion))
        eventChannel.cancel()
        sendChannel.cancel()

        assertTrue(logger.warnMessages.any { it.contains("TimeoutMessage") })
        assertTrue(logger.warnMessages.any { it.contains("[]") })
    }

    @Test
    fun eventChannelMessageToEventMessageCoversBothBranches() {
        val eventMessage = EventChannelMessage.EventMessage(testStorableEvent(1L))

        assertSame(eventMessage, eventMessage.toEventMessage())
        assertNull(EventChannelMessage.TimeoutMessage.toEventMessage())
    }

    @Test
    fun buildCoversEmptyDimensionsAndNullableAttributionFields() {
        val dto = PublishEventsQueue.build(
            listOf(testStorableEvent(1L, name = "no_dimensions", dimensions = emptyMap())),
            TestDeviceInfoImpl(),
            PublishEventsQueue.DTOBuildAttributionParams(
                advertiserId = null,
                trackingId = null,
                trackingProvider = "provider",
                userId = UUID.fromString("8a4929d4-b3f4-4593-84f9-b2fad0e9cc1e"),
                installInstanceId = UUID.fromString("f7642b2f-35e9-4a2f-9c26-a5f9cd2b4794"),
            ),
            sdkVersion,
            TestApplicationVersion(),
        ).toJSON(Formatter)

        assertFalse(dto.getJSONArray("events").getJSONObject(0).has("dimensions"))
        assertTrue(dto.getJSONObject("user").isNull("deviceId"))
    }

    @Test
    fun dtoBuildAttributionParamsGeneratedMethodsAreCovered() {
        val params = PublishEventsQueue.DTOBuildAttributionParams(
            "advertiser",
            "tracking",
            "provider",
            UUID.fromString("8a4929d4-b3f4-4593-84f9-b2fad0e9cc1e"),
            UUID.fromString("f7642b2f-35e9-4a2f-9c26-a5f9cd2b4794"),
        )
        val copy = params.copy(advertiserId = "other", trackingId = "other-tracking")

        assertEquals("other", copy.advertiserId)
        assertEquals("other-tracking", copy.trackingId)
        assertEquals("provider", copy.trackingProvider)
        assertTrue(params.toString().contains("advertiser"))
    }

    private fun newQueue(
        repository: FakeEventRepository = FakeEventRepository(),
        executor: PublishEventTaskExecutor = PublishEventTaskExecutor { events, _ -> ValueFuture(events) },
        logger: Logger = TestLogger(),
        runDelayMS: Long = 5,
        isTracking: AtomicBoolean = AtomicBoolean(true),
    ): PublishEventsQueue {
        val queue = PublishEventsQueue(
            executor,
            logger,
            NetworkErrorLogger(),
            repository,
            runDelayMS,
            isTracking,
            sdkVersion,
            GlobalDimensionsRepo(EmptySharedPreferences()),
            enableConnectionTracking = false,
            connectivityProvider = CapturingConnectivityProvider(),
        )
        return queue
    }

    private fun testPublishableEvent(
        name: String,
        dimensions: Map<String?, String?> = emptyMap(),
        sdkVersion: SdkVersion = this.sdkVersion,
    ): PublishableAppEvent = PublishableAppEvent(
        name,
        dimensions,
        0.0,
        Unit.COUNT,
        null,
        "session-id",
        sdkVersion,
        Date(0),
    )

    private fun testStorableEvent(
        id: Long,
        eventId: UUID = UUID.randomUUID(),
        name: String = "stored_event_$id",
        dimensions: Map<String?, String?> = emptyMap(),
        sdkVersion: SdkVersion = this.sdkVersion,
    ): StorableEvent = StorableEvent(id, eventId, testPublishableEvent(name, dimensions, sdkVersion), id)

    private fun assertFutureTimesOut(future: AsyncFuture<*>) {
        try {
            future.get(100, TimeUnit.MILLISECONDS)
            throw AssertionError("Expected future to time out")
        } catch (_: TimeoutException) {
            // Expected.
        }
    }

    private fun assertFutureFailsWith(future: AsyncFuture<*>, expectedCause: Class<out Throwable>) {
        try {
            future.get(3, TimeUnit.SECONDS)
            throw AssertionError("Expected future to fail with ${expectedCause.simpleName}")
        } catch (exception: java.util.concurrent.ExecutionException) {
            assertTrue(
                "Expected ${expectedCause.simpleName}, got ${exception.cause?.javaClass?.name}",
                expectedCause.isInstance(exception.cause),
            )
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("condition was not met before timeout", condition())
    }

    private fun Any.setPrivateField(name: String, value: Any?) {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(this, value)
    }

    private fun Any.getPrivateField(name: String): Any? {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this)
    }

    private fun Any.invokePrivate(name: String, vararg args: Any?) {
        val method = javaClass.getDeclaredMethod(name, String::class.java, Throwable::class.java)
        method.isAccessible = true
        method.invoke(this, *args)
    }

    private fun setBuildConfigDebug(value: Boolean): Boolean {
        val field = BuildConfig::class.java.getDeclaredField("DEBUG")
        field.isAccessible = true
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val base = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java).invoke(unsafe, field)
        val offset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java).invoke(unsafe, field) as Long
        val previous = unsafeClass.getMethod("getBoolean", Any::class.java, Long::class.javaPrimitiveType).invoke(unsafe, base, offset) as Boolean
        unsafeClass.getMethod("putBooleanVolatile", Any::class.java, Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .invoke(unsafe, base, offset, value)
        return previous
    }

    private class FakeEventRepository(
        private val storeException: Exception? = null,
        private val fetchException: Exception? = null,
        private val markException: Exception? = null,
        private val unmarkException: Exception? = null,
        private val deleteException: Exception? = null,
    ) : EventRepository {
        val events = linkedMapOf<Long, StorableEvent>()
        val storedEvents = mutableListOf<StorableEvent>()
        val markedIds = mutableSetOf<Long>()
        val markedHistory = mutableListOf<Long>()
        val unmarkedIds = mutableListOf<Long>()
        val deletedEvents = mutableListOf<StorableEvent>()
        var fetchCalls = 0
        var isClosed = false
        private var nextId = 1L

        override suspend fun storeEntity(data: StorableEvent): Pair<Long, Long>? {
            storeException?.let { throw it }
            val id = nextId++
            val event = StorableEvent(id, data.eventId, data.event, id)
            events[id] = event
            storedEvents.add(event)
            return Pair(id, id)
        }

        override suspend fun storeEntities(dataList: List<StorableEvent>) {
            dataList.forEach { storeEntity(it) }
        }

        override suspend fun removeEntitiesByDate(cutoffMS: Long) = doNothing()

        override suspend fun fetchNextBatchAndMark(batchSize: Int): List<StorableEvent> {
            fetchCalls++
            fetchException?.let { throw it }
            val batch = events.filterKeys { it !in markedIds }.values.take(batchSize)
            markedIds.addAll(batch.map { it.id })
            markedHistory.addAll(batch.map { it.id })
            return batch
        }

        override suspend fun markEntitiesById(idList: List<Long>) {
            markException?.let { throw it }
            markedIds.addAll(idList)
            markedHistory.addAll(idList)
        }

        override suspend fun unMarkEntitiesById(idList: List<Long>) {
            unmarkException?.let { throw it }
            markedIds.removeAll(idList.toSet())
            unmarkedIds.addAll(idList)
        }

        override suspend fun deleteEntities(dataList: List<StorableEvent>) {
            deleteException?.let { throw it }
            deletedEvents.addAll(dataList)
            dataList.forEach {
                events.remove(it.id)
                markedIds.remove(it.id)
            }
        }

        override suspend fun getAll(): List<StorableEvent> = events.values.toList()

        override suspend fun getAllUnMark(): List<StorableEvent> = events.filterKeys { it !in markedIds }.values.toList()

        override fun close() {
            isClosed = true
        }
    }

    private class CapturingConnectivityProvider : ConnectivityProvider {
        var callback: ConnectivityProvider.ConnectivityCallback? = null
        override var connectionType: ConnectionType = ConnectionType.UNKNOWN

        override fun registerOnReconnected(callback: ConnectivityProvider.ConnectivityCallback): Subscription {
            this.callback = callback
            return object : Subscription {
                override fun unsubscribe() {
                    this@CapturingConnectivityProvider.callback = null
                }
            }
        }

        override fun shutdown() = doNothing()
    }

    private class CapturingLogger : Logger {
        val warnMessages = mutableListOf<String>()
        override val fallback: Logger get() = this
        override fun debug(message: String, vararg fields: LoggerFields) = doNothing()

        override fun info(message: String, vararg fields: LoggerFields) = doNothing()

        override fun warn(message: String, vararg fields: LoggerFields) {
            warnMessages.add(message)
        }

        override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) {
            warnMessages.add(message)
        }

        override fun error(message: String, vararg fields: LoggerFields) = doNothing()

        override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) = doNothing()

        override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) = doNothing()
    }

    private class TestSessionManager : SessionManager {
        var updateCalls = 0
        override fun start() = doNothing()

        override fun shutdown() = doNothing()

        override fun getLatestSessionId(): String = "session-id"
        override fun onResume() = doNothing()

        override fun onPause() = doNothing()

        override fun updateSessionTimeStamp() {
            updateCalls++
        }
    }

    private class TestSdkVersion : SdkVersion {
        override val platformType: PlatformType = PlatformType.ANDROID
        override val major: Int = 1
        override val minor: Int = 2
        override val patch: Int = 3
        override val name: String = "1.2.3-test"
    }

    private class TestApplicationVersion : ApplicationVersion {
        override fun getVersionName(): String = "1.0.0"
        override fun getVersionCode(): String = "100"
    }

    private companion object {
        fun doNothing() = kotlin.Unit
    }
}
