package io.justtrack.versions

import io.justtrack.PlatformType
import io.justtrack.Version

internal interface SdkVersion : Version {
    val platformType: PlatformType
}
