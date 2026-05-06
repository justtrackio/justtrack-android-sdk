package io.justtrack.integrations

import android.content.Context
import io.justtrack.JustTrackSdk
import io.justtrack.log.Logger

/**
 * Adapter interface for integrating third-party ad or analytics SDKs with the justtrack SDK.
 */
interface IntegrationAdapter {
    /**
     * Performs the integration of the third-party SDK.
     *
     * @param context The application context.
     * @param sdk     The justtrack SDK instance to integrate with.
     * @param logger  A logger for diagnostic output during integration.
     */
    fun integrate(context: Context, sdk: JustTrackSdk, logger: Logger)
}
