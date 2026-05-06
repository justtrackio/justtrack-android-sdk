package io.justtrack

internal data class AppVersionUpdateInfo(
    val appInstallVersion: ApplicationVersion,
    val appLastVersion: ApplicationVersion,
    val kind: AppVersionUpdateKind,
)
