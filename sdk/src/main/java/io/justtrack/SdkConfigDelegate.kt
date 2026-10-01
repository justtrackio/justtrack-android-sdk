package io.justtrack

import io.justtrack.providers.AdvertiserIdProvider

internal class SdkConfigDelegate internal constructor(
    private val firebaseIdManager: FirebaseIdManager,
    private val attributionIdManager: AttributionIdManager,
) {

    @JvmName("applyingConfig")
    internal fun applyingConfig(config: JustTrackSdkConfig, userIdFuture: AsyncFuture<String>, advertiserIdProvider: AdvertiserIdProvider) {
        if (config.firebaseAppInstanceId != null) {
            setFirebaseAppInstanceId(
                config.firebaseAppInstanceId,
                userIdFuture,
                advertiserIdProvider,
            )
        }
    }

    @JvmName("setFirebaseAppInstanceId")
    internal fun setFirebaseAppInstanceId(
        firebaseAppInstanceId: String,
        userIdFuture: AsyncFuture<String>,
        advertiserIdProvider: AdvertiserIdProvider,
    ): AsyncFuture<Boolean> {
        return firebaseIdManager.setFirebaseId(
            attributionIdManager,
            FirebaseIdManager.AttributionParams(
                userIdFuture,
                advertiserIdProvider,
                firebaseAppInstanceId,
            ),
        )
    }
}
