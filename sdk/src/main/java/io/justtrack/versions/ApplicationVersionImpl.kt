package io.justtrack.versions

import io.justtrack.ApplicationVersion
import java.util.Objects

internal class ApplicationVersionImpl internal constructor(
    private val name: String? = null,
    private val code: String? = null,
) : ApplicationVersion {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ApplicationVersion
        return name == that.getVersionName() && code == that.getVersionCode()
    }

    override fun hashCode(): Int {
        return Objects.hash(name, code)
    }

    override fun getVersionName(): String {
        return this.name ?: ""
    }

    override fun getVersionCode(): String {
        return this.code ?: ""
    }
}
