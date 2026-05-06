package io.justtrack

import androidx.annotation.VisibleForTesting
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider

@VisibleForTesting
internal fun IntegrityTokenProvider.Companion.mockGoogleTokenProvider(provider: StandardIntegrityTokenProvider) {
    sGoogleIntegrityProvider = provider
}
