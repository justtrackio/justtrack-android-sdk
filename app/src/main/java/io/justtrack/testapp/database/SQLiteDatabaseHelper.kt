package io.justtrack.testapp.database

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * this sqlite is for retrieving total log count
 */
class SQLiteDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "justtrack", null, 4) {
    override fun onCreate(db: SQLiteDatabase?) {
        // no need to create any tables, we just read them
    }

    override fun onUpgrade(
        db: SQLiteDatabase?,
        oldVersion: Int,
        newVersion: Int,
    ) {
        // no need to upgrade any tables, we just read them
    }

    fun getStoredLogCount(): Long =
        runBlocking {
            withContext(Dispatchers.IO) {
                readableDatabase.use {
                    try {
                        val messageCount = DatabaseUtils.queryNumEntries(it, "message")
                        val metricCount = DatabaseUtils.queryNumEntries(it, "metric")

                        return@withContext messageCount + metricCount
                    } catch (ignore: Exception) {
                        return@withContext 0L
                    }
                }
            }
        }
}
