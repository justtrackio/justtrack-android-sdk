package io.justtrack;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Future;

import io.justtrack.attribution.AdvertiserIdInfo;
import io.justtrack.providers.AdvertiserIdProviderImpl;

/**
 * Class to encapsulate getting an {@link AdvertiserIdInfo} {@link Future}. Allows us to easily lock
 * this class without having to fear that we deadlock anything (we used to lock the {@link BaseJustTrackSdk}
 * for this - this lead to a problem when you needed to lock the SDK to process events, but another
 * thread had the SDK locked while blocking on publishing a new event as the blocking queue was full).
 */
class AdvertiserIdReader {
    @Nullable
    private AsyncFuture<AdvertiserIdInfo> advertiserIdInfo;

    @NonNull
    // Deadlock-Safety: executeAsFuture is not locking anything (besides the executor maybe).
    synchronized AsyncFuture<AdvertiserIdInfo> provideAdvertiserIdFuture(
            @NonNull TaskExecutor taskExecutor,
            @NonNull Context context,
            @NonNull HttpLogger logger
    ) {
        if (advertiserIdInfo == null) {
            advertiserIdInfo = taskExecutor.executeAsFuture(new AdvertiserIdReaderTask(new AdvertiserIdProviderImpl(context), logger));
        }

        return advertiserIdInfo;
    }
}
