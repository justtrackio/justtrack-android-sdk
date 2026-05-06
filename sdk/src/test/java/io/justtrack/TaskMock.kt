package io.justtrack

internal class TaskMock<T>(private val callback: suspend () -> T) : Task<T> {
    val resolvedValues: MutableList<T> = ArrayList()
    val thrownErrors: MutableList<Throwable> = ArrayList()

    override suspend fun execute(): T {
        try {
            val result = callback()
            resolvedValues.add(result)

            return result
        } catch (e: Throwable) {
            thrownErrors.add(e)
            throw e
        }
    }
}
