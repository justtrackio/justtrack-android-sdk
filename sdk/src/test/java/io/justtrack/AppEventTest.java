package io.justtrack;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;

import io.justtrack.events.JtAdInternalEvent;
import io.justtrack.events.Money;
import io.justtrack.exceptions.InvalidFieldException;

public class AppEventTest {
    @Test
    public void callAllPredefinedEvents() {
        // verify events
        Collection<String> allEvents = new HashSet<>(UserEventNameList.ALL_EVENTS);
        for (AppEvent publishableAppEvent : UserEventList.getEventsList()) {
            Assert.assertNotNull(publishableAppEvent);
            String name = publishableAppEvent.getName();
            Assert.assertTrue("Duplicate test for " + name, allEvents.contains(name));
            allEvents.remove(name);
        }
        StringBuilder missingEvents = new StringBuilder();
        for (String missingEvent : allEvents) {
            missingEvents.append(missingEvent).append(", ");
        }
        Assert.assertTrue("Missing events: " + missingEvents, allEvents.isEmpty());
    }

    @Test
    public void allPredefinedEventContainJTPrefix() {
        for (AppEvent appEvent : UserEventList.getEventsList()) {
            Assert.assertNotNull(appEvent);
            String name = appEvent.getName();

            Assert.assertTrue(name.startsWith("jt_"));

            for (String dimension : appEvent.getDimensions().keySet()) {
                Assert.assertTrue(dimension.startsWith("jt_"));
            }
        }
    }

    @Test
    public void ignoreEmptyDimensionName() throws InvalidFieldException {
        AppEvent customEvent = new AppEvent("custom");
        for (int i = 0; i < 10; i++) {
            customEvent.addDimension("dim_" + i, "value_" + i);
        }
        customEvent.validate();

        customEvent.addDimension("", "ignore");
        customEvent.validate();

        customEvent.addDimension("dim_11", "value_11");
        try {
            customEvent.validate();
            Assert.fail("Should not be reached");
        } catch (InvalidFieldException exception) {
            Assert.assertNotNull(exception.getMessage());
            Assert.assertTrue("Unexpected message: " + exception.getMessage(), exception.getMessage().contains("Too many dimensions"));
        }
    }

    @Test
    public void ignoreEmptyDimensionValue() throws InvalidFieldException {
        AppEvent customEvent = new AppEvent("custom");
        for (int i = 0; i < 10; i++) {
            customEvent.addDimension("dim_" + i, "value_" + i);
        }
        customEvent.validate();

        customEvent.addDimension("dim_11", "");
        customEvent.validate();

        customEvent.addDimension("dim_11", "value_11");
        try {
            customEvent.validate();
            Assert.fail("Should not be reached");
        } catch (InvalidFieldException exception) {
            Assert.assertNotNull(exception.getMessage());
            Assert.assertTrue("Unexpected message: " + exception.getMessage(), exception.getMessage().contains("Too many dimensions"));
        }
    }

    @Test
    public void customEventLimitedToTenDimension() throws InvalidFieldException {
        AppEvent customEvent = new AppEvent("custom");
        for (int i = 0; i < 10; i++) {
            customEvent.addDimension("dim_" + i, "value_" + i);
        }
        customEvent.validate();

        customEvent.addDimension("dim_11", "value_11");
        try {
            customEvent.validate();
            Assert.fail("Should not be reached");
        } catch (InvalidFieldException exception) {
            Assert.assertNotNull(exception.getMessage());
            Assert.assertTrue("Unexpected message: " + exception.getMessage(), exception.getMessage().contains("Too many dimensions"));
        }
    }

    @Test
    public void dimensionsWithoutValueDelete() throws InvalidFieldException {
        AppEvent customEvent = new AppEvent("custom");
        for (int i = 0; i <= 10; i++) {
            customEvent.addDimension("dim_" + i, "value_" + i);
        }

        try {
            customEvent.validate();
            Assert.fail("Should not be reached");
        } catch (InvalidFieldException exception) {
            Assert.assertNotNull(exception.getMessage());
            Assert.assertTrue("Unexpected message: " + exception.getMessage(), exception.getMessage().contains("Too many dimensions"));
        }

        customEvent.addDimension("dim_10", null);
        customEvent.validate();
    }

    @Test
    public void predefinedEventLimitedToTenDimension() throws InvalidFieldException {
        JtAdInternalEvent event = new JtAdInternalEvent(
                "success",
                "bundle_id",
                "instance_name",
                "ad_network",
                "placement",
                "ad_sdk",
                "segment",
                "unit",
                "test_group",
                new Money(10.0, "USD"),
                new Date()
        );
        for (int i = 0; i < 2; i++) {
            event.addDimension("dim_" + i, "value_" + i);
        }
        try {
            event.validate();
            Assert.fail("Should not be reached");
        } catch (InvalidFieldException exception) {
            Assert.assertNotNull(exception.getMessage());
            Assert.assertTrue("Unexpected message: " + exception.getMessage(), exception.getMessage().contains("Too many dimensions"));
        }

        event.removeDimension("dim_0");
        event.validate();
    }
}
