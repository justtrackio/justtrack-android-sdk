package io.justtrack.config

import io.justtrack.AsyncFuture

/**
 * Remote config functionality for retrieving and activating experiment assignments.
 */
interface RemoteConfig {
    /**
     * Fetches remote config values from the server if the minimum fetch interval has elapsed.
     *
     * @return AsyncFuture completed when the experiment is activated
     * @throws Exception if there was an error fetching the remote config values.
     */
    fun fetch(): AsyncFuture<Void?>

    /**
     * Activates experiment assignments by confirming enrollment with the server.
     *
     * @param experiments list of experiment ids to activate
     * @return AsyncFuture completed when the experiment is activated
     * @throws Exception if there was an error activating the experiment.
     */
    fun activate(experiments: List<String>): AsyncFuture<Void?>

    /**
     * A convenience method that fetches and then activates all pending experiments in one call.
     *
     * @return AsyncFuture completed when the experiment is activated
     * @throws Exception if there was an error fetching or activating the experiments.
     */
    fun fetchAndActivate(): AsyncFuture<Void?>

    /**
     * Applies configuration settings for remote config.
     *
     * @param settings The configuration settings to apply.
     */
    fun setConfig(settings: JusttrackRemoteConfigSettings)

    /**
     * Returns all remote config values.
     *
     * @return list of all remote config values
     */
    fun getAll(): List<Assignment>?

    /**
     * Return the config value as boolean.
     *
     * @param configKey The key of the config value.
     * @return Config value as Boolean. If the config value is not a valid Boolean or is not found, null is returned.
     */
    fun getBoolean(configKey: String): Boolean?

    /**
     * Return the config value as Double.
     *
     * @param configKey The key of the config value.
     * @return Config value as Double. If the config value is not a valid Double or is not found, null is returned.
     */
    fun getDouble(configKey: String): Double?

    /**
     * Return the config value as Int.
     *
     * @param configKey The key of the config value.
     * @return Config value as Int. If the config value is not a valid Int or is not found, null is returned.
     */
    fun getInt(configKey: String): Int?

    /**
     * Return the config value as Long.
     *
     * @param configKey The key of the config value.
     * @return Config value as Long. If the config value is not a valid Long or is not found, null is returned.
     */
    fun getLong(configKey: String): Long?

    /**
     * Return the config value as String.
     *
     * @param configKey The key of the config value.
     * @return Config value as String. If the config value is not a valid String or is not found, null is returned.
     */
    fun getString(configKey: String): String?
}
