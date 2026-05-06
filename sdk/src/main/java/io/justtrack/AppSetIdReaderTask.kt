package io.justtrack

import android.content.Context
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdInfo
import io.justtrack.log.Logger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class AppSetIdReaderTask(private val context: Context, private val logger: Logger) :
    Task<AppSetIdInfo?> {
    override suspend fun execute(): AppSetIdInfo? = suspendCoroutine { continuation ->
        val continued = AtomicBoolean()

        try {
            val appSetIdClient = AppSet.getClient(context)
            appSetIdClient.appSetIdInfo
                .addOnSuccessListener { response: AppSetIdInfo? ->
                    if (!continued.getAndSet(true)) {
                        continuation.resume(
                            response,
                        )
                    }
                }
                .addOnFailureListener { e: Exception ->
                    logger.warn("Failed to read appSet id", e)
                    if (!continued.getAndSet(true)) {
                        continuation.resume(null)
                    }
                }
        } catch (e: Throwable) {
            // there is not really much we can do in this case...
            logger.warn("Failed to read appSet id", e)
            if (!continued.getAndSet(true)) {
                continuation.resume(null)
            }
        }
    }
}
