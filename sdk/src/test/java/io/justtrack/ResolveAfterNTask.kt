package io.justtrack

internal class ResolveAfterNTask(private val maxNumCalled: Int) : Task<String> {
    private var numCalled = 0

    override suspend fun execute(): String {
        if (numCalled < maxNumCalled) {
            numCalled++
            throw err
        }

        return "done"
    }

    companion object {
        @JvmField
        val err: Exception = RuntimeException()
    }
}
