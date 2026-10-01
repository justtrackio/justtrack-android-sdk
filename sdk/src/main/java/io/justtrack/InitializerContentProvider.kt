package io.justtrack

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import io.justtrack.util.ExcludeFromJacocoGeneratedReport

/**
 * A [ContentProvider] declared in the SDK's manifest that auto-initializes the justtrack SDK
 * when the host application starts. Android instantiates this class via reflection, so it must
 * remain public even though it is not part of the consumer-facing API.
 */
class InitializerContentProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val context = context
        if (context != null) {
            JustTrack.init(context.applicationContext, null)
        }

        return true
    }

    @ExcludeFromJacocoGeneratedReport
    override fun query(uri: Uri, strings: Array<String?>?, string: String?, strings1: Array<String?>?, s1: String?): Cursor? = null

    @ExcludeFromJacocoGeneratedReport
    override fun getType(uri: Uri): String? = null

    @ExcludeFromJacocoGeneratedReport
    override fun insert(uri: Uri, contentValues: ContentValues?): Uri? = null

    @ExcludeFromJacocoGeneratedReport
    override fun delete(uri: Uri, string: String?, strings: Array<String?>?): Int = 0

    @ExcludeFromJacocoGeneratedReport
    override fun update(uri: Uri, contentValues: ContentValues?, string: String?, strings: Array<String?>?): Int = 0
}
