@file:OptIn(DelicateCoroutinesApi::class)

package io.justtrack

import android.database.sqlite.SQLiteException
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PRIVATE
import io.justtrack.BaseJustTrackSdk.PublishEventsTaskRunner
import io.justtrack.events.JtSessionTrackingEvent
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.exceptions.SdkNotTrackingException
import io.justtrack.log.Logger
import io.justtrack.versions.SdkVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.sql.SQLException
import java.util.Collections
import java.util.Date
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

internal class PublishEventsQueue(
    private val publishEvents: PublishEventsTaskRunner,
    private val logger: Logger,
    private val networkErrorLogger: NetworkErrorLogger,
    private val eventRepository: EventRepository,
    private val runDelayMS: Long = 5_000L,
    private val isTracking: AtomicBoolean,
    private val sdkVersionInfo: SdkVersion,
) : AutoCloseable {
    @VisibleForTesting
    internal var maxBatchSize: Int = DEFAULT_BATCH_SIZE

    private val coroutineCancelExceptionHandler = CoroutineExceptionHandler { _, exception ->
        handleException("PublishEventQueue coroutine cancelled", exception)
    }
    private val coroutineScope = CoroutineScope(Dispatchers.IO + coroutineCancelExceptionHandler)

    private val done = AtomicBoolean(false)

    private val runningTasks = mutableListOf<Job>()

    private val futureMap = HashMap<Long, Callback<PublishingEvent>>()
    private val eventsInFlight = HashSet<UUID>()

    // Deadlock-Safety: this mutex used for eventsInFlight & futureMap access
    private val mutex = Mutex()

    private val eventQueue: BlockingQueue<EventQueueMessage> = createEventQueue()

    private fun createEventQueue(): BlockingQueue<EventQueueMessage> {
        val eventQueue: BlockingQueue<EventQueueMessage> = LinkedBlockingQueue()
        // ensure any events we have stored but failed to send are retried.
        // we do this directly after creating the queue to ensure there is no message before that
        // message in the queue.
        eventQueue.offer(EventQueueMessage.RescheduleMessages("initial retry"))

        return eventQueue
    }

    private val eventChannel: Channel<EventChannelMessage> =
        Channel(Channel.UNLIMITED) { element ->
            logger.warn("undelivered event $element")
        }
    private val sendChannel: Channel<Pair<List<StorableEvent>, SdkVersion>> =
        Channel(Channel.UNLIMITED) { element ->
            logger.warn("undelivered event $element")
        }

    @JvmName("start")
    internal fun start(connectivityProvider: ConnectivityProvider?) {
        connectivityProvider?.registerOnReconnected(
            object : ConnectivityProvider.ConnectivityCallback {
                override fun onConnectivityChange(connected: Boolean) {
                    if (connected) {
                        // upon reconnect, try to pump all the messages from the database to the server again
                        eventQueue.offer(EventQueueMessage.RescheduleMessages("reconnect"))
                    }
                }
            },
        )

        runTask("queue") {
            runQueue()
        }
        runTask("eventChannel") {
            consumeEventChannel()
        }
        for (i in 0 until WORKER_COUNT) {
            runTask("worker-$i") {
                runWorker()
            }
        }
        // get a copy of all our currently running tasks so we can wait on them before adding the last cleanup task
        val mainTasks = runningTasks.toList()

        runTask("cleanup") {
            // after the last task finished, cancel all remaining futures and reject them. Otherwise they will be pending forever.
            mainTasks.joinAll()
            rejectPendingFutures()
            // we are done with everything, so close our repository
            eventRepository.close()
            logger.debug("PublishEventsQueue: Shutdown completed")
        }
        logger.debug("PublishEventsQueue: Initialized")
    }

    private fun runTask(taskName: String, task: suspend CoroutineScope.() -> Unit) {
        val job = coroutineScope.launch {
            try {
                task()
            } catch (e: Exception) {
                networkErrorLogger.logException(
                    logger,
                    e,
                    "PublishEventsQueue: failed to run $taskName task",
                )
            }
        }
        runningTasks.add(job)
    }

    private suspend fun rejectPendingFutures() {
        val pendingFutures: List<Callback<PublishingEvent>>
        // Deadlock-Safety: We only hold the lock to copy the map of remaining futures and clear it.
        // We specifically don't call the callbacks while holding the lock (as they could contain
        // arbitrary code).
        mutex.withLock {
            pendingFutures = ArrayList(futureMap.values)
            futureMap.clear()
            // also clear the map of events in flight, all tasks are done now
            eventsInFlight.clear()
        }

        pendingFutures.forEach {
            it.reject(Throwable("Application is shutting down, unable to resolve this event."))
        }
    }

    private suspend fun runQueue() {
        while (true) {
            val message = withContext(Dispatchers.IO) {
                eventQueue.take()
            }
            when (message) {
                is EventQueueMessage.CancelMessage -> {
                    // we are told to stop consuming messages, so return from the worker
                    logger.debug("PublishEventsQueue: Event queue persister shutting down")
                    eventChannel.close()
                    return
                }

                is EventQueueMessage.RescheduleMessages -> {
                    val reason = message.reason
                    val events = getStoredEventsAndMark()

                    var rescheduledEvents = 0
                    for (event in events) {
                        // Deadlock-Safety: We just query a collection, it is never locked while taking another lock.
                        val isInFlight =
                            mutex.withLock {
                                eventsInFlight.contains(event.eventId)
                            }
                        if (isInFlight) {
                            if (BuildConfig.DEBUG) {
                                logger.debug(
                                    "PublishEventsQueue: Not rescheduled in-flight event ${event.event.name} at ${event.getHappenedAt()}",
                                )
                            }
                            continue
                        }
                        eventChannel.send(
                            EventChannelMessage.EventMessage(
                                PublishingEvent(
                                    event.id,
                                    event.eventId,
                                    event.event,
                                    event.sequenceNumber,
                                ),
                            ),
                        )
                        rescheduledEvents++
                        if (BuildConfig.DEBUG) {
                            logger.debug("PublishEventsQueue: Rescheduled event ${event.event.name} at ${event.getHappenedAt()}")
                        }
                    }
                    if (BuildConfig.DEBUG) {
                        logger.debug("PublishEventsQueue: Rescheduled $rescheduledEvents events (reason = $reason)")
                    }

                    if (rescheduledEvents > 0) {
                        logger.debug("PublishEventsQueue: Enqueued $rescheduledEvents stored events again")
                        // if we found some events, try to find more events AFTER processing all
                        // pending messages (because writing too many messages to the channel might
                        // eventually block)
                        eventQueue.offer(EventQueueMessage.RescheduleMessages("retry: $reason"))
                    }
                }

                is EventQueueMessage.PublishMessage -> {
                    var event = message.event
                    val result = message.result
                    val resultPair = storeEvent(event)
                    if (resultPair != null) {
                        val storeId = resultPair.first
                        val sequenceNumber = resultPair.second
                        event = PublishingEvent(storeId, event.eventId, event.event, sequenceNumber)
                        // Deadlock-Safety: We only add an object to a map
                        mutex.withLock {
                            futureMap[storeId] = result
                            eventsInFlight.add(event.eventId)
                        }
                        eventChannel.send(EventChannelMessage.EventMessage(event))
                        if (BuildConfig.DEBUG) {
                            logger.debug("PublishEventsQueue: Persisted event ${event.event.name} at ${event.getHappenedAt()}")
                        }
                    } else {
                        result.reject(Throwable("Failed to store event to local database. Event: ${event.event}"))
                    }
                }
            }
        }
    }

    private suspend fun consumeEventChannel() {
        try {
            var pendingEvents = ArrayList<StorableEvent>()

            while (true) {
                val result = eventChannel.receiveCatching().getOrNull()
                if (result == null) {
                    // channel closed, send last batch and return
                    if (BuildConfig.DEBUG) {
                        logger.debug("PublishEventsQueue: Sending last batch of ${pendingEvents.size} to server")
                    }
                    enqueueEventsToChannel(pendingEvents)
                    return
                }
                when (result) {
                    is EventChannelMessage.TimeoutMessage -> {
                        if (pendingEvents.isNotEmpty()) {
                            // we got the timeout, send any pending events
                            if (BuildConfig.DEBUG) {
                                logger.debug("PublishEventsQueue: Sending batch of ${pendingEvents.size} to server after timeout")
                            }
                            enqueueEventsToChannel(pendingEvents)
                            pendingEvents = ArrayList()
                        }
                    }

                    is EventChannelMessage.EventMessage -> {
                        // we got a single event, add it to the batch and retry reading
                        // limit the remaining time to 5s (runDelayMS) and subtract any time we spend waiting.
                        // should the time get negative, we will immediately get null in the next iteration
                        if (pendingEvents.isEmpty()) {
                            coroutineScope.launch {
                                delay(runDelayMS)
                                if (!eventChannel.isClosedForSend) {
                                    eventChannel.send(EventChannelMessage.TimeoutMessage)
                                }
                            }
                        }
                        val event = result.event
                        markEventById(event.id)
                        pendingEvents.add(event)
                        // if we see a session end event, we want to publish them immediately as the app might be going to the background.
                        // if we reach the max batch size, we also immediately send the data to flush it
                        val isEventTrackingEnd = event.event.name == JtSessionTrackingEvent.NAME && event.event.dimensions["jt_action"].equals("end")

                        if (isEventTrackingEnd || pendingEvents.size == maxBatchSize) {
                            // try to add as many events as possible to the current batch without blocking
                            while (pendingEvents.size < maxBatchSize) {
                                val nextMessage =
                                    eventChannel.tryReceive().getOrNull()?.toEventMessage() ?: break

                                val nextEvent = nextMessage.event
                                markEventById(nextEvent.id)
                                pendingEvents.add(nextEvent)
                            }

                            if (BuildConfig.DEBUG) {
                                logger.debug(
                                    "PublishEventsQueue: Sending batch of ${pendingEvents.size} to server after session end or full batch",
                                )
                            }
                            enqueueEventsToChannel(pendingEvents)
                            pendingEvents = ArrayList()
                        }
                    }
                }
            }
        } finally {
            sendChannel.close()
        }
    }

    private suspend fun enqueueEventsToChannel(pendingEvents: List<StorableEvent>) {
        val pendingEventGroups = pendingEvents.groupBy {
            it.event.sdkVersion
        }

        pendingEventGroups.forEach { group ->
            sendChannel.send(Pair(group.value, group.key))
        }
    }

    private suspend fun runWorker() {
        for (pendingEvents in sendChannel) {
            // send the batch of events to the server. if there are none (signalled by null return),
            // immediately continue
            val result = publishEventsInBatch(pendingEvents.first, pendingEvents.second) ?: continue

            result.onSuccess { events ->
                for (event in events) {
                    val message = "PublishEventsQueue: Successfully published " +
                        "seqNo ${event.sequenceNumber}, ${event.event} at ${event.getHappenedAt()} in batch"
                    logger.debug(message)
                }
                if (events.isNotEmpty()) {
                    // if we managed to publish events once, try to send any stored events as well.
                    // we might have had a bad internet connection before (but didn't lose it), so
                    // this gives us an additional place to perform these actions.
                    eventQueue.offer(EventQueueMessage.RescheduleMessages("got a success"))
                }
                deleteEvents(events)
                val pendingCalls: ArrayList<Pair<Callback<PublishingEvent>, PublishingEvent>> =
                    ArrayList()
                // Deadlock-Safety: We only extract all futures we have to call from the map, we call them
                // in a later step as calling them can run arbitrary code.
                mutex.withLock {
                    for (event in events) {
                        val future = futureMap.remove(event.id)
                        if (future != null) {
                            pendingCalls.add(Pair(future, event))
                        }
                        eventsInFlight.remove(event.eventId)
                    }
                }

                for (call in pendingCalls) {
                    call.first.resolve(call.second)
                }
            }
            result.onFailure { exception ->
                for (event in pendingEvents.first) {
                    val message = "PublishEventsQueue: Failed to publish ${event.event} at ${event.getHappenedAt()}"
                    logger.debug(message)
                }
                // Deadlock-Safety: We only remove some ids from eventsInFlight, we don't take any additional locks.
                mutex.withLock {
                    for (event in pendingEvents.first) {
                        eventsInFlight.remove(event.eventId)
                    }
                }
                unMarkEvents(pendingEvents.first)
                handleException(
                    "Failed to send event batch (size = ${pendingEvents.first.size}) to server",
                    exception,
                )
                // don't resolve the future as failed because we still might retry sending the event
            }
        }
    }

    @JvmName("publishEvent")
    internal fun publishEvent(event: AppEvent, sessionManager: SessionManager): AsyncFuture<Void?> {
        if (!isTracking.get()) {
            return ErrorFuture(SdkNotTrackingException())
        }

        try {
            event.validate()
        } catch (exception: InvalidFieldException) {
            logger.warn("Not publishing invalid app event", exception)

            return ErrorFuture(exception)
        }

        // If we have an open session (because the app didn't shut down properly / was killed), we will
        // send an end event from the session manager constructor - before assigning the sessionManager
        // variable in our constructor.
        sessionManager.updateSessionTimeStamp()

        val buildEvent = event.build(sessionManager.getLatestSessionId(), sdkVersionInfo)

        return publishEvent(buildEvent)
    }

    @VisibleForTesting(otherwise = PRIVATE)
    @JvmName("publishEvent")
    internal fun publishEvent(event: PublishableAppEvent): AsyncFuture<Void?> {
        if (done.get()) {
            return ErrorFuture(IllegalStateException("queue has been shut down"))
        }

        val result = ResolvableFuture<PublishingEvent>()
        val publishingEvent = PublishingEvent(
            eventId = UUID.randomUUID(),
            event = event,
        )

        if (!eventQueue.offer(EventQueueMessage.PublishMessage(publishingEvent, result))) {
            return ErrorFuture(IllegalStateException("too many events in publishing queue, slow down publishing events"))
        }

        return TransformingFuture(
            result,
        ) { null }
    }

    private suspend fun storeEvent(data: PublishingEvent): Pair<Long, Long>? {
        return try {
            eventRepository.storeEntity(data)
        } catch (e: Exception) {
            handleException("Unable to store event to database", e)
            null
        }
    }

    override fun close() {
        val wasDone = done.getAndSet(true)
        if (wasDone) {
            return
        }

        eventQueue.offer(EventQueueMessage.CancelMessage)
    }

    @VisibleForTesting
    @JvmName("waitForShutdown")
    internal suspend fun waitClosed() {
        runningTasks.joinAll()
    }

    private suspend fun getStoredEventsAndMark(): List<StorableEvent> {
        return try {
            eventRepository.fetchNextBatchAndMark(maxBatchSize)
        } catch (exception: Exception) {
            handleException("Unable to retrieve events from database", exception)
            emptyList()
        }
    }

    private suspend fun publishEventsInBatch(batch: List<StorableEvent>, sdkVersion: SdkVersion): Result<List<PublishingEvent>>? {
        if (batch.isEmpty()) {
            return null
        }

        return try {
            Result.success(
                publishEvents.runPublishEventTask(
                    batch.map {
                        PublishingEvent(
                            it.id,
                            it.eventId,
                            it.event,
                            it.sequenceNumber,
                        )
                    },
                    sdkVersion,
                ).await(),
            )
        } catch (e: java.lang.Exception) {
            Result.failure(e)
        }
    }

    private fun markEventById(id: Long) = coroutineScope.launch {
        try {
            eventRepository.markEntitiesById(Collections.singletonList(id))
        } catch (e: Exception) {
            handleException("Unable to mark event", e)
        }
    }

    private suspend fun unMarkEvents(idList: List<StorableEvent>) {
        try {
            eventRepository.unMarkEntitiesById(idList.map { it.id })
        } catch (e: Exception) {
            handleException("Unable to unmark events", e)
        }
    }

    private suspend fun deleteEvents(idList: List<PublishingEvent>) {
        try {
            eventRepository.deleteEntities(idList)
        } catch (e: Exception) {
            handleException("Unable to delete events", e)
        }
    }

    private fun handleException(message: String, exception: Throwable) {
        when (exception) {
            is SQLiteException, is SQLException -> {
                logger.warn("PublishEventsQueue: $message: SQL error ", exception)
            }

            is CancellationException -> {
                logger.warn("PublishEventsQueue: $message: got cancel ", exception)
            }

            else -> {
                networkErrorLogger.logException(logger, exception, "PublishEventsQueue: $message: error")
            }
        }
    }

    internal data class DTOBuildAttributionParams(
        internal val advertiserId: String?,
        internal val trackingId: String?,
        internal val trackingProvider: String,
        internal val userId: UUID,
        internal val installInstanceId: UUID,
    )

    internal companion object {
        private const val WORKER_COUNT = 8

        private const val DEFAULT_BATCH_SIZE = 100

        @JvmStatic
        @JvmName("build")
        internal fun build(
            events: Iterable<PublishingEvent>,
            deviceInfo: DeviceInfo,
            attributionParams: DTOBuildAttributionParams,
            sdkVersion: SdkVersion,
            applicationVersion: ApplicationVersion,
        ): DTOAppEvent {
            val encodedEvents: List<DTOAppEventEvent> =
                events.map { event ->
                    val dimensions = event.event.dimensions
                    val encodedDimensions = if (dimensions.isEmpty()) {
                        null
                    } else {
                        JSONObject(dimensions.mapKeys { it.key.toString() })
                    }
                    DTOAppEventEvent(
                        event.eventId,
                        event.event.name,
                        encodedDimensions,
                        event.event.value,
                        event.event.unit,
                        event.event.currency,
                        event.event.sessionId,
                        event.getHappenedAt(),
                        event.sequenceNumber,
                    )
                }
            val appVersionDTO = DTOAppVersion(applicationVersion.getVersionName(), applicationVersion.getVersionCode())
            val sdkVersionDTO = DTOSdkVersion(
                sdkVersion.major,
                sdkVersion.minor,
                sdkVersion.patch,
                sdkVersion.name,
                sdkVersion.platformType.platform,
                sdkVersion.platformType.wrapper,
            )

            return DTOAppEvent(
                appVersionDTO,
                sdkVersionDTO,
                DTOAppEventUser(
                    attributionParams.advertiserId,
                    deviceInfo.getCountryIso(),
                    deviceInfo.getDeviceLocale(),
                    attributionParams.userId,
                    attributionParams.installInstanceId,
                ),
                DTOAppEventDevice(
                    deviceInfo.getConnectionType(),
                    DTOAppEventDeviceOS(
                        deviceInfo.osVersion,
                        deviceInfo.osName,
                    ),
                    Date(),
                ),
                encodedEvents,
            )
        }
    }
}

internal sealed class EventQueueMessage {
    data object CancelMessage : EventQueueMessage()

    data class RescheduleMessages(
        val reason: String,
    ) : EventQueueMessage()

    data class PublishMessage(
        val event: PublishingEvent,
        val result: Callback<PublishingEvent>,
    ) : EventQueueMessage()
}

internal sealed class EventChannelMessage {
    fun toEventMessage(): EventMessage? {
        return if (this is EventMessage) {
            this
        } else {
            null
        }
    }

    data object TimeoutMessage : EventChannelMessage()

    data class EventMessage(val event: PublishingEvent) : EventChannelMessage()
}
