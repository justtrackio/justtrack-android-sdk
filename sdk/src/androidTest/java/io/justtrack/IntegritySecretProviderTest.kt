package io.justtrack

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.database.Database
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.UUID

internal class IntegritySecretProviderTest {
    lateinit var context: Context
    private lateinit var databaseInterface: DatabaseInterface

    @Before
    fun createDb() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        JustTrack.resetForTesting()
        databaseInterface = DatabaseInterface(context, LoggerImpl())
    }

    @Test
    fun provideStoredSecret() = runBlocking {
        val installInstanceIdFuture = TestAsyncFuture(UUID.randomUUID().toString())
        val provider = IntegritySecretProvider(TaskExecutorTest(), databaseInterface)
        val result = provider.getIntegritySecret(installInstanceIdFuture).execute()
        val storedResult = provider.getIntegritySecret(installInstanceIdFuture).execute()

        Assert.assertEquals(result, storedResult)
    }
}
