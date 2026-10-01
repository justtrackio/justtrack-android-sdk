package io.justtrack.config

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
internal class RemoteConfigStoreImplTest {

    private lateinit var store: RemoteConfigStoreImpl

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(RemoteConfigStoreImpl.STORE_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = RemoteConfigStoreImpl(context)
    }

    @After
    fun tearDown() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(RemoteConfigStoreImpl.STORE_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    // region fetch interval

    @Test
    fun getCurrentFetchInterval_returnsDefaultWhenUnset() {
        assertEquals(RemoteConfigStoreImpl.DEFAULT_MIN_FETCH_INTERVAL, store.getCurrentFetchInterval())
    }

    @Test
    fun setMinimumIntervalInSecond_persistsValue() {
        store.setMinimumIntervalInSecond(60L)

        assertEquals(60L, store.getCurrentFetchInterval())
    }

    // endregion

    // region previous fetch timestamp

    @Test
    fun getPreviousFetchTimeStamp_returnsNullWhenUnset() {
        assertNull(store.getPreviousFetchTimeStamp())
    }

    @Test
    fun setPreviousFetchTimeStamp_persistsValueAndReturnsIt() {
        store.setPreviousFetchTimeStamp(1_234L)

        assertEquals(1_234L, store.getPreviousFetchTimeStamp())
    }

    // endregion

    // region retry-after

    @Test
    fun getRetryAfterSeconds_returnsNullWhenUnset() {
        assertNull(store.getRetryAfterSeconds())
    }

    @Test
    fun setRetryAfterSeconds_persistsValue() {
        store.setRetryAfterSeconds(42)

        assertEquals(42, store.getRetryAfterSeconds())
    }

    @Test
    fun setRetryAfterSeconds_consumedSentinelStillReadable() {
        store.setRetryAfterSeconds(RemoteConfigStoreImpl.CONSUMED_RETRY_AFTER)

        assertEquals(RemoteConfigStoreImpl.CONSUMED_RETRY_AFTER, store.getRetryAfterSeconds())
    }

    // endregion

    // region assignments

    @Test
    fun getStoredAssignments_returnsNullWhenUnset() {
        assertNull(store.getStoredAssignments())
    }

    @Test
    fun setStoredAssignments_persistsAndReturnsAssignmentsByKey() {
        val assignments = JSONArray()
            .put(JSONObject().put("configKey", "a").put("configValue", "1").put("experimentId", "e1").put("pending", false))
            .put(JSONObject().put("configKey", "b").put("configValue", "2").put("experimentId", "e2").put("pending", true))
        val raw = JSONObject().put("assignments", assignments).toString()

        store.setStoredAssignments(raw)

        val stored = store.getStoredAssignments()
        assertEquals(2, stored?.size)
        assertEquals("1", stored?.get("a")?.configValue)
        assertEquals(false, stored?.get("a")?.isPending)
        assertEquals(true, stored?.get("b")?.isPending)
    }

    @Test
    fun setStoredAssignments_nullClearsExistingEntries() {
        store.setStoredAssignments(
            JSONObject()
                .put("assignments", JSONArray().put(JSONObject().put("configKey", "a").put("configValue", "1").put("experimentId", "e1")))
                .toString(),
        )

        store.setStoredAssignments(null)

        assertNull(store.getStoredAssignments())
    }

    @Test
    fun setStoredAssignments_emptyArrayStoresEmptyMap() {
        val raw = JSONObject().put("assignments", JSONArray()).toString()

        store.setStoredAssignments(raw)

        val stored = store.getStoredAssignments()
        assertTrue("Expected empty map, was $stored", stored != null && stored.isEmpty())
    }

    // endregion
}
