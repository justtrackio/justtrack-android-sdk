package io.justtrack

import android.content.Context
import io.justtrack.events.Dimension
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
internal class GlobalDimensionsRepoTest {

    private lateinit var repo: GlobalDimensionsRepo

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
    fun testSetAndGetDimension() {
        repo.set(Dimension.JT_GLOBAL_0, "warrior")

        val all = repo.getAll()
        assertEquals("warrior", all["jt_global_0"])
    }

    @Test
    fun testSetMultipleDimensions() {
        repo.set(Dimension.JT_GLOBAL_0, "warrior")
        repo.set(Dimension.JT_GLOBAL_1, "experiment_a")
        repo.set(Dimension.JT_GLOBAL_2, "level_5")

        val all = repo.getAll()
        assertEquals(3, all.size)
        assertEquals("warrior", all["jt_global_0"])
        assertEquals("experiment_a", all["jt_global_1"])
        assertEquals("level_5", all["jt_global_2"])
    }

    @Test
    fun testClearDimensionWithNull() {
        repo.set(Dimension.JT_GLOBAL_0, "warrior")
        assertEquals("warrior", repo.getAll()["jt_global_0"])

        repo.set(Dimension.JT_GLOBAL_0, null)
        assertNull(repo.getAll()["jt_global_0"])
    }

    @Test
    fun testUpdateDimension() {
        repo.set(Dimension.JT_GLOBAL_1, "old_value")
        repo.set(Dimension.JT_GLOBAL_1, "new_value")

        val all = repo.getAll()
        assertEquals("new_value", all["jt_global_1"])
    }

    @Test
    fun testPersistenceAcrossInstances() {
        repo.set(Dimension.JT_GLOBAL_0, "persisted")
        repo.set(Dimension.JT_GLOBAL_2, "also_persisted")

        // Create a new repo instance with the same SharedPreferences
        val context = RuntimeEnvironment.getApplication()
        val newRepo = GlobalDimensionsRepo(
            context.getSharedPreferences(GlobalDimensionsRepo.STORE_NAME, Context.MODE_PRIVATE),
        )
        val all = newRepo.getAll()
        assertEquals("persisted", all["jt_global_0"])
        assertEquals("also_persisted", all["jt_global_2"])
    }

    @Test
    fun testGetAllReturnsEmptyWhenNothingSet() {
        val all = repo.getAll()
        assertTrue(all.isEmpty())
    }
}
