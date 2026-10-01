package io.justtrack

import android.content.Context
import io.justtrack.events.Dimension
import io.justtrack.events.PublishEventTaskExecutor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import io.justtrack.versions.SdkVersion
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
internal class GlobalDimensionsInjectionTest {

    private lateinit var repo: GlobalDimensionsRepo
    private lateinit var queue: PublishEventsQueue

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(GlobalDimensionsRepo.STORE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        repo = GlobalDimensionsRepo(
            context.getSharedPreferences(GlobalDimensionsRepo.STORE_NAME, Context.MODE_PRIVATE),
        )
        queue = newQueue(globalDimensionsRepo = repo)
    }

    @After
    fun tearDown() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(GlobalDimensionsRepo.STORE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun globalDimensionsAttachedToEvent() {
        repo.set(Dimension.JT_GLOBAL_0, "warrior")
        repo.set(Dimension.JT_GLOBAL_1, "experiment_a")

        val event = AppEvent("test_event")
        queue.track(event, TestSessionManager())

        assertEquals("warrior", event.dimensions["jt_global_0"])
        assertEquals("experiment_a", event.dimensions["jt_global_1"])
        assertNull(event.dimensions["jt_global_2"])
    }

    @Test
    fun eventLevelDimensionOverridesGlobal() {
        repo.set(Dimension.JT_GLOBAL_0, "global_value")

        val event = AppEvent("test_event")
        event.addDimension(Dimension.JT_GLOBAL_0, "user_value")
        queue.track(event, TestSessionManager())

        assertEquals("user_value", event.dimensions["jt_global_0"])
    }

    @Test
    fun clearedGlobalDimensionNotAttached() {
        repo.set(Dimension.JT_GLOBAL_0, "warrior")
        repo.set(Dimension.JT_GLOBAL_0, null)

        val event = AppEvent("test_event")
        queue.track(event, TestSessionManager())

        assertNull(event.dimensions["jt_global_0"])
    }

    @Test
    fun validationPassesWithMaxDimensionsPlusGlobals() {
        // Create an event with exactly 10 user-set dimensions (the max)
        val event = AppEvent("test_event")
        event.addDimension(Dimension.JT_ACTION, "click")
        event.addDimension(Dimension.JT_CATEGORY, "category")
        event.addDimension(Dimension.JT_CONTEXT, "context")
        event.addDimension(Dimension.JT_DETAIL, "detail")
        event.addDimension(Dimension.JT_ITEM_ID, "item_id")
        event.addDimension(Dimension.JT_ITEM_NAME, "item_name")
        event.addDimension(Dimension.JT_ITEM_TYPE, "item_type")
        event.addDimension(Dimension.JT_LOCATION, "location")
        event.addDimension(Dimension.JT_METHOD, "method")
        event.addDimension(Dimension.JT_STATE, "state")

        // Set global dimensions
        repo.set(Dimension.JT_GLOBAL_0, "warrior")
        repo.set(Dimension.JT_GLOBAL_1, "experiment_a")
        repo.set(Dimension.JT_GLOBAL_2, "level_5")

        // Track should succeed — globals are injected after validation
        queue.track(event, TestSessionManager())

        // Event now has 13 dimensions total (10 user + 3 global)
        assertEquals(13, event.dimensions.size)
        assertEquals("warrior", event.dimensions["jt_global_0"])
        assertEquals("experiment_a", event.dimensions["jt_global_1"])
        assertEquals("level_5", event.dimensions["jt_global_2"])
    }

    @Test
    fun emptyGlobalStoreDoesNotModifyEvent() {
        val event = AppEvent("test_event")
        event.addDimension(Dimension.JT_ACTION, "click")

        queue.track(event, TestSessionManager())

        assertEquals(1, event.dimensions.size)
        assertEquals("click", event.dimensions["jt_action"])
    }

    private fun newQueue(globalDimensionsRepo: GlobalDimensionsRepo = this.repo): PublishEventsQueue {
        return PublishEventsQueue(
            PublishEventTaskExecutor { events, _ -> ValueFuture(events) },
            TestLogger(),
            NetworkErrorLogger(),
            FakeEventRepository(),
            5L,
            AtomicBoolean(true),
            TestSdkVersion(),
            globalDimensionsRepo,
            enableConnectionTracking = false,
            connectivityProvider = object : ConnectivityProvider {
                override val connectionType: ConnectionType = ConnectionType.UNKNOWN
                override fun registerOnReconnected(callback: ConnectivityProvider.ConnectivityCallback): Subscription {
                    return object : Subscription {
                        override fun unsubscribe() = Unit
                    }
                }
                override fun shutdown() = Unit
            },
        )
    }

    private class TestSessionManager : SessionManager {
        override fun start() = Unit
        override fun shutdown() = Unit
        override fun getLatestSessionId(): String = "session-id"
        override fun onResume() = Unit
        override fun onPause() = Unit
        override fun updateSessionTimeStamp() = Unit
    }

    private class TestSdkVersion : SdkVersion {
        override val platformType: PlatformType = PlatformType.ANDROID
        override val major: Int = 1
        override val minor: Int = 2
        override val patch: Int = 3
        override val name: String = "1.2.3-test"
    }

    private class FakeEventRepository : EventRepository {
        private var nextId = 1L

        override suspend fun storeEntity(data: StorableEvent): Pair<Long, Long> {
            val id = nextId++
            return Pair(id, id)
        }

        override suspend fun storeEntities(dataList: List<StorableEvent>) = Unit

        override suspend fun removeEntitiesByDate(cutoffMS: Long) = Unit

        override suspend fun fetchNextBatchAndMark(batchSize: Int): List<StorableEvent> = emptyList()

        override suspend fun markEntitiesById(idList: List<Long>) = Unit

        override suspend fun unMarkEntitiesById(idList: List<Long>) = Unit

        override suspend fun deleteEntities(idList: List<StorableEvent>) = Unit

        override suspend fun getAll(): List<StorableEvent> = emptyList()

        override suspend fun getAllUnMark(): List<StorableEvent> = emptyList()

        override fun close() = Unit
    }
}
