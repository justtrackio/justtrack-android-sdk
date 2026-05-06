package io.justtrack.versions

import io.justtrack.PlatformType

internal data class SdkVersionImpl internal constructor(
    override val major: Int = 0,
    override val minor: Int = 0,
    override val patch: Int = 0,
    override val name: String = "$major.$minor.$patch",
    override val platformType: PlatformType,
) : SdkVersion {

    internal constructor(
        major: Int,
        minor: Int,
        patch: Int,
        name: String,
    ) : this(major, minor, patch, name, PlatformType.ANDROID)
}
