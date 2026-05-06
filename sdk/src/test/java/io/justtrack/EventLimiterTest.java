package io.justtrack;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import junit.framework.TestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.justtrack.log.Logger;

@RunWith(Parameterized.class)
public class EventLimiterTest extends TestCase {
    @Parameterized.Parameters
    public static Object[][] data() throws JSONException {
        JSONObject caughtDimensions = new JSONObject();
        caughtDimensions.put("caughtKey", "caughtValue");

        JSONObject uncaughtDimensions = new JSONObject();
        uncaughtDimensions.put("uncaughtKey", "uncaughtValue");

        List<DTOAttributionOutputSdkRule> baseRules = Arrays.asList(
                new DTOAttributionOutputSdkRule("caught", true, new JSONObject()),
                new DTOAttributionOutputSdkRule("caughtWithRule", true, caughtDimensions)
        );

        Map<String, Double> logRuleMap = new HashMap<>();
        logRuleMap.put(LogLevel.DEBUG.toString(), 100.0);
        logRuleMap.put(LogLevel.INFO.toString(), 100.0);
        logRuleMap.put(LogLevel.WARN.toString(), 0.0);
        logRuleMap.put(LogLevel.ERROR.toString(), 0.0);

        DTOAttributionOutputSdkLog logRule = new DTOAttributionOutputSdkLog(baseRules, logRuleMap);
        DTOAttributionOutputSdkMetric metricRule = new DTOAttributionOutputSdkMetric(baseRules);
        DTOAttributionOutputSdkEvent eventRule = new DTOAttributionOutputSdkEvent(new ArrayList<>());
        DTOAttributionOutputSdkConfig sdkConfig = new DTOAttributionOutputSdkConfig(logRule, metricRule, eventRule);

        return new Object[][]{
                new Object[]{
                        /*
                         reduceMessage : empty percentage rule, empty list -> return empty list
                         filterByRules : empty config, empty list -> return empty list
                         */

                        null,
                        new ArrayList<DTOLogMessage>(),
                        0,
                        new ArrayList<DTOLogMetric>(),
                        new ArrayList<DTOAppEventEvent>(),
                        0
                },
                new Object[]{
                        /*
                         reduceMessage : empty percentage rule, fill message list -> return full list
                         filterByRules : empty config, fill metric -> return full list
                         */

                        null,
                        Arrays.asList(
                                new DTOLogMessage(LogLevel.INFO, "messageInfo", caughtDimensions, Calendar.getInstance().getTime()),
                                new DTOLogMessage(LogLevel.DEBUG, "messageDebug", caughtDimensions, Calendar.getInstance().getTime())),
                        2,
                        Arrays.asList(
                                new DTOLogMetric("metric1", caughtDimensions, 10.0, "unit", Calendar.getInstance().getTime()),
                                new DTOLogMetric("metric2", caughtDimensions, 10.0, "unit", Calendar.getInstance().getTime())),
                        new ArrayList<DTOAppEventEvent>(),
                        2
                },
                new Object[]{
                        /*
                        reduceMessage : remove Error, Warn, three message -> return 1 INFO message
                        filterByRules : remove caught metric, three metric -> return two metric
                        */

                        sdkConfig,
                        Arrays.asList(
                                new DTOLogMessage(LogLevel.INFO, "messageInfo", uncaughtDimensions, Calendar.getInstance().getTime()),
                                new DTOLogMessage(LogLevel.WARN, "messageWarn", uncaughtDimensions, Calendar.getInstance().getTime()),
                                new DTOLogMessage(LogLevel.ERROR, "messageError", uncaughtDimensions, Calendar.getInstance().getTime())),
                        1,
                        Arrays.asList(
                                new DTOLogMetric("metric1", caughtDimensions, 10.0, "unit", Calendar.getInstance().getTime()),
                                new DTOLogMetric("metric2", uncaughtDimensions, 10.0, "unit", Calendar.getInstance().getTime()),
                                new DTOLogMetric("caught", uncaughtDimensions, 10.0, "unit", Calendar.getInstance().getTime())),
                        new ArrayList<DTOAppEventEvent>(),
                        2

                },
                new Object[]{
                        /*
                        reduceMessage : remove Error, Warn & remove caught with dimension, 4 message -> return 1 message
                        filterByRules :  remove caught with dimension, 3 message -> return 1 message
                        */

                        sdkConfig,
                        Arrays.asList(
                                new DTOLogMessage(LogLevel.INFO, "messageInfo", uncaughtDimensions, Calendar.getInstance().getTime()),
                                new DTOLogMessage(LogLevel.WARN, "messageWarn", uncaughtDimensions, Calendar.getInstance().getTime()),
                                new DTOLogMessage(LogLevel.ERROR, "messageError", uncaughtDimensions, Calendar.getInstance().getTime()),
                                new DTOLogMessage(LogLevel.INFO, "caughtWithRule", caughtDimensions, Calendar.getInstance().getTime())),
                        1,
                        Arrays.asList(
                                new DTOLogMetric("metric1", uncaughtDimensions, 10.0, "unit", Calendar.getInstance().getTime()),
                                new DTOLogMetric("uncaught", caughtDimensions, 10.0, "unit", Calendar.getInstance().getTime()),
                                new DTOLogMetric("caughtWithRule", caughtDimensions, 10.0, "unit", Calendar.getInstance().getTime())),
                        new ArrayList<DTOAppEventEvent>(),
                        2

                }
        };
    }

