package io.justtrack.config

import org.json.JSONObject

/**
 * Class representing a single config value.
 * @param configValue The value of the config.
 */
data class Assignment internal constructor(
    /**
     * he config key associated with this assignment.
     */
    val configKey: String,
    /**
     * The raw config value for this assignment.
     */
    val configValue: String,
    /**
     * The unique identifier of the experiment (UUID format).
     */
    val experimentId: String,
    /**
     * Whether the config is pending for activation.
     */
    val isPending: Boolean,
) {
    /**
     * Constructor for Assignment from a JSONObject.
     */
    internal constructor(jsonObject: JSONObject) : this(
        jsonObject.getString("configKey"),
        jsonObject.getString("configValue"),
        jsonObject.getString("experimentId"),
        jsonObject.optBoolean("pending", true),
    )

    /**
     * parse Assignment to JSONObject.
     */
    internal fun toJson(): JSONObject {
        val assignmentJson = JSONObject()
        assignmentJson.put("configKey", configKey)
        assignmentJson.put("configValue", configValue)
        assignmentJson.put("experimentId", experimentId)
        assignmentJson.put("pending", isPending)
        return assignmentJson
    }

    internal companion object {
        fun parseAssignments(jsonObject: JSONObject): List<Assignment> {
            val assignments = jsonObject.optJSONArray("assignments") ?: return emptyList()
            val configList = ArrayList<Assignment>(assignments.length())
            for (index in 0 until assignments.length()) {
                val assignment = assignments.getJSONObject(index)
                configList.add(Assignment(assignment))
            }
            return configList
        }
    }
}
