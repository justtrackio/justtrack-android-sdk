package io.justtrack

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.util.ExecutorServiceFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class ExperimentVariantTest {
    lateinit var context: Context

    internal val formatter = Formatter
    private val executorBuilder = ExecutorServiceFactory {
        val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
        executor.allowCoreThreadTimeOut(true)
        executor
    }

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun test_calling_api() = runBlocking {
        val experiment = "experiment1"
        val variant = "variant1"
        val sdk = TestSdk(context, executorBuilder, false)
        sdk.start()

        sdk.setExperimentVariant(experiment, variant, listOf(), null).await()

        try {
            sdk.setExperimentVariant(
                experiment,
                variant,
                listOf("tag1", "tag2", "tag3", "tag4", "tag5", "tag6"),
                Date(),
            ).await()
            Assert.fail()
        } catch (exception: Exception) {
        }

        try {
            sdk.setExperimentVariant(
                experiment,
                variant,
                listOf("tag1", "tag2", "tag3 ¡", "tag4", "tag5"),
                Date(),
            ).await()
            Assert.fail()
        } catch (exception: Exception) {
        }

        try {
            sdk.setExperimentVariant("¡", variant, listOf("tag1", "tag2", "tag3", "tag4", "tag5"), Date()).await()
            Assert.fail()
        } catch (exception: Exception) {
        }

        try {
            sdk.setExperimentVariant(experiment, "§", listOf("tag1", "tag2", "tag3", "tag4", "tag5"), Date()).await()
            Assert.fail()
        } catch (exception: Exception) {
        }
    }
}
