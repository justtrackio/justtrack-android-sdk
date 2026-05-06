package io.justtrack.integrations.charboost

import android.app.Activity
import android.app.Application
import android.content.Context
import com.chartboost.sdk.view.CBImpressionActivity
import io.justtrack.ActivityBasedAdSdkIntegration
import io.justtrack.JustTrackSdk
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.util.SimpleActivityLifecycleCallbacks
import java.lang.reflect.InvocationTargetException

class ChartboostIntegrationAdapter : IntegrationAdapter {
    override fun integrate(
        context: Context,
        sdk: JustTrackSdk,
        logger: Logger,
    ) {
        try {
            val integration =
                ActivityBasedAdSdkIntegration(
                    context,
                    CBImpressionActivity::class.java,
                    "chartboost",
                    sdk,
                )

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
                val chartboostClass = Class.forName("com.chartboost.sdk.Chartboost")
                val getVersionMethod = chartboostClass.getDeclaredMethod("getSDKVersion")
                val version = getVersionMethod.invoke(null) as String?

                logger.info(
                    "Completed integration with Chartboost",
                    LoggerFieldsBuilder().with("version", version ?: "null"),
                )
            } catch (exception: ClassNotFoundException) {
                logger.error(
                    "Completed Chartboost integration, but failed to determine version",
                    exception,
                )
            } catch (exception: NoSuchMethodException) {
                logger.error(
                    "Completed Chartboost integration, but failed to determine version",
                    exception,
                )
            } catch (exception: IllegalAccessException) {
                logger.error(
                    "Completed Chartboost integration, but failed to determine version",
                    exception,
                )
            } catch (exception: InvocationTargetException) {
                logger.error(
                    "Completed Chartboost integration, but failed to determine version",
                    exception,
                )
            }
        } catch (exception: Throwable) {
            logger.error("Failed to setup Chartboost integration", exception)
        }
    }
}
