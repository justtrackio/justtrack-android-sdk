package io.justtrack

internal enum class DeviceType(private val type: String) {
    PHONE("phone"),
    TABLET("tablet"),
    ;

    override fun toString(): String = type
}
