package io.justtrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.database.Database
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseAttributionTest {
    private lateinit var databaseInterface: DatabaseInterface

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        databaseInterface = DatabaseInterface(context, LoggerImpl())
    }

    @Test
    @Throws(Exception::class)
    fun test_IntegrityTokenSent() = runBlocking {
        val isSent = true
        databaseInterface.openAttribution().use {
            it.setIntegrityTokenSent(isSent)
        }
        var result: Boolean?
        databaseInterface.openAttribution().use {
            result = it.isIntegrityTokenSent()
        }
        Assert.assertEquals(isSent, result)
    }

    @Test
    @Throws(Exception::class)
    fun test_IntegritySecret() = runBlocking {
        val secret = UUID.randomUUID().toString()
        databaseInterface.openAttribution().use {
            it.setIntegritySecret(secret)
        }
        var result: String?
        databaseInterface.openAttribution().use {
            result = it.getIntegritySecret()
        }
        Assert.assertEquals(secret, result)
    }
}
