package io.justtrack

import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class RetryingTaskTest {
    private val errorClassifier = object : ErrorClassifier {
        override fun unrecoverable(exception: Throwable): Boolean {
            return false
        }
    }

    @JvmField
    @Rule
    var globalTimeout: Timeout = Timeout.seconds(10)

    @Test
    fun doesNotChangeAnything() = runBlocking {
        val mock = TaskMock { "done" }
        val task: Task<String> = RetryingTask(mock, MockDeviceInfo(), TestLogger(), 3, errorClassifier, null)
        task.execute()
        Assert.assertEquals(1, mock.resolvedValues.size)
        Assert.assertEquals(0, mock.thrownErrors.size)
        Assert.assertEquals("done", mock.resolvedValues[0])
    }

    @Test
    fun rejectThenResolve() = runBlocking {
        val innerTask = ResolveAfterNTask(2)
        val mock = TaskMock { innerTask.execute() }
        val task: Task<String> = RetryingTask(mock, MockDeviceInfo(), TestLogger(), 3, errorClassifier, null)
        task.execute()
        Assert.assertEquals(1, mock.resolvedValues.size)
        Assert.assertEquals(2, mock.thrownErrors.size)
        Assert.assertEquals("done", mock.resolvedValues[0])
        Assert.assertEquals(ResolveAfterNTask.err, mock.thrownErrors[0])
        Assert.assertEquals(ResolveAfterNTask.err, mock.thrownErrors[1])
    }

    @Test
    fun notCalledTooOften(): Unit = runBlocking {
        val innerTask = ResolveAfterNTask(20)
        val mock = TaskMock { innerTask.execute() }
        val task: Task<String> = RetryingTask(mock, MockDeviceInfo(), TestLogger(), 3, errorClassifier, null)
        try {
            task.execute()
        } catch (e: Throwable) {
            Assert.assertEquals(ResolveAfterNTask.err, e)
            Assert.assertEquals(0, mock.resolvedValues.size)
            Assert.assertEquals(4, mock.thrownErrors.size)
            Assert.assertEquals(ResolveAfterNTask.err, mock.thrownErrors[0])
            Assert.assertEquals(ResolveAfterNTask.err, mock.thrownErrors[1])
            Assert.assertEquals(ResolveAfterNTask.err, mock.thrownErrors[2])
            Assert.assertEquals(ResolveAfterNTask.err, mock.thrownErrors[3])
        }
    }
}
