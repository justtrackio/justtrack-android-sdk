package io.justtrack

import android.app.Application
import android.net.ConnectivityManager
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConnectivityProviderLegacyImplTiramisuTest {

    private val application: Application = RuntimeEnvironment.getApplication()

    @Test
    fun `init registers broadcast receiver with RECEIVER_NOT_EXPORTED on TIRAMISU+`() {
        val provider = ConnectivityProviderLegacyImpl(application)
        try {
            val receivers = shadowOf(application).registeredReceivers
                .filter { it.broadcastReceiver === provider }
            Assert.assertEquals(1, receivers.size)
            val actions = receivers[0].intentFilter.actionsIterator().asSequence().toList()
            Assert.assertTrue(actions.contains(ConnectivityManager.CONNECTIVITY_ACTION))
        } finally {
            provider.shutdown()
        }
    }
}
