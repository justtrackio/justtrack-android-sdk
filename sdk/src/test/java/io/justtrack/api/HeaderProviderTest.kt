package io.justtrack.api

import io.justtrack.ApplicationVersion
import io.justtrack.DeviceInfo
import io.justtrack.PlatformType
import io.justtrack.okhttp.Headers
import io.justtrack.versions.SdkVersion
import io.justtrack.versions.VersionBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class HeaderProviderTest {

    private val apiToken = "test-token"
    private val pkg = "io.justtrack.host"

    private fun sdkVersion(name: String = "1.2.3", platform: PlatformType = PlatformType.ANDROID): SdkVersion = mock<SdkVersion>().apply {
        whenever(this.name).thenReturn(name)
        whenever(this.platformType).thenReturn(platform)
    }

    private fun appVersion(versionName: String = "9.9.9", versionCode: String = "999"): ApplicationVersion = mock<ApplicationVersion>().apply {
        whenever(getVersionName()).thenReturn(versionName)
        whenever(getVersionCode()).thenReturn(versionCode)
    }

    private fun deviceInfo(
        product: String = "Pixel",
        model: String = "Pixel 7",
        cpu: String? = "arm64-v8a",
        osVersion: String = "13",
        build: String = "TQ1A",
        appName: String? = "JustTrack Demo",
    ): DeviceInfo = mock<DeviceInfo>().apply {
        whenever(deviceProduct).thenReturn(product)
        whenever(deviceModel).thenReturn(model)
        whenever(cpuArch).thenReturn(cpu)
        whenever(this.osVersion).thenReturn(osVersion)
        whenever(this.build).thenReturn(build)
        whenever(getAppName()).thenReturn(appName)
    }

    private fun newSubject(
        device: DeviceInfo = deviceInfo(),
        bundle: VersionBundle = VersionBundle(sdkVersion(), appVersion()),
    ): HeaderProviderImpl = HeaderProviderImpl(apiToken, pkg, device, bundle)

    private fun Headers.namesAsList(): List<String> = (0 until size).map { name(it) }
    private fun Headers.valueOf(headerName: String): String? = values(headerName).firstOrNull()

    // region provideHeader

    @Test
    fun provideHeader_includesClientHeadersAndOmitsNullableIdentifiers() {
        val subject = newSubject()

        val headers = subject.provideHeader(advertiserId = null, uuid = null, installId = null, logger = TestApiLogger())

        assertEquals(pkg, headers.valueOf("X-CLIENT-ID"))
        assertEquals(apiToken, headers.valueOf("X-CLIENT-TOKEN"))
        assertEquals("missing", headers.valueOf("X-ADVERTISER-ID"))
        assertNull(headers.valueOf("X-USER-ID"))
        assertNull(headers.valueOf("X-INSTALL-ID"))
        assertNotNull(headers.valueOf("User-Agent"))
    }

    @Test
    fun provideHeader_includesAdvertiserUserAndInstallWhenProvided() {
        val subject = newSubject()

        val headers = subject.provideHeader(
            advertiserId = "adv",
            uuid = "uuid-1",
            installId = "install-1",
            logger = TestApiLogger(),
        )

        assertEquals("adv", headers.valueOf("X-ADVERTISER-ID"))
        assertEquals("uuid-1", headers.valueOf("X-USER-ID"))
        assertEquals("install-1", headers.valueOf("X-INSTALL-ID"))
    }

    // endregion

    // region provideHeaderV2

    @Test
    fun provideHeaderV2_usesAppBundleAndTokenHeadersAndOmitsNullables() {
        val subject = newSubject()

        val headers = subject.provideHeaderV2(advertiserId = null, uuid = null, installId = null, logger = TestApiLogger())

        assertEquals(pkg, headers.valueOf("X-APP-BUNDLE-ID"))
        assertEquals(apiToken, headers.valueOf("X-APP-TOKEN"))
        assertEquals("missing", headers.valueOf("X-ADVERTISER-ID"))
        assertNull(headers.valueOf("X-USER-ID"))
        assertNull(headers.valueOf("X-INSTALL-ID"))
        assertNotNull(headers.valueOf("User-Agent"))
        // Ensure V1-only names are NOT present.
        val names = headers.namesAsList()
        assertFalse("V2 must not emit X-CLIENT-ID", names.contains("X-CLIENT-ID"))
        assertFalse("V2 must not emit X-CLIENT-TOKEN", names.contains("X-CLIENT-TOKEN"))
    }

    @Test
    fun provideHeaderV2_includesAdvertiserUserAndInstallWhenProvided() {
        val subject = newSubject()

        val headers = subject.provideHeaderV2(
            advertiserId = "adv",
            uuid = "uuid-1",
            installId = "install-1",
            logger = TestApiLogger(),
        )

        assertEquals("adv", headers.valueOf("X-ADVERTISER-ID"))
        assertEquals("uuid-1", headers.valueOf("X-USER-ID"))
        assertEquals("install-1", headers.valueOf("X-INSTALL-ID"))
    }

    // endregion

    // region User-Agent

    /**
     * Decodes `%XX` escapes without treating `+` as a space, mirroring the backend contract
     * (Go's `url.PathUnescape`). [java.net.URLDecoder] must not be used here: it would turn the
     * literal `+` that [escapeField] deliberately keeps into a space.
     */
    private fun decode(value: String): String {
        val bytes = java.io.ByteArrayOutputStream()
        var index = 0

        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 <= value.length - 1) {
                bytes.write(value.substring(index + 1, index + 3).toInt(16))
                index += 3
            } else {
                bytes.write(char.code)
                index++
            }
        }

        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun assertPrintableAscii(userAgent: String) {
        assertTrue("UA must be printable US-ASCII only, was: $userAgent", userAgent.all { it.code in 0x20..0x7E })
    }

    @Test
    fun userAgent_isBuiltFromDeviceAndVersionMetadata() {
        val device = deviceInfo(
            product = "Pixel",
            model = "Pixel 7",
            cpu = "arm64-v8a",
            osVersion = "13",
            build = "TQ1A.230205.002",
            appName = "JustTrack Demo!",
        )
        val bundle = VersionBundle(
            sdkVersion(name = "1.2.3", platform = PlatformType.ANDROID),
            appVersion(versionName = "9.9.9", versionCode = "999"),
        )
        val subject = newSubject(device, bundle)

        val ua = subject.provideHeader(advertiserId = "adv", uuid = null, installId = null, logger = TestApiLogger())
            .valueOf("User-Agent")!!

        assertTrue("UA must include SDK marker, was: $ua", ua.startsWith("JustTrackSDK/1.2.3"))
        assertTrue("UA must include product/model, was: $ua", ua.contains("(Pixel; Pixel 7;"))
        assertTrue("UA must include CPU arch, was: $ua", ua.contains("arm64-v8a CPU;"))
        assertTrue("UA must include Android version, was: $ua", ua.contains("Android 13;"))
        assertTrue("UA must include locale, was: $ua", ua.contains(Locale.getDefault().toString()))
        assertTrue("UA must include build, was: $ua", ua.contains("Build/TQ1A.230205.002"))
        // Plain ASCII values without delimiters are passed through verbatim, spaces included.
        assertTrue("UA must keep the app name verbatim, was: $ua", ua.contains("JustTrack Demo!/9.9.9 (Android)"))
    }

    @Test
    fun userAgent_leavesPlainAsciiValuesCompletelyUnchanged() {
        val subject = newSubject(
            device = deviceInfo(product = "Pixel", model = "Pixel 7", cpu = "arm64-v8a", osVersion = "13", build = "TQ1A", appName = "MyApp"),
            bundle = VersionBundle(sdkVersion(name = "1.2.3"), appVersion(versionName = "9.9.9")),
        )

        val ua = subject.provideHeader(null, null, null, TestApiLogger()).valueOf("User-Agent")!!

        // Escaping must be a no-op here - no percent sign may appear anywhere.
        assertFalse("UA must not contain any escape, was: $ua", ua.contains("%"))
        assertEquals(
            "JustTrackSDK/1.2.3 (Pixel; Pixel 7; arm64-v8a CPU; Android 13; ${Locale.getDefault()}; Build/TQ1A) MyApp/9.9.9 (Android)",
            ua,
        )
    }

    @Test
    fun userAgent_escapesNonLatinAppNameButKeepsTheStructureReadable() {
        // Regression test: a Chinese app name previously reached OkHttp unescaped, which rejected the
        // header with "IllegalArgumentException: Unexpected char 0x80cc ... in User-Agent value".
        // The old `replace("\\W", "_")` did not catch it because Android's regex \w is unicode-aware.
        val chineseAppName = "背包文明_进化对决"
        val device = deviceInfo(
            product = "PD2419",
            model = "V2419A",
            cpu = "aarch64",
            osVersion = "16",
            build = "BP2A.250605",
            appName = chineseAppName,
        )
        val subject = newSubject(
            device = device,
            bundle = VersionBundle(sdkVersion(name = "8.0.0"), appVersion(versionName = "1.0")),
        )

        // Building the headers at all proves OkHttp accepted the value.
        val ua = subject.provideHeader(null, null, null, TestApiLogger()).valueOf("User-Agent")!!

        assertPrintableAscii(ua)
        // The surrounding fields and all delimiters stay untouched and human readable.
        assertTrue("device block must stay readable, was: $ua", ua.contains("(PD2419; V2419A; aarch64 CPU; Android 16; "))
        assertTrue("build must stay readable, was: $ua", ua.contains("Build/BP2A.250605)"))
        val escapedName = "%E8%83%8C%E5%8C%85%E6%96%87%E6%98%8E_%E8%BF%9B%E5%8C%96%E5%AF%B9%E5%86%B3"
        assertTrue("only the app name is escaped, was: $ua", ua.contains(" $escapedName/1.0 (Android)"))
        assertEquals(chineseAppName, decode(ua).substringAfterLast(") ").substringBefore("/"))
    }

    @Test
    fun userAgent_escapesNonLatinDeviceModel() {
        // Build.MODEL is the one device field the Android CDD puts no format requirements on, so it
        // may contain non-ASCII characters.
        val device = deviceInfo(
            product = "PD2419",
            model = "P98 4G八核版(A8H8)",
            cpu = "aarch64",
            osVersion = "16",
            build = "BP2A.250605",
            appName = "MyApp",
        )
        val subject = newSubject(device = device)

        val ua = subject.provideHeader(null, null, null, TestApiLogger()).valueOf("User-Agent")!!

        assertPrintableAscii(ua)
        // Only the non-ASCII part is escaped. Parentheses stay as they are: Uri.encode keeps them in
        // its fixed unreserved set, and the backend handles parentheses inside a field.
        assertTrue("model must keep its ascii parts, was: $ua", ua.contains("; P98 4G%E5%85%AB%E6%A0%B8%E7%89%88(A8H8); "))
        assertEquals("P98 4G八核版(A8H8)", decode(ua).substringAfter("; ").substringBefore("; aarch64"))
    }

    @Test
    fun userAgent_escapesDelimitersAndPercentInAppName() {
        val device = deviceInfo(appName = "Rock/Paper 100% Disney+")
        val subject = newSubject(device = device)

        val ua = subject.provideHeader(null, null, null, TestApiLogger()).valueOf("User-Agent")!!

        assertPrintableAscii(ua)
        // '/' would split name from version and '%' must survive a single decode. '+' is escaped as
        // well, which keeps the value unambiguous for either decoder on the backend.
        assertTrue("slash must be escaped, was: $ua", ua.contains("Rock%2FPaper"))
        assertTrue("percent must be escaped, was: $ua", ua.contains("100%25"))
        assertTrue("plus must be escaped, was: $ua", ua.contains("Disney%2B/"))
        assertEquals("Rock/Paper 100% Disney+", decode(ua).substringAfterLast(") ").substringBefore("/9.9.9"))
    }

    @Test
    fun userAgent_omitsCpuSegmentWhenCpuArchIsNull() {
        val device = deviceInfo(cpu = null)
        val subject = newSubject(device = device)

        val ua = subject.provideHeader(null, null, null, TestApiLogger()).valueOf("User-Agent")!!

        assertFalse("UA must not contain CPU segment, was: $ua", ua.contains(" CPU; "))
    }

    @Test
    fun userAgent_omitsAppSegmentWhenAppNameIsNull() {
        val device = deviceInfo(appName = null)
        val subject = newSubject(device = device)

        val ua = subject.provideHeader(null, null, null, TestApiLogger()).valueOf("User-Agent")!!

        // With no appName the UA must not contain any "<name>/<version> (<platform>)" app segment after the device block.
        assertFalse("UA must not contain app segment, was: $ua", ua.matches(Regex(".*\\)\\s+\\S+/\\S+\\s+\\(.+\\)$")))
        assertTrue("UA must end at the device-info closing paren, was: $ua", ua.trimEnd().endsWith(")"))
    }

    @Test
    fun userAgent_fallsBackToVersionCodeWhenVersionNameIsEmpty() {
        val bundle = VersionBundle(
            sdkVersion(),
            appVersion(versionName = "", versionCode = "42"),
        )
        val subject = newSubject(bundle = bundle)

        val ua = subject.provideHeader(null, null, null, TestApiLogger()).valueOf("User-Agent")!!

        assertTrue("UA must use versionCode fallback, was: $ua", ua.contains("/42 ("))
    }

    @Test
    fun userAgent_isCachedAcrossInvocationsAndDoesNotRereadDeviceInfo() {
        val device = deviceInfo()
        val bundle = VersionBundle(sdkVersion(), appVersion())
        val subject = newSubject(device = device, bundle = bundle)
        val logger = TestApiLogger()

        val first = subject.provideHeader(null, null, null, logger).valueOf("User-Agent")!!
        val second = subject.provideHeaderV2(null, null, null, logger).valueOf("User-Agent")!!

        assertSame("Both invocations must reuse the cached UA string", first, second)
        // deviceInfo accessors should only have been hit during the first build.
        verify(device, times(1)).deviceProduct
        verify(device, times(1)).deviceModel
        verify(device, times(1)).cpuArch
        verify(device, times(1)).osVersion
        verify(device, times(1)).build
        verify(device, times(1)).getAppName()
    }

    @Test
    fun userAgent_isLoggedAtDebugLevelOnFirstInvocation() {
        val subject = newSubject()
        val logger = mock<io.justtrack.log.Logger>()

        subject.provideHeader(null, null, null, logger)
        subject.provideHeader(null, null, null, logger) // 2nd call uses cache

        // Only the first call should have produced a debug log line.
        verify(logger, times(1)).debug(any(), any())
    }

    // endregion
}
