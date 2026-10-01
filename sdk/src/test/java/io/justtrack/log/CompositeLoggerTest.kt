package io.justtrack.log

import io.justtrack.Metric
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class CompositeLoggerTest {
    private val defaultLogger: Logger = mock()
    private val customLogger: Logger = mock()
    private val field1: LoggerFields = mock()
    private val field2: LoggerFields = mock()
    private val throwable = RuntimeException("boom")
    private val metric: Metric = mock()

    private fun composite() = CompositeLogger(defaultLogger, customLogger)

    @Test
    fun `debug forwards to both loggers with all fields`() {
        composite().debug("msg", field1, field2)
        verify(defaultLogger).debug("msg", field1, field2)
        verify(customLogger).debug("msg", field1, field2)
    }

    @Test
    fun `info forwards to both loggers with all fields`() {
        composite().info("msg", field1, field2)
        verify(defaultLogger).info("msg", field1, field2)
        verify(customLogger).info("msg", field1, field2)
    }

    @Test
    fun `warn forwards to both loggers with all fields`() {
        composite().warn("msg", field1, field2)
        verify(defaultLogger).warn("msg", field1, field2)
        verify(customLogger).warn("msg", field1, field2)
    }

    @Test
    fun `warn with exception forwards to both loggers`() {
        composite().warn("msg", throwable, field1, field2)
        verify(defaultLogger).warn("msg", throwable, field1, field2)
        verify(customLogger).warn("msg", throwable, field1, field2)
    }

    @Test
    fun `error forwards to both loggers with all fields`() {
        composite().error("msg", field1, field2)
        verify(defaultLogger).error("msg", field1, field2)
        verify(customLogger).error("msg", field1, field2)
    }

    @Test
    fun `error with exception forwards to both loggers`() {
        composite().error("msg", throwable, field1, field2)
        verify(defaultLogger).error("msg", throwable, field1, field2)
        verify(customLogger).error("msg", throwable, field1, field2)
    }

    @Test
    fun `publishMetric forwards to both loggers with all dimensions`() {
        composite().publishMetric(metric, 1.5, field1, field2)
        verify(defaultLogger).publishMetric(metric, 1.5, field1, field2)
        verify(customLogger).publishMetric(metric, 1.5, field1, field2)
    }

    @Test
    fun `fallback returns this`() {
        val composite = composite()
        assertSame(composite, composite.fallback)
    }

    @Test
    fun `null defaultLogger and null customLogger - all methods are no-ops`() {
        val composite = CompositeLogger(null, null)
        composite.debug("a", field1)
        composite.info("a", field1)
        composite.warn("a", field1)
        composite.warn("a", throwable, field1)
        composite.error("a", field1)
        composite.error("a", throwable, field1)
        composite.publishMetric(metric, 0.0, field1)
        // also covers the null-branch for both arms in every method
    }

    @Test
    fun `only defaultLogger present - customLogger never invoked`() {
        val composite = CompositeLogger(defaultLogger, null)
        composite.debug("a")
        composite.info("a")
        composite.warn("a")
        composite.warn("a", throwable)
        composite.error("a")
        composite.error("a", throwable)
        composite.publishMetric(metric, 1.0)

        verify(defaultLogger).debug("a")
        verify(defaultLogger).info("a")
        verify(defaultLogger).warn("a")
        verify(defaultLogger).warn("a", throwable)
        verify(defaultLogger).error("a")
        verify(defaultLogger).error("a", throwable)
        verify(defaultLogger).publishMetric(metric, 1.0)
        verifyNoInteractions(customLogger)
    }

    @Test
    fun `only customLogger present - defaultLogger never invoked`() {
        val composite = CompositeLogger(null, customLogger)
        composite.debug("a")
        composite.info("a")
        composite.warn("a")
        composite.warn("a", throwable)
        composite.error("a")
        composite.error("a", throwable)
        composite.publishMetric(metric, 1.0)

        verify(customLogger).debug("a")
        verify(customLogger).info("a")
        verify(customLogger).warn("a")
        verify(customLogger).warn("a", throwable)
        verify(customLogger).error("a")
        verify(customLogger).error("a", throwable)
        verify(customLogger).publishMetric(metric, 1.0)
        verifyNoInteractions(defaultLogger)
    }
}
