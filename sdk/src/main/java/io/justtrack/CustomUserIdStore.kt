package io.justtrack

internal class CustomUserIdStore : PersistentIdStore() {
    override fun getSharePrefName(): String {
        return "justtrack-attribution-custom-user-id-store"
    }

    internal companion object {
        private val mInstance: CustomUserIdStore by lazy {
            CustomUserIdStore()
        }

        @JvmStatic
        @JvmName("getInstance")
        internal fun getInstance(): CustomUserIdStore {
            return mInstance
        }
    }
}
