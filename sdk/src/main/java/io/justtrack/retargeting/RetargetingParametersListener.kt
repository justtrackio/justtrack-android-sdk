package io.justtrack.retargeting

import io.justtrack.JustTrackSdk

/**
 * Listener for retargeting parameter updates.
 */
interface RetargetingParametersListener {
    /**
     * Called every time we retrieve new retargeting parameters (if you register before the first call to
     * [JustTrackSdk.retargetingParameters] it is also called for that response).
     *
     * @param retargetingParameters The current retargeting parameters of the user.
     */
    fun onRetargetingParametersReceived(retargetingParameters: RetargetingParameters)
}
