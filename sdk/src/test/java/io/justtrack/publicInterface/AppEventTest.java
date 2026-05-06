package io.justtrack.publicInterface;

import org.junit.Assert;
import org.junit.Test;

import io.justtrack.AppEvent;
import io.justtrack.events.Unit;

public class AppEventTest {
    @Test
    public void userEventCorrectSetters() {
        String eventName = "test_event_action";
        {
            AppEvent event = new AppEvent(eventName);
            Assert.assertEquals("[AppEvent test_event_action, value = 0.0 null, happenedAt = now]", event.toString());
        }
        {
            AppEvent event = new AppEvent(eventName, 2, Unit.COUNT);
            Assert.assertEquals("[AppEvent test_event_action, value = 2.0 count, happenedAt = now]", event.toString());
            AppEvent eventWithSetters = new AppEvent(eventName);
            eventWithSetters.setCount(2.0);
            Assert.assertEquals(event, eventWithSetters);
        }
        {
            AppEvent event = new AppEvent(eventName, 2, Unit.SECONDS);
            Assert.assertEquals(
                    "["
                            + "AppEvent test_event_action, "
                            + "value = 2000.0 milliseconds, "
                            + "happenedAt = now"
                            + "]",
                    event.toString()
            );
            AppEvent eventWithSetters = new AppEvent(eventName);
            eventWithSetters.setSeconds(2.0);
            Assert.assertEquals(event, eventWithSetters);
        }
        {
            AppEvent event = new AppEvent(eventName, 2, Unit.MILLISECONDS);
            Assert.assertEquals(
                    "["
                            + "AppEvent test_event_action, "
                            + "value = 2.0 milliseconds, "
                            + "happenedAt = now"
                            + "]",
                    event.toString()
            );
            AppEvent eventWithSetters = new AppEvent(eventName);
            eventWithSetters.setMilliseconds(2.0);
            Assert.assertEquals(event, eventWithSetters);
        }
        {
            AppEvent event = new AppEvent(eventName);
            event.addDimension("custom_1", "dim1");
            Assert.assertEquals(
                    "["
                            + "AppEvent test_event_action, "
                            + "dimensions = [custom_1 = dim1], "
                            + "value = 0.0 null, "
                            + "happenedAt = now"
                            + "]",
                    event.toString()
            );
            AppEvent eventWithSetters = new AppEvent(eventName);
            eventWithSetters.addDimension("custom_1", "dim1");
            Assert.assertEquals(event, eventWithSetters);
        }
        {
            AppEvent event = new AppEvent(eventName);
            event.addDimension("custom_1", "dim1");
            event.addDimension("custom_2", "dim2");
            Assert.assertEquals("["
                            + "AppEvent test_event_action, "
                            + "dimensions = [custom_1 = dim1, custom_2 = dim2], "
                            + "value = 0.0 null, "
                            + "happenedAt = now"
                            + "]",
                    event.toString()
            );
            AppEvent eventWithSetters = new AppEvent(eventName);
            eventWithSetters.addDimension("custom_1", "dim1");
            eventWithSetters.addDimension("custom_2", "dim2");
            Assert.assertEquals(event, eventWithSetters);
        }
        {
            AppEvent event = new AppEvent(eventName);
            event.addDimension("custom_1", "dim1");
            event.addDimension("custom_2", "dim2");
            event.addDimension("custom_3", "dim3");
            Assert.assertEquals(
                    "["
                            + "AppEvent test_event_action, "
                            + "dimensions = [custom_1 = dim1, custom_2 = dim2, custom_3 = dim3], "
                            + "value = 0.0 null, "
                            + "happenedAt = now"
                            + "]",
                    event.toString()
            );
            AppEvent eventWithSetters = new AppEvent(eventName);
            eventWithSetters.addDimension("custom_1", "dim1");
            eventWithSetters.addDimension("custom_2", "dim2");
            eventWithSetters.addDimension("custom_3", "dim3");
            Assert.assertEquals(event, eventWithSetters);
        }
        {
            AppEvent event = new AppEvent(eventName, 2, Unit.COUNT);
            event.addDimension("custom_1", "dim1");
            event.addDimension("custom_2", "dim2");
            event.addDimension("custom_3", "dim3");
            Assert.assertEquals(
                    "["
                            + "AppEvent test_event_action, "
                            + "dimensions = [custom_1 = dim1, custom_2 = dim2, custom_3 = dim3], "
                            + "value = 2.0 count, "
                            + "happenedAt = now"
                            + "]",
                    event.toString()
            );
            AppEvent eventWithSetters = new AppEvent(eventName);
            eventWithSetters.addDimension("custom_1", "dim1");
            eventWithSetters.addDimension("custom_2", "dim2");
            eventWithSetters.addDimension("custom_3", "dim3");
            eventWithSetters.setValue(2.0, Unit.COUNT);
            Assert.assertEquals(event, eventWithSetters);
        }
    }
}