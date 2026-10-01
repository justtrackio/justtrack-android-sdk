package io.justtrack.events

import io.justtrack.AppEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class JtPurchaseEventTest {

    @Test
    fun constructorWithStringActionAndCount() {
        val event = JtPurchaseEvent("click", "product_123", "token_abc", "inapp", 5.0)

        val expected = AppEvent(JtPurchaseEvent.NAME)
            .addDimension(Dimension.JT_ACTION, "click")
            .addDimension(Dimension.JT_PRODUCT_ID, "product_123")
            .addDimension(Dimension.JT_TOKEN, "token_abc")
            .addDimension(Dimension.JT_PRODUCT_TYPE, "inapp")
            .setValue(5.0, Unit.COUNT)

        assertEquals(expected, event)
        assertEquals(
            "[AppEvent jt_purchase, " +
                "dimensions = [jt_action = click, jt_product_id = product_123, jt_token = token_abc, jt_product_type = inapp], " +
                "value = 5.0 count, " +
                "happenedAt = now]",
            event.toString(),
        )
    }

    @Test
    fun constructorWithStringActionWithoutCount() {
        val event = JtPurchaseEvent("view", "product_456", "token_def", "subs")

        val expected = AppEvent(JtPurchaseEvent.NAME)
            .addDimension(Dimension.JT_ACTION, "view")
            .addDimension(Dimension.JT_PRODUCT_ID, "product_456")
            .addDimension(Dimension.JT_TOKEN, "token_def")
            .addDimension(Dimension.JT_PRODUCT_TYPE, "subs")

        assertEquals(expected, event)
        assertEquals(
            "[AppEvent jt_purchase, " +
                "dimensions = [jt_action = view, jt_product_id = product_456, jt_token = token_def, jt_product_type = subs], " +
                "value = 0.0 null, " +
                "happenedAt = now]",
            event.toString(),
        )
    }

    @Test
    fun constructorWithEnumActionAndCount() {
        val event = JtPurchaseEvent(
            JtPurchaseEvent.Action.CLICK,
            "product_789",
            "token_ghi",
            "inapp",
            3.0,
        )

        val expected = AppEvent(JtPurchaseEvent.NAME)
            .addDimension(Dimension.JT_ACTION, "click")
            .addDimension(Dimension.JT_PRODUCT_ID, "product_789")
            .addDimension(Dimension.JT_TOKEN, "token_ghi")
            .addDimension(Dimension.JT_PRODUCT_TYPE, "inapp")
            .setValue(3.0, Unit.COUNT)

        assertEquals(expected, event)
    }

    @Test
    fun constructorWithEnumActionWithoutCount() {
        val event = JtPurchaseEvent(
            JtPurchaseEvent.Action.VIEW,
            "product_000",
            "token_jkl",
            "subs",
        )

        val expected = AppEvent(JtPurchaseEvent.NAME)
            .addDimension(Dimension.JT_ACTION, "view")
            .addDimension(Dimension.JT_PRODUCT_ID, "product_000")
            .addDimension(Dimension.JT_TOKEN, "token_jkl")
            .addDimension(Dimension.JT_PRODUCT_TYPE, "subs")

        assertEquals(expected, event)
    }

    @Test
    fun constructorWithNullToken() {
        val event = JtPurchaseEvent("click", "product_123", null, "inapp", 1.0)

        val expected = AppEvent(JtPurchaseEvent.NAME)
            .addDimension(Dimension.JT_ACTION, "click")
            .addDimension(Dimension.JT_PRODUCT_ID, "product_123")
            .addDimension(Dimension.JT_TOKEN, null)
            .addDimension(Dimension.JT_PRODUCT_TYPE, "inapp")
            .setValue(1.0, Unit.COUNT)

        assertEquals(expected, event)
        // null token should be removed from dimensions
        assertEquals(
            "[AppEvent jt_purchase, " +
                "dimensions = [jt_action = click, jt_product_id = product_123, jt_product_type = inapp], " +
                "value = 1.0 count, " +
                "happenedAt = now]",
            event.toString(),
        )
    }

    @Test
    fun constructorWithNullTokenWithoutCount() {
        val event = JtPurchaseEvent(
            JtPurchaseEvent.Action.VIEW,
            "product_456",
            null,
            "subs",
        )

        val expected = AppEvent(JtPurchaseEvent.NAME)
            .addDimension(Dimension.JT_ACTION, "view")
            .addDimension(Dimension.JT_PRODUCT_ID, "product_456")
            .addDimension(Dimension.JT_TOKEN, null)
            .addDimension(Dimension.JT_PRODUCT_TYPE, "subs")

        assertEquals(expected, event)
        // null token should be removed from dimensions
        assertEquals(
            "[AppEvent jt_purchase, " +
                "dimensions = [jt_action = view, jt_product_id = product_456, jt_product_type = subs], " +
                "value = 0.0 null, " +
                "happenedAt = now]",
            event.toString(),
        )
    }

    @Test
    fun eventNameIsCorrect() {
        val event = JtPurchaseEvent("click", "product_id", "token", "inapp")
        assertEquals("jt_purchase", event.name)
    }

    @Test
    fun enumActionValuesAreCorrect() {
        assertEquals("view", JtPurchaseEvent.Action.VIEW.value)
        assertEquals("click", JtPurchaseEvent.Action.CLICK.value)
    }

    @Test
    fun stringAndEnumActionProduceSameEvent() {
        val stringEvent = JtPurchaseEvent("click", "product_id", "token", "inapp", 2.0)
        val enumEvent = JtPurchaseEvent(
            JtPurchaseEvent.Action.CLICK,
            "product_id",
            "token",
            "inapp",
            2.0,
        )

        assertEquals(stringEvent, enumEvent)
    }

    @Test
    fun stringAndEnumActionProduceSameEventWithoutCount() {
        val stringEvent = JtPurchaseEvent("view", "product_id", "token", "subs")
        val enumEvent = JtPurchaseEvent(
            JtPurchaseEvent.Action.VIEW,
            "product_id",
            "token",
            "subs",
        )

        assertEquals(stringEvent, enumEvent)
    }

    @Test
    fun validatePassesForValidEvent() {
        val event = JtPurchaseEvent("click", "product_id", "token_abc", "inapp", 1.0)
        event.validate()
    }
}
