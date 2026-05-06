package io.justtrack

import io.justtrack.log.Logger
import java.util.UUID

internal interface HttpLogger : Logger, AutoCloseable {
    /**
     * Set the advertiser id to send to the server. As this is lazily loaded, we initially send
     * a sentinel value until this value could be fetched.
     *
     * @param advertiserId The advertiser id of the user.
     */
    fun setAdvertiserId(advertiserId: String)

    /**
     * Set the user id to send to the server. As this is lazily loaded, we only send this value after
     * it could be fetched.
     *
     * @param userId    Future of user id of the user.
     * @param installInstanceId Future of install id of the user.
     */
    fun setUser(userId: AsyncFuture<UUID?>, installInstanceId: AsyncFuture<String?>)

    /**
     * Set the user id to send to the server. As this is lazily loaded, we only send this value after
     * it could be fetched.
     *
     * @param userId    The user id of the user.
     * @param installId The install id of the user.
     */
    fun setUser(userId: UUID?, installId: String)

    /**
     * Set the config form server to limit the logs and metrics sent to backend.
     *
     * @param config The config containing the log and metric rules.
     */
    fun setLogAndMetricRules(config: DTOAttributionOutputSdkConfig)

    /**
     * Send aggregated logs to the server.
     */
    fun sendToServer()

    /**
     * set the breadcrumb manager for storing all message and metric.
     *
     * @param reporter The breadcrumb manager.
     */
    fun setBreadCrumbReporter(reporter: BreadCrumbReporter?)
}
