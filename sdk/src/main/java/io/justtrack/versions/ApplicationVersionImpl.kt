package io.justtrack.versions

import io.justtrack.ApplicationVersion

internal data class ApplicationVersionImpl(
    private val name: String? = null,
    private val code: String? = null,
) : ApplicationVersion {

    override fun getVersionName(): String = this.name ?: ""

    override fun getVersionCode(): String = this.code ?: ""
}
