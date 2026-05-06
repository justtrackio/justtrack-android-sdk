package io.justtrack.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.AttributionDAOImpl
import io.justtrack.database.Database.Companion.DATABASE_NAME
import io.justtrack.log.Logger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logger: Logger = mock()

    @Before
    fun setup() {
        // Ensure a clean slate before each test
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun teardown() {
        // Clean up after each test
        context.deleteDatabase(DATABASE_NAME)
    }

    /**
     * Helper function to create a database at a specific version and optionally insert data.
     */
    private fun createDatabaseAtVersion(version: Int, insertData: ((db: Database) -> Unit)? = null): Database {
        // Create a custom Database class instance with the old version.
        val db = Database(context, logger, AttributionDAOImpl(logger), version = version)
        if (insertData != null) {
            insertData(db)
        }
        db.close()
        return db
    }

    @Test
    fun downgrade_from_7_to_6_clears_database() {
        // 1. Create a database at version 7 and insert some data
        createDatabaseAtVersion(version = 7) { db ->
            db.writableDatabase.execSQL("INSERT INTO message (level) VALUES ('INFO')")
        }

        // 2. Open the database at version 6, triggering onDowngrade
        val newDb = Database(context, logger, AttributionDAOImpl(logger), version = 6, isDebugModeEnabled = false)

        // 3. Verify that the MESSAGE table exists but is empty
        val messageCount = newDb.readableDatabase.rawQuery("SELECT COUNT(*) FROM message", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(0, messageCount)

        // 4. Verify that the tables were recreated by checking for the existence of one of them
        val messageTableExists = newDb.readableDatabase.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='message'",
            null,
        ).use { it.count > 0 }
        assertEquals(true, messageTableExists)

        newDb.close()
    }

    @Test
    fun downgrade_from_7_to_6_in_debug_causes_crash() {
        // 1. Create a database at version 7 and insert some data
        createDatabaseAtVersion(version = 7) { db ->
            db.writableDatabase.execSQL("INSERT INTO message (level) VALUES ('INFO')")
        }

        assertThrows(IllegalStateException::class.java) {
            // When trying to downgrade in debug mode, we should get a crash
            Database(context, logger, AttributionDAOImpl(logger), version = 6, isDebugModeEnabled = true)
        }
    }
}
