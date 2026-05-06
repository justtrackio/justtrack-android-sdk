package io.justtrack

internal class FirebaseIdStore : PersistentIdStore() {
    override fun getSharePrefName(): String {
        return "justtrack-attribution-firebase-store"
    }

    internal companion object {
        private val mInstance: FirebaseIdStore by lazy {
            FirebaseIdStore()
        }

        @JvmStatic
        @JvmName("getInstance")
        internal fun getInstance(): FirebaseIdStore {
            return mInstance
        }
    }
}
