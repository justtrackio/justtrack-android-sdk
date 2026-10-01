package io.justtrack

import android.content.Context
import android.os.Bundle
import android.os.RemoteException
import io.justtrack.installreferrer.api.InstallReferrerClient
import io.justtrack.installreferrer.api.InstallReferrerStateListener
import io.justtrack.installreferrer.api.ReferrerDetails
import io.justtrack.log.LoggerFieldsBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class InstallReferrerReader(
    private val context: Context,
    private val bundle: Bundle?,
    private val logger: HttpLogger,
) : InstallReferrerProvider {

    override fun newTask(): Task<ReferrerDetails?> = object : Task<ReferrerDetails?> {
        override suspend fun execute(): ReferrerDetails? {
            if (bundle != null) {
                return ReferrerDetails(bundle)
            }

            val referrerClient = InstallReferrerClient.newBuilder(context).build()
            val wasResumed = AtomicBoolean(false)

            return suspendCoroutine { continuation ->
                referrerClient.startConnection(
                    object : InstallReferrerStateListener {
                        override fun onInstallReferrerSetupFinished(responseCode: Int) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val referrerDetails =
                                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                                            logger.debug(
                                                "Retrieved Install Referrer",
                                                LoggerFieldsBuilder().with("installReferrer", referrerClient.installReferrer.installReferrer),
                                            )
                                            referrerClient.installReferrer
                                        } else {
                                            logger.debug(
                                                "Failed to retrieve Install Referrer",
                                                LoggerFieldsBuilder().with("responseCode", responseCode),
                                            )
                                            null
                                        }
                                    if (referrerClient.isReady) {
                                        referrerClient.endConnection()
                                    }

                                    if (!wasResumed.getAndSet(true)) {
                                        continuation.resume(referrerDetails)
                                    }
                                } catch (e: RemoteException) {
                                    try {
                                        referrerClient.endConnection()
                                    } catch (exception: IllegalArgumentException) {
                                        e.addSuppressed(exception)
                                        logger.warn("InstallReferrer: Unable to endConnection, the connection is probably closed already", exception)
                                    }

                                    if (!wasResumed.getAndSet(true)) {
                                        logger.warn("Failed to retrieve Install Referrer with error", e)
                                        continuation.resumeWithException(e)
                                    }
                                } catch (e: IllegalArgumentException) {
                                    if (!wasResumed.getAndSet(true)) {
                                        logger.warn("Failed to retrieve Install Referrer with error", e)
                                        continuation.resumeWithException(e)
                                    }
                                }
                            }
                        }

                        override fun onInstallReferrerServiceDisconnected() {
                            try {
                                referrerClient.endConnection()
                            } catch (exception: IllegalArgumentException) {
                                logger.warn("InstallReferrer: Unable to endConnection, the connection is probably closed already", exception)
                            }

                            if (!wasResumed.getAndSet(true)) {
                                continuation.resume(null)
                            }
                        }
                    },
                )
            }
        }
    }
}
