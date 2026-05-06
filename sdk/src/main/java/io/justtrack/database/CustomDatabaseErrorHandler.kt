package io.justtrack.database

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import io.justtrack.log.Logger
import java.io.File

/**
 * In case the database got corrupted we will delete the current one.
 * When this database will be access afterward the onCreate() will create a new one.
 */
internal class CustomDatabaseErrorHandler(consoleLogger: Logger) : DatabaseErrorHandler {
    private var logger: Logger = consoleLogger

    override fun onCorruption(dbObj: SQLiteDatabase?) {
        dbObj?.let { db ->
            logger.warn("Database corrupted: ${db.path}")
            db.close()
            val dbFile = File(db.path)
            dbFile.delete()
        }
    }

    internal fun setLogger(logger: Logger) {
        this.logger = logger
    }
}