    @Parameterized.Parameter(0)
    public DTOAttributionOutputSdkConfig sdkRule;
    @Parameterized.Parameter(1)
    public List<DTOLogMessage> messages;
    @Parameterized.Parameter(2)
    public int messageRemaining;
    @Parameterized.Parameter(3)
    public List<DTOLogMetric> metrics;
    @Parameterized.Parameter(4)
    public List<DTOAppEventEvent> userEvents;
    @Parameterized.Parameter(5)
    public int filterByRuleRemain;

    @Test
    public void filterByRules() {
        Logger loggerMock = mock(Logger.class);
        Collection<DTOLogMetric> result;
        if (sdkRule != null) {
            result = EventLimiter.filterByRules(metrics, sdkRule.getMetric().getRules(), loggerMock, LogMetricDatum::new);
        } else {
            result = EventLimiter.filterByRules(metrics, new ArrayList<>(), loggerMock, LogMetricDatum::new);
        }
        assertEquals(filterByRuleRemain, result.size());
        verify(loggerMock, times(0)).info(any(), any());
    }

    @Test
    public void isRemoveByRule_sameName_emptyList_notDrop() throws JSONException {
        String eventName = "testEvent";
        JSONObject dimensions = new JSONObject();
        JSONObject mainRuleObj = new JSONObject();
        JSONObject ruleObj = new JSONObject();
        JSONArray ruleArray = new JSONArray();
        ruleObj.put("dimensions", dimensions);
        ruleObj.put("rule", eventName);
        ruleObj.put("drop", false);
        ruleArray.put(ruleObj);
        mainRuleObj.put("rules", ruleArray);

        DTOAttributionOutputSdkEvent rules = new DTOAttributionOutputSdkEvent(mainRuleObj);
        boolean isRemoved = EventLimiter.shouldDropByRules(eventName, dimensions, rules.getRules());
        assertFalse(isRemoved);
    }

    @Test
    public void isRemoveByRule_sameName_fillDim_emptyRule_false() throws JSONException {
        String eventName = "testEvent";
        JSONObject dimensions = new JSONObject();
        dimensions.put("test1", "value1");

        JSONObject mainRuleObj = new JSONObject();
        JSONObject ruleObj = new JSONObject();
        JSONArray ruleArray = new JSONArray();

        ruleObj.put("dimensions", new JSONObject());
        ruleObj.put("rule", eventName);
        ruleObj.put("drop", true);

        ruleArray.put(ruleObj);
        mainRuleObj.put("rules", ruleArray);
        DTOAttributionOutputSdkEvent rules = new DTOAttributionOutputSdkEvent(mainRuleObj);
        boolean isRemoved = EventLimiter.shouldDropByRules(eventName, dimensions, rules.getRules());
        assertTrue(isRemoved);

        ruleObj.put("drop", false);
        ruleArray = new JSONArray();
        mainRuleObj = new JSONObject();
        ruleArray.put(ruleObj);
        mainRuleObj.put("rules", ruleArray);
        rules = new DTOAttributionOutputSdkEvent(mainRuleObj);
        isRemoved = EventLimiter.shouldDropByRules(eventName, dimensions, rules.getRules());
        assertFalse(isRemoved);
    }

