package io.justtrack

import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.providers.AdvertiserIdProviderImpl
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

internal class AdvertiserIdReaderTaskTest {

    @Test
    internal fun `read AdvertiserId with normal id`() = runBlocking {
        val advertiserIdProvider: AdvertiserIdProviderImpl = mock()
        val id = UUID.randomUUID().toString()
        val limit = true
        val idInfo = object : AdvertiserIdInfo {
            override val advertiserId: String?
                get() = id
            override val isLimitedAdTracking: Boolean
                get() = limit
        }
        whenever(advertiserIdProvider.provideAdvertiserId()).thenReturn(idInfo)

        val readerTask = AdvertiserIdReaderTask(advertiserIdProvider, mock())

        val result = readerTask.execute()

        assert(result.advertiserId == id)
        assert(result.isLimitedAdTracking == limit)
    }

    @Test
    internal fun `read AdvertiserId with bad id`() = runBlocking {
        val advertiserIdProvider: AdvertiserIdProviderImpl = mock()
        val id = "00000000-0000-0000-0000-000000000000"
        val limit = false
        val idInfo = object : AdvertiserIdInfo {
            override val advertiserId: String?
                get() = id

            override val isLimitedAdTracking: Boolean
                get() = limit
        }
        whenever(advertiserIdProvider.provideAdvertiserId()).thenReturn(idInfo)

        val readerTask = AdvertiserIdReaderTask(advertiserIdProvider, mock())

        val result = readerTask.execute()

        assert(result.advertiserId == null)
        assert(result.isLimitedAdTracking)
    }

    @Test
    internal fun `read AdvertiserId with no id`() = runBlocking {
        val advertiserIdProvider: AdvertiserIdProviderImpl = mock()
        val id: String? = null
        val limit = false
        val idInfo = object : AdvertiserIdInfo {
            override val advertiserId: String?
                get() = id
            override val isLimitedAdTracking: Boolean
                get() = limit
        }
        whenever(advertiserIdProvider.provideAdvertiserId()).thenReturn(idInfo)

        val readerTask = AdvertiserIdReaderTask(advertiserIdProvider, mock())

        val result = readerTask.execute()

        assert(result.advertiserId == null)
        assert(result.isLimitedAdTracking)
    }

    @Test
    internal fun `read AdvertiserId with wrong format id`() = runBlocking {
        val advertiserIdProvider: AdvertiserIdProviderImpl = mock()
        val id = "332_int"
        val limit = false
        val idInfo = object : AdvertiserIdInfo {
            override val advertiserId: String?
                get() = id
            override val isLimitedAdTracking: Boolean
                get() = limit
        }
        whenever(advertiserIdProvider.provideAdvertiserId()).thenReturn(idInfo)

        val readerTask = AdvertiserIdReaderTask(advertiserIdProvider, mock())

        val result = readerTask.execute()

        assert(result.advertiserId == null)
        assert(result.isLimitedAdTracking)
    }
}
