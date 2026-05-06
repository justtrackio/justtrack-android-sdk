package io.justtrack.integrity

import android.app.Activity
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken

internal class IntegrityToken(private val token: String) : StandardIntegrityToken() {
    override fun token(): String {
        return token
    }

    override fun showDialog(p0: Activity?, p1: Int): Task<Int> {
        throw Exception("showDialog Not Implemented")
    }
}
