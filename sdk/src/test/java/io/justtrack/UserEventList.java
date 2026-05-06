package io.justtrack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

import io.justtrack.events.JtAdEvent;
import io.justtrack.events.JtAppInstallEvent;
import io.justtrack.events.JtAppOpenEvent;
import io.justtrack.events.JtDeeplinkHandledEvent;
import io.justtrack.events.JtDeeplinkNotHandledEvent;
import io.justtrack.events.JtLoginEvent;
import io.justtrack.events.JtProgressionEvent;
import io.justtrack.events.JtPurchaseEvent;
import io.justtrack.events.JtResourceEvent;
import io.justtrack.events.JtSessionTrackingEvent;
import io.justtrack.events.JtTrackingPermissionEvent;
import io.justtrack.events.TimeUnitGroup;

public class UserEventList {
    public static Collection<AppEvent> getEventsList() {
        Collection<AppEvent> events = new ArrayList<>();

        events.add(new JtSessionTrackingEvent(UUID.randomUUID().toString(), "start", 0.0, TimeUnitGroup.MILLISECONDS, new Date()));
        events.add(new JtAppOpenEvent(UUID.randomUUID().toString(), 1.0, TimeUnitGroup.MILLISECONDS, new Date()));
        events.add(new JtAppInstallEvent(UUID.randomUUID().toString(), 1.0, TimeUnitGroup.MILLISECONDS, new Date()));
        events.add(new JtDeeplinkHandledEvent(UUID.randomUUID().toString(), "url", new Date()));
        events.add(new JtDeeplinkNotHandledEvent(UUID.randomUUID().toString(), "url", new Date()));
        events.add(new JtTrackingPermissionEvent("requested", new Date()));
        events.add(new JtProgressionEvent("start", "level name", null, null));
        events.add(new JtResourceEvent("sink", "item type", "item name", "item id"));
        events.add(new JtPurchaseEvent("click", "product id", "token", "purchase", 1.0));

        events.add(
                new JtAdEvent(
                        "click",
                        "bundle id",
                        "ad instance name",
                        "ad network",
                        "ad placement",
                        "ad sdk name",
                        "ad segment name",
                        "ad unit",
                        "test group",
                        2.0,
                        TimeUnitGroup.MILLISECONDS
                )
        );
        events.add(new JtLoginEvent("success", "provider"));

        return events;

    }
}
