package io.justtrack.integrations.firebase

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import io.justtrack.JustTrackSdk
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.log.Logger

class FirebaseIntegrationAdapter : IntegrationAdapter {
    override fun integrate(
        context: Context,
        sdk: JustTrackSdk,
        logger: Logger,
    ) {
        val instance = FirebaseAnalytics.getInstance(context)
        instance.appInstanceId.addOnSuccessListener { id ->
            if (id != null) {
                sdk.setFirebaseAppInstanceId(id)
            }
        }.addOnFailureListener { exception ->
            logger.error("FirebaseIntegrationAdapter: Unable to integrate", exception)
        }
    }
}