    @Test
    public void isRemoveByRule_sameName_fillDim_fillRule_true() throws JSONException {
        String eventName = "testEvent";
        JSONObject dimensions = new JSONObject();
        dimensions.put("test1", "value1");
        dimensions.put("test2", "value2");
        dimensions.put("test3", "value3");

        JSONObject mainRuleObj = new JSONObject();
        JSONObject ruleObj = new JSONObject();
        JSONArray ruleArray = new JSONArray();

        ruleObj.put("dimensions", dimensions);
        ruleObj.put("rule", eventName);
        ruleObj.put("drop", true);

        ruleArray.put(ruleObj);
        mainRuleObj.put("rules", ruleArray);
        DTOAttributionOutputSdkEvent rules = new DTOAttributionOutputSdkEvent(mainRuleObj);
        boolean isRemoved = EventLimiter.shouldDropByRules(eventName, dimensions, rules.getRules());
        assertTrue(isRemoved);
    }

    @Test
    public void isRemoveByRule_sameName_fillDim_fillRule_false() throws JSONException {

        JSONObject dimensions = new JSONObject();
        dimensions.put("test1", "value1");
        dimensions.put("test2", "value2");
        dimensions.put("test3", "value3");

        JSONObject ruleDimensions = new JSONObject();
        ruleDimensions.put("wrong", "value1");

        JSONObject mainRuleObj = new JSONObject();
        JSONObject ruleObj = new JSONObject();
        JSONArray ruleArray = new JSONArray();

        String eventName = "testEvent";

        ruleObj.put("dimensions", ruleDimensions);
        ruleObj.put("rule", eventName);
        ruleObj.put("drop", true);

        ruleArray.put(ruleObj);
        mainRuleObj.put("rules", ruleArray);
        DTOAttributionOutputSdkEvent rules = new DTOAttributionOutputSdkEvent(mainRuleObj);
        boolean isRemoved = EventLimiter.shouldDropByRules(eventName, dimensions, rules.getRules());
        assertFalse(isRemoved);
    }

    @Test
    public void compareJsonObject_common_return_false() throws JSONException {
        JSONObject obj1 = new JSONObject();
        obj1.put("test", "result1");
        JSONObject obj2 = new JSONObject();
        obj2.put("test", "result2");
        assertFalse(EventLimiter.hasRequiredDimensions(obj1, obj2));

        obj2.put("test2", "result1");
        assertFalse(EventLimiter.hasRequiredDimensions(obj1, obj2));
    }

    @Test
    public void compareJsonObject_emptyList_return_false() throws JSONException {
        JSONObject obj1 = new JSONObject();
        JSONObject obj2 = new JSONObject();
        obj2.put("test", "value1");
        assertFalse(EventLimiter.hasRequiredDimensions(obj1, obj2));
    }

    @Test
    public void compareJsonObject_duplicate() throws JSONException {
        JSONObject obj1 = new JSONObject();
        obj1.put("test", "result1");
        obj1.put("test", "result1");
        JSONObject obj2 = new JSONObject();
        obj2.put("test", "result1");
        assertTrue(EventLimiter.hasRequiredDimensions(obj1, obj2));
    }

    @Test
    public void compareJsonObject_sameKey_differentValue_false() throws JSONException {
        JSONObject obj1 = new JSONObject();
        obj1.put("test", "result1");
        obj1.put("test", "result1");
        JSONObject obj2 = new JSONObject();
        obj2.put("test", "result2");
        assertFalse(EventLimiter.hasRequiredDimensions(obj1, obj2));
    }

    @Test
    public void compareJsonObject_differentKey_sameValue_false() throws JSONException {
        JSONObject obj1 = new JSONObject();
        obj1.put("test1", "result1");
        obj1.put("test1", "result1");
        JSONObject obj2 = new JSONObject();
        obj2.put("test", "result1");
        assertFalse(EventLimiter.hasRequiredDimensions(obj1, obj2));
    }

    @Test
    public void compareJsonObject_long_list() throws JSONException {
        JSONObject obj1 = new JSONObject();
        obj1.put("test5", "result6");
        obj1.put("test6", "result7");

        JSONObject obj2 = new JSONObject();
        obj2.put("test5", "result6");
        obj2.put("test6", "result7");
        obj2.put("test7", "result8");
        assertFalse(EventLimiter.hasRequiredDimensions(obj1, obj2));

        obj1.put("test7", "result8");
        assertTrue(EventLimiter.hasRequiredDimensions(obj1, obj2));
    }
}