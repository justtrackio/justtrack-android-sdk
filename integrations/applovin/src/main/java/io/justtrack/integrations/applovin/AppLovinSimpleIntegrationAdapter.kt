package io.justtrack.integrations.applovin

import android.app.Activity
import android.app.Application
import android.content.Context
import com.applovin.adview.AppLovinFullscreenActivity
import com.applovin.sdk.AppLovinSdk
import io.justtrack.ActivityBasedAdSdkIntegration
import io.justtrack.JustTrackSdk
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.util.SimpleActivityLifecycleCallbacks

class AppLovinSimpleIntegrationAdapter : IntegrationAdapter {
    override fun integrate(
        context: Context,
        sdk: JustTrackSdk,
        logger: Logger,
    ) {
        try {
            val activityClass =
                AppLovinFullscreenActivity::class.java

            val integration = ActivityBasedAdSdkIntegration(context, activityClass, "appLovin", sdk)

            (context.applicationContext as? Application)
                ?.registerActivityLifecycleCallbacks(
                    object : SimpleActivityLifecycleCallbacks() {
                        override fun onActivityResumed(activity: Activity) {
                            integration.onResume(activity)
                        }

                        override fun onActivityPaused(activity: Activity) {
                            integration.onPause(activity)
                        }
                    },
                )

            // report version & integration
            try {
                val version = AppLovinSdk.VERSION

                logger.info(
                    "Completed integration with AppLovin",
                    LoggerFieldsBuilder().with("version", version ?: "null"),
                )
            } catch (exception: Throwable) {
                logger.error(
                    "Completed AppLovin integration, but failed to determine version",
                    exception,
                )
            }
        } catch (exception: Throwable) {
            logger.error("Failed to setup AppLovin integration", exception)
        }
    }
}
