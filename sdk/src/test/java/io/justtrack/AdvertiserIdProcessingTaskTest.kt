package io.justtrack

import io.justtrack.ads.AdvertiserIdProcessingTask
import io.justtrack.ads.DeviceAdvertiserIdReader
import io.justtrack.attribution.AdvertiserIdInfo
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

internal class AdvertiserIdProcessingTaskTest {

    @Test
    internal fun `read AdvertiserId with normal id`() = runBlocking {
        val deviceAdvertiserIdReader: DeviceAdvertiserIdReader = mock()
        val id = UUID.randomUUID().toString()
        val limit = true
        val idInfo = object : AdvertiserIdInfo {
            override val advertiserId: String
                get() = id
            override val isLimitedAdTracking: Boolean
                get() = limit
        }
        whenever(deviceAdvertiserIdReader.readAdvertiserId()).thenReturn(idInfo)

        val readerTask = AdvertiserIdProcessingTask(deviceAdvertiserIdReader, mock())

        val result = readerTask.execute()

        assert(result.advertiserId == id)
        assert(result.isLimitedAdTracking == limit)
    }

    @Test
    internal fun `read AdvertiserId with bad id`() = runBlocking {
        val deviceAdvertiserIdReader: DeviceAdvertiserIdReader = mock()
        val id = "00000000-0000-0000-0000-000000000000"
        val limit = false
        val idInfo = object : AdvertiserIdInfo {
            override val advertiserId: String?
                get() = id

            override val isLimitedAdTracking: Boolean
                get() = limit
        }
        whenever(deviceAdvertiserIdReader.readAdvertiserId()).thenReturn(idInfo)

        val readerTask = AdvertiserIdProcessingTask(deviceAdvertiserIdReader, mock())

        val result = readerTask.execute()

        assert(result.advertiserId == null)
        assert(result.isLimitedAdTracking)
    }

    @Test
    internal fun `read AdvertiserId with no id`() = runBlocking {
        val deviceAdvertiserIdReader: DeviceAdvertiserIdReader = mock()
        val id: String? = null
        val limit = false
        val idInfo = object : AdvertiserIdInfo {
            override val advertiserId: String?
                get() = id
            override val isLimitedAdTracking: Boolean
                get() = limit
        }
        whenever(deviceAdvertiserIdReader.readAdvertiserId()).thenReturn(idInfo)

        val readerTask = AdvertiserIdProcessingTask(deviceAdvertiserIdReader, mock())

        val result = readerTask.execute()

        assert(result.advertiserId == null)
        assert(result.isLimitedAdTracking)
    }

    @Test
    internal fun `read AdvertiserId with wrong format id`() = runBlocking {
        val deviceAdvertiserIdReader: DeviceAdvertiserIdReader = mock()
        val id = "332_int"
        val limit = false
        val idInfo = object : AdvertiserIdInfo {
            override val advertiserId: String?
                get() = id
            override val isLimitedAdTracking: Boolean
                get() = limit
        }
        whenever(deviceAdvertiserIdReader.readAdvertiserId()).thenReturn(idInfo)

        val readerTask = AdvertiserIdProcessingTask(deviceAdvertiserIdReader, mock())

        val result = readerTask.execute()

        assert(result.advertiserId == null)
        assert(result.isLimitedAdTracking)
    }

    @Test
    internal fun `read AdvertiserId failure logs warning and returns fallback`() = runBlocking {
        val deviceAdvertiserIdReader: DeviceAdvertiserIdReader = mock()
        val logger: HttpLogger = mock()
        val failure = RuntimeException("failed")
        whenever(deviceAdvertiserIdReader.readAdvertiserId()).thenThrow(failure)
        val readerTask = AdvertiserIdProcessingTask(deviceAdvertiserIdReader, logger)

        val result = readerTask.execute()

        assert(result.advertiserId == null)
        assert(!result.isLimitedAdTracking)
        verify(logger).warn("Failed to read advertiser id", failure)
    }
}
