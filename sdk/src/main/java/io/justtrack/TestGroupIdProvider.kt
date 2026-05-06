package io.justtrack

import io.justtrack.attribution.AdvertiserIdInfo
import java.util.concurrent.Future

/**
 * Class to encapsulate getting an [Int] [Future]. Allows us to easily lock
 * this class without having to fear that we deadlock anything (we used to lock the [BaseJustTrackSdk]
 * for this - this lead to a problem when you needed to lock the SDK to process events, but another
 * thread had the SDK locked while blocking on publishing a new event as the blocking queue was full).
 */
internal class TestGroupIdProvider internal constructor(
    val taskExecutor: TaskExecutor,
) {
    private var testGroupId: AsyncFuture<Int?>? = null

    // Deadlock-Safety: executeAsFuture is not locking anything (besides the executor maybe).
    @Synchronized
    @JvmName("provideTestGroupIdFuture")
    internal fun provideTestGroupIdFuture(
        logger: HttpLogger,
        advertiserIdFuture: AsyncFuture<AdvertiserIdInfo>,
        databaseInterface: DatabaseInterface,
    ): AsyncFuture<Int?> {
        val currentTestGroupId = testGroupId

        return if (currentTestGroupId == null) {
            val result = taskExecutor.executeAsFuture(
                TestGroupIdReaderTask(
                    logger,
                    advertiserIdFuture,
                    databaseInterface,
                ),
            )
            testGroupId = result
            result
        } else {
            currentTestGroupId
        }
    }

    @Synchronized
    // Deadlock-Safety: We did not lock anything.
    @JvmName("setTestGroupId")
    internal fun setTestGroupId(testGroupId: Int?) {
        this.testGroupId = ValueFuture(testGroupId)
    }
}
