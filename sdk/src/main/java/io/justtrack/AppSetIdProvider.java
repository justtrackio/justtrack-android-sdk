package io.justtrack;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.appset.AppSetIdInfo;

import java.util.concurrent.Future;

import io.justtrack.executor.TaskExecutor;

/**
 * Class to encapsulate getting an {@link AppSetIdInfo} {@link Future}. Allows us to easily lock
 * this class without having to fear that we deadlock anything (we used to lock the {@link JustTrackSdkImpl}
 * for this - this lead to a problem when you needed to lock the SDK to process events, but another
 * thread had the SDK locked while blocking on publishing a new event as the blocking queue was full).
 */
class AppSetIdProvider {
    @Nullable
    private AsyncFuture<AppSetIdInfo> appSetIdInfoFuture;

    @NonNull
    // Deadlock-Safety: executeAsFuture is not locking anything (besides the executor maybe).
    synchronized AsyncFuture<AppSetIdInfo> provideAppSetIdFuture(
            @NonNull TaskExecutor taskExecutor,
            @NonNull Context context,
            @NonNull HttpLogger logger
    ) {
        if (appSetIdInfoFuture == null) {
            appSetIdInfoFuture = taskExecutor.executeFuture(
                    new AppSetIdReaderTask(context, logger)
            );
        }

        return appSetIdInfoFuture;
    }
}
