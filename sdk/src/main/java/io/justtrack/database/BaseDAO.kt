package io.justtrack.database

import android.database.sqlite.SQLiteDatabase

internal interface BaseDAO {
    fun createTable(db: SQLiteDatabase)
    fun endTransaction(writeableDatabase: SQLiteDatabase)
}
