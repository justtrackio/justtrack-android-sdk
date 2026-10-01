package io.justtrack.util

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

internal class FileAccessorImpl(private val context: Context) : FileAccessor {
    override fun listFileNames(): List<String> {
        return context.filesDir.list()?.toList() ?: emptyList()
    }

    override fun loadFile(fileName: String): String {
        return BufferedReader(InputStreamReader(context.openFileInput(fileName))).use { it.readText() }
    }

    override fun deleteFile(fileName: String) {
        context.deleteFile(fileName)
    }

    override fun getFile(fileName: String): File {
        return context.filesDir.resolve(fileName)
    }

    override fun createNewFile(file: File) {
        file.createNewFile()
    }

    override fun editFile(file: File, content: String) {
        file.writeText(content)
    }
}
