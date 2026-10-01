package io.justtrack

import io.justtrack.executor.TaskExecutor
import java.util.concurrent.RejectedExecutionException

internal class AttributionSubscriptionHandlerImpl(
    private val attributionSubscriptions: SubscriptionManager<AttributionListener>,
    private val logger: HttpLogger,
    private val networkErrorLogger: NetworkErrorLogger,
    private val taskExecutor: TaskExecutor,
) : AttributionSubscriptionHandler {
    override fun callAttributionSubscriptions(storedResponse: AttributionResponse) {
        attributionSubscriptions.call { listener ->
            taskExecutor.execute(
                { listener.onAttributionReceived(AttributionImpl(storedResponse)) },
                { exception: RejectedExecutionException ->
                    networkErrorLogger.logException(
                        logger,
                        exception,
                        "Could not call attribution subscription, SDK is shutting",
                    )
                },
                false,
            )
        }
    }
}
