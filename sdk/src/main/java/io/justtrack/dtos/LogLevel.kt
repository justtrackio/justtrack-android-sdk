package io.justtrack.dtos

internal enum class LogLevel(private val level: String) {
    DEBUG("debug"),
    INFO("info"),
    WARN("warn"),
    ERROR("error"),
    ;

    override fun toString(): String = level
}
