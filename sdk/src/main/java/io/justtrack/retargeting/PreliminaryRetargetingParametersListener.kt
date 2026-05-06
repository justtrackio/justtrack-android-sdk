package io.justtrack.retargeting

import io.justtrack.JustTrackSdk

/**
 * Listener for preliminary retargeting parameter updates.
 */
interface PreliminaryRetargetingParametersListener {
    /**
     * Called every time we retrieve new preliminary retargeting parameters (if you register before the first call to
     * [JustTrackSdk.preliminaryRetargetingParameters] it is also called for that response).
     *
     * @param preliminaryRetargetingParameters The current Preliminary retargeting parameters of the user.
     */
    fun onPreliminaryRetargetingParametersReceived(preliminaryRetargetingParameters: PreliminaryRetargetingParameters)
}
