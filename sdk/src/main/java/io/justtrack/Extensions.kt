package io.justtrack

import android.database.Cursor

internal inline fun <reified T : Enum<T>> enumValueOfOrNull(name: String): T? {
    return enumValues<T>().find { it.name == name }
}

internal fun Cursor.getIntOrNull(columnName: String): Int? =
    getColumnIndex(columnName).takeIf { it >= 0 }?.let { if (isNull(it)) null else getInt(it) }

internal fun Cursor.getStringOrNull(columnName: String): String? =
    getColumnIndex(columnName).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }

internal fun Cursor.getLongOrNull(columnName: String): Long? =
    getColumnIndex(columnName).takeIf { it >= 0L }?.let { if (isNull(it)) null else getLong(it) }

internal fun Boolean.toInt(): Int = if (this) 1 else 0
internal fun Int.toBoolean(): Boolean = this == 1
