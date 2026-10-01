package io.justtrack

import io.justtrack.providers.AdvertiserIdProvider
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ReconnectHandlerTest {

    private lateinit var mockAttributionOutputProvider: AttributionOutputProvider
    private lateinit var mockLogger: TestLogger
    private lateinit var handler: ReconnectHandler

    @Before
    fun setup() {
        mockAttributionOutputProvider = mock()
        mockLogger = TestLogger()
        handler = createHandler()
    }

    @Test
    fun retryAttributionAfterReconnect_resetsAndRefetches_whenOutputIsErrorFuture() {
        val errorFuture: AsyncFuture<AttributionOutput> = ErrorFuture(RuntimeException("network"))
        org.mockito.kotlin.whenever(mockAttributionOutputProvider.getOutput()).thenReturn(errorFuture)

        handler.retryAttributionAfterReconnect()

        verify(mockAttributionOutputProvider).setOutput(null)
        verify(mockAttributionOutputProvider).setAttributionCanRetryAt(0L)
        verify(mockAttributionOutputProvider).provideAttributionOutput(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION)
    }

    @Test
    fun retryAttributionAfterReconnect_doesNothing_whenOutputIsNotErrorFuture() {
        org.mockito.kotlin.whenever(mockAttributionOutputProvider.getOutput()).thenReturn(mock())

        handler.retryAttributionAfterReconnect()

        verify(mockAttributionOutputProvider, never()).setOutput(any())
        verify(mockAttributionOutputProvider, never()).setAttributionCanRetryAt(any())
        verify(mockAttributionOutputProvider, never()).provideAttributionOutput(any())
    }

    @Test
    fun retryAttributionAfterReconnect_doesNothing_whenOutputIsNull() {
        org.mockito.kotlin.whenever(mockAttributionOutputProvider.getOutput()).thenReturn(null)

        handler.retryAttributionAfterReconnect()

        verify(mockAttributionOutputProvider, never()).setOutput(any())
        verify(mockAttributionOutputProvider, never()).provideAttributionOutput(any())
    }

    @Test
    fun onReconnect_callsRetryAttributionAndRetrySendPersistId() {
        org.mockito.kotlin.whenever(mockAttributionOutputProvider.getOutput()).thenReturn(null)
        val mockContext = createMockContext()
        val testHandler = ReconnectHandler(
            context = mockContext,
            logger = mockLogger,
            attributionOutputProvider = mockAttributionOutputProvider,
            idManagers = ReconnectHandler.IdManagers(
                customIdManager = mock(),
                firebaseIdManager = mock(),
                userIdProvider = UserIdProvider { ValueFuture("user-id") },
                attributionIdManager = mock(),
                advertiserIdProvider = AdvertiserIdProvider { mock() },
            ),
        )

        testHandler.onReconnect()

        verify(mockAttributionOutputProvider).getOutput()
    }

    private fun createMockContext(): android.content.Context {
        val mockEditor: android.content.SharedPreferences.Editor = mock {
            on { clear() }.thenReturn(mock)
            on { putString(any(), any()) }.thenReturn(mock)
        }
        val mockPrefs: android.content.SharedPreferences = mock {
            on { getString(any(), any()) }.thenReturn(null)
            on { edit() }.thenReturn(mockEditor)
        }
        val ctx: android.content.Context = mock {
            on { getSharedPreferences(any<String>(), any()) }.thenReturn(mockPrefs)
        }
        return ctx
    }

    private fun createHandler(): ReconnectHandler {
        return ReconnectHandler(
            context = mock(),
            logger = mockLogger,
            attributionOutputProvider = mockAttributionOutputProvider,
            idManagers = ReconnectHandler.IdManagers(
                customIdManager = mock(),
                firebaseIdManager = mock(),
                userIdProvider = UserIdProvider { ValueFuture("user-id") },
                attributionIdManager = mock(),
                advertiserIdProvider = AdvertiserIdProvider { mock() },
            ),
        )
    }
}
