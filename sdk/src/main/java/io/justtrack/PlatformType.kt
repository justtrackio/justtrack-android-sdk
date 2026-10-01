package io.justtrack

/**
 * Describes the environment the SDK is embedded into (plain Android, Unity wrapper, etc.)
 */
enum class PlatformType(
    private val label: String,
    /**
     * The base OS name understood by backend mappings.
     */
    val platform: String,
    /**
     * The wrapper/bridge identifier if the SDK is loaded through one.
     * */
    val wrapper: String?,
) {
    /** Plain Android without any wrapper. */
    ANDROID("Android", "android", null),

    /** Android running inside a Unity wrapper. */
    UNITY("Unity; Android", "android", "unity"),

    /** Android running inside a React Native wrapper. */
    REACT_NATIVE("ReactNative; Android", "android", "react-native"),

    /** Android running inside a Flutter wrapper. */
    FLUTTER("Flutter; Android", "android", "flutter"),

    /** Android running inside a Godot wrapper. */
    GODOT("Godot; Android", "android", "godot"),
    ;

    override fun toString(): String {
        return label
    }

    /** Factory methods for [PlatformType]. */
    companion object {
        /**
         * Parses a [PlatformType] from its string representation. Returns [ANDROID] if no match is found.
         *
         * @param string The string to parse.
         * @return The matching [PlatformType], defaulting to [ANDROID].
         */
        fun fromString(string: String): PlatformType {
            for (type in entries) {
                if (type.toString() == string) {
                    return type
                }
            }
            return ANDROID
        }
    }
}
