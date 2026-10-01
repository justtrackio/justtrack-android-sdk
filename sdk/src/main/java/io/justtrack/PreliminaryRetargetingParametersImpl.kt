package io.justtrack

import android.content.Intent
import io.justtrack.retargeting.PreliminaryRetargetingParameters
import io.justtrack.retargeting.PreliminaryRetargetingParameters.ValidateResult

internal class PreliminaryRetargetingParametersImpl private constructor(url: String, parameters: Map<String, String>) :
    RetargetingParametersImpl(true, url, parameters), PreliminaryRetargetingParameters, Callback<ValidateResult> {
    private val validatedResult = ResolvableFuture<ValidateResult>()

    override fun validate(): AsyncFuture<ValidateResult?> {
        return validatedResult
    }

    override fun resolve(response: ValidateResult) {
        validatedResult.resolve(response)
    }

    override fun reject(exception: Throwable) {
        validatedResult.reject(exception)
    }

    companion object {
        @JvmStatic
        fun fromIntent(intent: Intent?): PreliminaryRetargetingParametersImpl? {
            if (intent == null || intent.action != Intent.ACTION_VIEW) {
                return null
            }

            val url = intent.dataString
            val extras = intent.extras

            return if (url == null || extras == null) {
                null
            } else {
                val parameters: MutableMap<String, String> = HashMap()

                for (key in extras.keySet()) {
                    val value = extras.getString(key)
                    if (value != null) {
                        parameters[key] = value
                    }
                }

                PreliminaryRetargetingParametersImpl(url, parameters)
            }
        }
    }
}
