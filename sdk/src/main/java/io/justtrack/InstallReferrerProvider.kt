package io.justtrack

import io.justtrack.installreferrer.api.ReferrerDetails

internal fun interface InstallReferrerProvider {
    fun newTask(): Task<ReferrerDetails?>
}
