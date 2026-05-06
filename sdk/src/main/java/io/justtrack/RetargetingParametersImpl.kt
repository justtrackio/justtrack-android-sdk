package io.justtrack

import android.net.Uri
import io.justtrack.retargeting.RetargetingParameters
import java.util.Collections

internal open class RetargetingParametersImpl(private val appWasAlreadyInstalled: Boolean, uri: String, parameters: Map<String, String>) :
    RetargetingParameters {
    override val uri: Uri? = Uri.parse(uri)
    override val parameters: Map<String, String> = Collections.unmodifiableMap(parameters)

    override fun wasAlreadyInstalled(): Boolean {
        return appWasAlreadyInstalled
    }

    override val promotionParameter: String?
        get() {
            if (!parameters.containsKey(PROMO_CODE)) {
                return null
            }

            val code = parameters[PROMO_CODE]
            return if (TextUtils.isNullOrEmpty(code)) null else code
        }

    companion object {
        private const val PROMO_CODE = "promo_code"
    }
}
