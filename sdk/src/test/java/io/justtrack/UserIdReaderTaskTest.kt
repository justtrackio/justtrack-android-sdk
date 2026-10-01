package io.justtrack

import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.providers.AdvertiserIdProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.UUID
import kotlin.experimental.xor

internal class UserIdReaderTaskTest {
    private val logger: HttpLogger = mock()
    private val deviceInfo: DeviceInfo = mock()
    private val attributionIdManager: AttributionIdManager = mock()

    @Test
    fun `returns cached stored user id without creating new one`() = runBlocking {
        val storedId = UUID.fromString("8a4929d4-b3f4-4593-84f9-b2fad0e9cc1e")
        val task = newTask(advertiserId = "advertiser-id", trackingId = "tracking-id", deviceId = "device-id", storedUserId = storedId)

        val result = task.execute()

        assertEquals(storedId.toString(), result)
        verify(deviceInfo, never()).getAndroidIdOrDefault(any())
        Unit
    }

    @Test
    fun `creates stores deterministic id from advertiser id first`() = runBlocking {
        val task = newTask(advertiserId = "ADVERTISER-ID", trackingId = "tracking-id", deviceId = "device-id")

        val result = task.execute()

        assertEquals(expectedUserId("advertiser-id"), result)
        verify(attributionIdManager).storeUserId(result)
    }

    @Test
    fun `creates id from tracking id when advertiser id absent`() = runBlocking {
        val task = newTask(advertiserId = null, trackingId = "TRACKING-ID", deviceId = "device-id")

        val result = task.execute()

        assertEquals(expectedUserId("tracking-id"), result)
        verify(attributionIdManager).storeUserId(result)
    }

    @Test
    fun `creates id from device id when advertiser and tracking ids absent`() = runBlocking {
        val task = newTask(advertiserId = "", trackingId = null, deviceId = "DEVICE-ID")

        val result = task.execute()

        assertEquals(expectedUserId("device-id"), result)
        verify(attributionIdManager).storeUserId(result)
    }

    @Test
    fun `throws logs and does not store when no unique id exists`() = runBlocking {
        val task = newTask(advertiserId = null, trackingId = "", deviceId = "")

        try {
            task.execute()
            fail("expected RuntimeException")
        } catch (exception: RuntimeException) {
            assertEquals("No unique user id found", exception.message)
            verify(logger).error("Unable to create user id", exception)
        }
        verify(attributionIdManager, never()).storeUserId(any())
    }

    @Test
    fun `throws when device id is null and no other id exists`() = runBlocking {
        val task = newTask(advertiserId = null, trackingId = null, deviceId = null)

        try {
            task.execute()
            fail("expected RuntimeException")
        } catch (exception: RuntimeException) {
            assertEquals("No unique user id found", exception.message)
        }
        verify(attributionIdManager, never()).storeUserId(any())
    }

    @Test
    fun `message digest failure logs hash and execute errors`() = runBlocking {
        val failure = NoSuchAlgorithmException("sha missing")
        val mocked = Mockito.mockStatic(MessageDigest::class.java)
        try {
            mocked.`when`<MessageDigest> { MessageDigest.getInstance("SHA-256") }.thenThrow(failure)
            val task = newTask(advertiserId = "advertiser-id", trackingId = null, deviceId = null)

            try {
                task.execute()
                fail("expected NoSuchAlgorithmException")
            } catch (exception: NoSuchAlgorithmException) {
                assertSame(failure, exception)
                verify(logger).error("Unable to generate userId", failure)
                verify(logger).error("Unable to create user id", failure)
            }
        } finally {
            mocked.close()
        }
    }

    @Test
    fun `attribution params generated methods are covered`() {
        val params = UserIdReaderTask.AttributionParams(
            advertiserIdProvider = AdvertiserIdProvider { ValueFuture(TestAdvertiserIdInfo("advertiser-id")) },
            trackingId = "tracking-id",
        )
        val copy = params.copy(trackingId = "other")

        assertEquals("tracking-id", params.trackingId)
        assertEquals("other", copy.trackingId)
        assertTrue(params.toString().contains("tracking-id"))
    }

    private fun newTask(advertiserId: String?, trackingId: String?, deviceId: String?, storedUserId: UUID? = null): UserIdReaderTask {
        whenever(attributionIdManager.getStoredUserId()).thenReturn(ValueFuture(storedUserId))
        whenever(deviceInfo.getAndroidIdOrDefault("")).thenReturn(deviceId)
        return UserIdReaderTask(
            deviceInfo = deviceInfo,
            logger = logger,
            attributionIdManager = attributionIdManager,
            attributionParams = UserIdReaderTask.AttributionParams(
                advertiserIdProvider = AdvertiserIdProvider { ValueFuture(TestAdvertiserIdInfo(advertiserId)) },
                trackingId = trackingId,
            ),
            applicationPackageName = APPLICATION_PACKAGE_NAME,
        )
    }

    private fun expectedUserId(uniqueId: String): String {
        val input = "${BuildConfig.USER_ID_SECRET}-${APPLICATION_PACKAGE_NAME.lowercase()}-$uniqueId"
        val hashed = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        val uuid = ByteArray(16)
        for (i in 0 until 16) {
            uuid[i] = (hashed[i * 2] xor hashed[i * 2 + 1])
        }
        uuid[6] = (uuid[6].toInt() and 0x0F or 0x40).toByte()
        uuid[8] = (uuid[8].toInt() and 0x3F or 0x80).toByte()
        return String.format(
            java.util.Locale.US,
            "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
            uuid[0],
            uuid[1],
            uuid[2],
            uuid[3],
            uuid[4],
            uuid[5],
            uuid[6],
            uuid[7],
            uuid[8],
            uuid[9],
            uuid[10],
            uuid[11],
            uuid[12],
            uuid[13],
            uuid[14],
            uuid[15],
        )
    }

    private class TestAdvertiserIdInfo(
        override val advertiserId: String?,
    ) : AdvertiserIdInfo {
        override val isLimitedAdTracking: Boolean = false
    }

    private companion object {
        const val APPLICATION_PACKAGE_NAME = "io.justtrack.TEST"
    }
}
