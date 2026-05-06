package io.justtrack.config

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentTest {
    @Test
    fun parseAssignments_returnsEmptyListWhenMissingArray() {
        val assignments = Assignment.parseAssignments(JSONObject())

        assertTrue(assignments.isEmpty())
    }

    @Test
    fun parseAssignments_readsAssignmentsWithDefaults() {
        val assignmentsJson = JSONArray()
        assignmentsJson.put(
            JSONObject()
                .put("configKey", "config_a")
                .put("configValue", "value_a")
                .put("experimentId", "exp-a")
                .put("pending", false),
        )
        assignmentsJson.put(
            JSONObject()
                .put("configKey", "config_b")
                .put("configValue", "value_b")
                .put("experimentId", "exp-b"),
        )

        val root = JSONObject().put("assignments", assignmentsJson)
        val assignments = Assignment.parseAssignments(root)

        assertEquals(2, assignments.size)
        assertEquals(false, assignments[0].isPending)
        assertEquals(true, assignments[1].isPending)
    }

    @Test
    fun toJson_preservesAssignmentFields() {
        val assignment = Assignment("config_a", "value_a", "exp-a", true)
        val json = assignment.toJson()

        assertEquals("config_a", json.getString("configKey"))
        assertEquals("value_a", json.getString("configValue"))
        assertEquals("exp-a", json.getString("experimentId"))
        assertEquals(true, json.getBoolean("pending"))
    }
}
