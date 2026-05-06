package io.justtrack;

import org.junit.Test;

import io.justtrack.log.Logger;

public class DefaultLoggerTest {
    @Test
    public void testLogger() {
        Logger logger = new LoggerImpl();
        logger.debug("test");
        logger.info("test");
        logger.warn("test");
        logger.error("test");
        logger.error("test", new RuntimeException("testing logging an error"));
    }
}
