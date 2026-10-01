package io.justtrack.util

import java.io.File

/**
 * Abstraction over file I/O operations used by the SDK to persist and retrieve data.
 *
 * Provides a testable boundary around Android's file system APIs, allowing
 * implementations to be swapped out in unit tests without requiring a real device context.
 */
internal interface FileAccessor {

    /**
     * Returns the names of all files in the managed storage directory.
     *
     * The returned list contains plain file names (not full paths). Callers are
     * responsible for any further filtering based on their own naming conventions.
     *
     * @return a list of file names, or an empty list if the directory is empty or unavailable.
     */
    fun listFileNames(): List<String>

    /**
     * Reads and returns the full text content of the file with the given name.
     *
     * @param fileName the name of the file to read.
     * @return the content of the file as a [String].
     */
    fun loadFile(fileName: String): String

    /**
     * Deletes the file with the given name from the managed storage directory.
     *
     * @param fileName the name of the file to delete.
     */
    fun deleteFile(fileName: String)

    /**
     * Returns a [File] reference for the given file name within the managed storage directory.
     *
     * The file is not guaranteed to exist; use [createNewFile] to create it if needed.
     *
     * @param fileName the name of the file.
     * @return a [File] pointing to the file location.
     */
    fun getFile(fileName: String): File

    /**
     * Creates the given [File] on disk if it does not already exist.
     *
     * @param file the [File] to create.
     */
    fun createNewFile(file: File)

    /**
     * Overwrites the content of the given [File] with [content].
     *
     * @param file the [File] to write to.
     * @param content the text to write into the file.
     */
    fun editFile(file: File, content: String)
}
