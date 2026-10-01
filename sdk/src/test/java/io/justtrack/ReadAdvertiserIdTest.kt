package io.justtrack

import io.justtrack.ads.AdvertiserIdProcessingTask
import io.justtrack.ads.DeviceAdvertiserIdReader
import io.justtrack.attribution.AdvertiserIdInfo
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.mock

@RunWith(Parameterized::class)
class ReadAdvertiserIdTest {
    var idInfo: String? = null
    var shouldPass: Boolean? = null

    constructor(idInfo: String?, shouldPass: Boolean?) {
        this.idInfo = idInfo
        this.shouldPass = shouldPass
    }

    @Test
    @Throws(Exception::class)
    fun testAdvertiserId() = runBlocking {
        val logger: HttpLogger = mock<HttpLogger>()
        val deviceAdvertiserIdReader = object : DeviceAdvertiserIdReader {
            override fun readAdvertiserId(): AdvertiserIdInfo {
                return object : AdvertiserIdInfo {
                    override val advertiserId: String?
                        get() = idInfo
                    override val isLimitedAdTracking: Boolean
                        get() = false
                }
            }
        }

        val task = AdvertiserIdProcessingTask(
            deviceAdvertiserIdReader,
            logger = logger,
        )

        val advertiserId = task.execute().advertiserId
        val isNotNull = advertiserId != null
        Assert.assertEquals(shouldPass, isNotNull)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "Square of {0} should be {1}")
        @Throws(JSONException::class)
        fun data(): Array<Array<Any?>> {
            return arrayOf(
                arrayOf(
                    "00000000-0000-0000-0000-000000000000",
                    false,
                ),
                arrayOf(
                    "0000-0000",
                    false,
                ),
                arrayOf(
                    "abcdABCD-1234-0000-0000-123456789101",
                    true,
                ),
                arrayOf(
                    "",
                    false,
                ),
                arrayOf(
                    null,
                    false,
                ),
            )
        }
    }
}
