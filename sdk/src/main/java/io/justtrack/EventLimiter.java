package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.justtrack.log.Logger;
import io.justtrack.log.LoggerFieldsBuilder;

class EventLimiter {
    private static final String DROP_LOG_PREFIX = "EventLimiter: Dropping ";

    static Collection<DTOLogMessage> filterLogMessages(
            @NonNull Collection<DTOLogMessage> messages,
            @Nullable DTOAttributionOutputSdkLog percentage,
            @NonNull Logger logger) {
        return percentage != null ? filterByRules(messages, percentage.getRules(), logger, LogMessageDatum::new) : messages;
    }

    static <B, T extends NamedDatum> List<B> filterByRules(
            @NonNull Collection<B> dataList,
            @NonNull List<DTOAttributionOutputSdkRule> rules,
            @Nullable Logger logger,
            @NonNull Function<B, T> wrap) {
        List<B> reducedList = new ArrayList<>();
        for (B base : dataList) {
            T data = wrap.apply(base);
            if (shouldDropByRules(data.getDatum(), data.getDimensions(), rules)) {
                if (data instanceof DTOLogMessage) {
                    logDroppedMessage((DTOLogMessage) data, logger);
                } else if (data instanceof DTOAppEventEvent) {
                    logDroppedEvent((DTOAppEventEvent) data, logger);
                } else {
                    //ignore for metric
                }
            } else {
                reducedList.add(base);
            }
        }

        return reducedList;
    }

    @VisibleForTesting
    static boolean shouldDropByRules(
            @NonNull String name,
            @NonNull JSONObject dimensions,
            @NonNull Iterable<DTOAttributionOutputSdkRule> rules) {
        for (DTOAttributionOutputSdkRule rule : rules) {
            try {
                if (name.matches(rule.getRule()) && hasRequiredDimensions(dimensions, rule.getDimensions())) {
                    return rule.isDrop();
                }
            } catch (JSONException ignored) {
                // Send all data to the backend in case of an error
            }
        }

        return false;
    }

    static boolean hasRequiredDimensions(
            @NonNull JSONObject actualDimensions,
            @NonNull JSONObject requiredDimensions) throws JSONException {
        for (String requiredDimensionKey : (Iterable<String>) requiredDimensions::keys) {
            if (!actualDimensions.has(requiredDimensionKey)) {
                return false;
            }

            String actualDimensionsValue = actualDimensions.getString(requiredDimensionKey);
            String requiredDimensionValue = requiredDimensions.getString(requiredDimensionKey);
            if (!actualDimensionsValue.matches(requiredDimensionValue)) {
                return false;
            }
        }

        return true;
    }

    static void logDroppedEvent(@NonNull DTOAppEventEvent event, @Nullable Logger logger) {
        if (logger == null) {
            return;
        }
        Map<String, String> dimensionsMap = new HashMap<>();
        Iterator<String> dimensionKeys = event.getDimensions().keys();
        while (dimensionKeys.hasNext()) {
            String key = dimensionKeys.next();
            try {
                dimensionsMap.put(key, event.getDimensions().getString(key));
            } catch (JSONException exception) {
                logger.warn(
                        "Failed to add dimension for eventDroppedDatum",
                        new LoggerFieldsBuilder()
                                .with("dimension", key)
                                .with("dimensions", event.getDimensions().toString())
                );
            }
        }
    }

    private static void logDroppedMessage(@NonNull DTOLogMessage message, @Nullable Logger logger) {
        if (logger == null) {
            return;
        }

        LoggerFieldsBuilder loggerFields = new LoggerFieldsBuilder();
        for (String fieldName : (Iterable<String>) message.getFields()::keys) {
            try {
                loggerFields.with(fieldName, message.getFields().getString(fieldName));
            } catch (JSONException exception) {
                loggerFields.with(fieldName, "failed to decode field: " + exception);
            }
        }

        logger.info(DROP_LOG_PREFIX + message.getMessage(), loggerFields);
    }

    @Nullable
    static DTOAppEvent filterEvents(
            @NonNull DTOAppEvent event,
            @Nullable List<DTOAttributionOutputSdkRule> eventRules,
            @Nullable Logger logger) {
        if (eventRules == null) {
            return event;
        }

        List<DTOAppEventEvent> newEvents = filterByRules(event.getEvents(), eventRules, logger, AppEventEventDatum::new);

        if (newEvents.isEmpty()) {
            return null;
        }

        return new DTOAppEvent(
                event.getAppVersion(),
                event.getSdkVersion(),
                event.getUser(),
                event.getDevice(),
                newEvents
        );
    }

    interface Function<T, R> {
        R apply(T input);
    }
}
