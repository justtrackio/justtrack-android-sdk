package io.justtrack

internal class RunningPublishRequests {
    private val runningRequests: MutableMap<String, AsyncFuture<Unit>> = HashMap()

    private fun offer(installId: String, publishId: String, future: AsyncFuture<Unit>): AsyncFuture<*>? {
        val key = keyFor(installId, publishId)
        // Deadlock-Safety: We only block on the hashmap to add some value if absent
        synchronized(runningRequests) {
            val existingFuture = runningRequests[key]
            if (existingFuture != null) {
                return existingFuture
            }
            runningRequests.put(key, future)
        }
        future.registerCallback(
            object : Callback<Unit> {
                override fun resolve(response: Unit) {
                    done(key)
                }

                override fun reject(exception: Throwable) {
                    done(key)
                }
            },
        )
        return null
    }

    private fun done(key: String) {
        // Deadlock-Safety: We only block on the hashmap to remove a value
        synchronized(runningRequests) {
            runningRequests.remove(key)
        }
    }

    private fun keyFor(installId: String, publishId: String): String {
        return "$installId:$publishId"
    }

    companion object {
        private val customUserIdRequests = RunningPublishRequests()
        private val firebaseAppInstanceIdRequests = RunningPublishRequests()

        internal fun offerCustomUserId(installId: String, customUserId: String, future: AsyncFuture<Unit>): AsyncFuture<*>? {
            return customUserIdRequests.offer(installId, customUserId, future)
        }

        internal fun offerFirebaseAppInstanceId(installId: String, firebaseAppInstanceId: String, future: AsyncFuture<Unit>): AsyncFuture<*>? {
            return firebaseAppInstanceIdRequests.offer(installId, firebaseAppInstanceId, future)
        }
    }
}
