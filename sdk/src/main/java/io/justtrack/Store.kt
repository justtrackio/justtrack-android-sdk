package io.justtrack

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.runBlocking

internal object Store {
    private const val NAME = "justtrack-attribution"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_INSTALL_APP_VERSION = "install_app_version"
    private const val KEY_LAST_APP_VERSION = "last_app_version"
    private const val KEY_TEST_GROUP = "test_group"

    private const val KEY_IS_MIGRATED_DB = "is_migrated_db"

    private var sharePref: SharedPreferences? = null

    @VisibleForTesting
    @JvmStatic
    fun clearForTesting(context: Context) {
        getSharedPreferences(context).clearIO()
    }

    @JvmStatic
    fun setUserId(context: Context, userId: String?) {
        getSharedPreferences(context).putStringIO(KEY_USER_ID, userId)
    }

    @JvmStatic
    fun setInstallId(context: Context, installId: String?) {
        getSharedPreferences(context).putStringIO(KEY_INSTALL_ID, installId)
    }

    @JvmStatic
    fun getInstallId(context: Context): String? {
        return getSharedPreferences(context).getStringIO(KEY_INSTALL_ID, null)
    }

    @JvmStatic
    fun setMigratedToDB(context: Context, isMigrated: Boolean) {
        getSharedPreferences(context).putIntIO(KEY_IS_MIGRATED_DB, isMigrated.toInt())
    }

    @JvmStatic
    fun isMigratedToDB(context: Context): Boolean {
        return getSharedPreferences(context).getIntIO(KEY_IS_MIGRATED_DB, 0).toBoolean()
    }

    @VisibleForTesting
    @JvmStatic
    fun setTestGroup(context: Context, testGroup: Int) {
        getSharedPreferences(context).putIntIO(KEY_TEST_GROUP, testGroup)
    }

    @JvmStatic
    fun getAllData(context: Context): Map<String, *> {
        return getSharedPreferences(context).all
    }

    @VisibleForTesting
    @JvmStatic
    @JvmName("setInstallVersionLegacy")
    internal fun setInstallVersionLegacy(context: Context, version: Long) = runBlocking {
        getSharedPreferences(context = context).edit().apply {
            putLong(KEY_INSTALL_APP_VERSION, version)
        }.apply()
    }

    @VisibleForTesting
    @JvmStatic
    @JvmName("setLastVersionLegacy")
    internal fun setLastVersionLegacy(context: Context, version: Long) = runBlocking {
        getSharedPreferences(context = context).edit().apply {
            putLong(KEY_LAST_APP_VERSION, version)
        }.apply()
    }

    @JvmStatic
    @JvmName("migrateVersionIntToString")
    internal fun migrateVersionIntToString(context: Context) = runBlocking {
        getVersionAndMigrate(getSharedPreferences(context), KEY_LAST_APP_VERSION, null)
        getVersionAndMigrate(getSharedPreferences(context), KEY_INSTALL_APP_VERSION, null)
    }

    private fun getVersionAndMigrate(sharedPreferences: SharedPreferences, key: String, default: String?): String? {
        val isDataMigrated: Boolean = checkType<String>(sharedPreferences, key) ?: return default

        if (isDataMigrated) {
            val versionString = sharedPreferences.getStringIO(key, null)
            if (versionString != null) {
                return versionString
            }
        } else {
            val versionNo = sharedPreferences.getLongIO(key, -1)
            val version = versionNo.toString()
            sharedPreferences.edit()
                .remove(key)
                .putString(key, version)
                .apply()
            return version
        }

        return default
    }

    private fun getSharedPreferences(context: Context): SharedPreferences {
        val currentStore = sharePref
        return if (currentStore == null) {
            val result = context.getSharePrefIO(
                NAME,
                Context.MODE_PRIVATE,
            )
            sharePref = result
            result
        } else {
            currentStore
        }
    }

    private inline fun <reified T> checkType(sharedPreferences: SharedPreferences, key: String): Boolean? {
        val contain = sharedPreferences.all.containsKey(key)
        if (!contain) {
            return null
        }

        val dataAny = sharedPreferences.all[key]
        return dataAny is T
    }
}
