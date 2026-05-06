package io.justtrack

import io.justtrack.attribution.AdvertiserIdInfo

internal class SdkConfigDelegate internal constructor(
    private val firebaseIdManager: FirebaseIdManager,
    private val attributionIdManager: AttributionIdManager,
) {

    @JvmName("applyingConfig")
    internal fun applyingConfig(
        config: JustTrackSdkConfig,
        userIdFuture: AsyncFuture<String>,
        advertiserIdInfoFuture: AsyncFuture<AdvertiserIdInfo>,
    ) {
        if (config.firebaseAppInstanceId != null) {
            setFirebaseAppInstanceId(
                config.firebaseAppInstanceId,
                userIdFuture,
                advertiserIdInfoFuture,
            )
        }
    }

    @JvmName("setFirebaseAppInstanceId")
    internal fun setFirebaseAppInstanceId(
        firebaseAppInstanceId: String,
        userIdFuture: AsyncFuture<String>,
        advertiserIdInfoFuture: AsyncFuture<AdvertiserIdInfo>,
    ): AsyncFuture<Boolean> {
        return firebaseIdManager.setFirebaseId(
            attributionIdManager,
            FirebaseIdManager.AttributionParams(
                userIdFuture,
                advertiserIdInfoFuture,
                firebaseAppInstanceId,
            ),
        )
    }
}
