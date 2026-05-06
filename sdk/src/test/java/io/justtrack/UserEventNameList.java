package io.justtrack;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

class UserEventNameList {
    /**
     * Map containing all events defined by the SDK.
     */
    @NonNull static final Set<String> ALL_EVENTS;

    static {
        ALL_EVENTS = new HashSet<>();
        ALL_EVENTS.add("jt_session_tracking");
        ALL_EVENTS.add("jt_app_open");
        ALL_EVENTS.add("jt_app_install");
        ALL_EVENTS.add("jt_deeplink_handled");
        ALL_EVENTS.add("jt_deeplink_not_handled");
        ALL_EVENTS.add("jt_tracking_permission");
        ALL_EVENTS.add("jt_progression");
        ALL_EVENTS.add("jt_resource");
        ALL_EVENTS.add("jt_purchase");
        ALL_EVENTS.add("jt_ad");
        ALL_EVENTS.add("jt_login");
        ALL_EVENTS.add("jt_ad");
        ALL_EVENTS.add("jt_purchase");
    }
}
