package io.justtrack.integrity

import android.app.Activity
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

internal open class StandardTokenProviderTask(
    val tokenResult: StandardIntegrityToken? = null,
    val integrityException: Exception? = null,
    private val retryCountBeforeSuccess: Int? = null,
) : Task<StandardIntegrityToken>() {
    private var currentOnFailureRetryCount = AtomicInteger(0)
    private var currentOnSuccessRetryCount = AtomicInteger(0)

    override fun addOnFailureListener(listener: OnFailureListener): Task<StandardIntegrityToken> {
        if (integrityException != null) {
            if (retryCountBeforeSuccess == null || currentOnFailureRetryCount.getAndIncrement() <= retryCountBeforeSuccess) {
                listener.onFailure(integrityException)
            }
        }

        return this
    }

    override fun addOnSuccessListener(listener: OnSuccessListener<in StandardIntegrityToken>): Task<StandardIntegrityToken> {
        if (tokenResult != null) {
            if (retryCountBeforeSuccess == null || currentOnSuccessRetryCount.getAndIncrement() >= retryCountBeforeSuccess) {
                listener.onSuccess(tokenResult)
            }
        }

        return this
    }

    override fun addOnFailureListener(activity: Activity, listener: OnFailureListener): Task<StandardIntegrityToken> {
        return addOnFailureListener(listener)
    }

    override fun addOnFailureListener(executor: Executor, listener: OnFailureListener): Task<StandardIntegrityToken> {
        return addOnFailureListener(listener)
    }

    override fun getException(): java.lang.Exception? {
        return integrityException
    }

    override fun getResult(): StandardIntegrityToken {
        return tokenResult!!
    }

    override fun <X : Throwable?> getResult(classType: Class<X>): StandardIntegrityToken {
        return tokenResult!!
    }

    override fun isCanceled(): Boolean {
        return false
    }

    override fun isComplete(): Boolean {
        return true
    }

    override fun isSuccessful(): Boolean {
        return exception == null
    }

    override fun addOnSuccessListener(executor: Executor, listener: OnSuccessListener<in StandardIntegrityToken>): Task<StandardIntegrityToken> {
        return addOnSuccessListener(listener)
    }

    override fun addOnSuccessListener(activity: Activity, listener: OnSuccessListener<in StandardIntegrityToken>): Task<StandardIntegrityToken> {
        return addOnSuccessListener(listener)
    }
}
