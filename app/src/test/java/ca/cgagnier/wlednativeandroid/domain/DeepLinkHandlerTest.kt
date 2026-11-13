package ca.cgagnier.wlednativeandroid.domain

import android.content.Intent
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeepLinkHandlerTest {

    private lateinit var handler: DeepLinkHandler

    @BeforeEach
    fun setUp() {
        handler = DeepLinkHandler()
    }

    private fun mockUri(uriString: String): Uri {
        val mock = mockk<Uri>(relaxed = true)
        every { mock.toString() } returns uriString
        // Match android.net.Uri hierarchical splitting (including colon-separated MAC authorities
        // where android.net.Uri.host truncates at the last ':' as a port separator).
        val scheme = uriString.substringBefore("://", missingDelimiterValue = "").takeIf { it.isNotEmpty() }
        val afterScheme = uriString.substringAfter("://", missingDelimiterValue = "")
        val authority = if (scheme != null) {
            afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val host = authority?.let { auth ->
            if (auth.startsWith('[')) {
                auth.substringBeforeLast(']') + "]"
            } else {
                auth.substringBeforeLast(':')
            }
        }?.takeIf { it.isNotEmpty() }
        val path = if (scheme != null && afterScheme.contains('/')) {
            "/" + afterScheme.substringAfter('/').substringBefore('?').substringBefore('#')
        } else {
            null
        }
        val segments = path?.split('/')?.filter { it.isNotEmpty() }.orEmpty()
        every { mock.scheme } returns scheme
        every { mock.authority } returns authority
        every { mock.host } returns host
        every { mock.path } returns path
        every { mock.pathSegments } returns segments
        return mock
    }

    // --- parseUri tests ---

    @Test
    fun `parseUri with illumidel scheme and MAC address returns MacAddress`() {
        val uri = mockUri("illumidel://AABBCCDDEEFF")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.MacAddress)
        assertEquals("AABBCCDDEEFF", (result as DeepLink.MacAddress).mac)
    }

    @Test
    fun `parseUri with illumidel scheme and lowercase MAC address returns uppercase MacAddress`() {
        val uri = mockUri("illumidel://aabbccddeeff")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.MacAddress)
        assertEquals("AABBCCDDEEFF", (result as DeepLink.MacAddress).mac)
    }

    @Test
    fun `parseUri with illumidel scheme and mixed case MAC address returns uppercase MacAddress`() {
        val uri = mockUri("illumidel://AaBbCcDdEeFf")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.MacAddress)
        assertEquals("AABBCCDDEEFF", (result as DeepLink.MacAddress).mac)
    }

    @Test
    fun `parseUri with illumidel scheme and IPv4 address returns Address`() {
        val uri = mockUri("illumidel://192.168.1.50")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.Address)
        assertEquals("192.168.1.50", (result as DeepLink.Address).address)
    }

    @Test
    fun `parseUri with illumidel scheme and hostname returns Address`() {
        val uri = mockUri("illumidel://illumidel.local")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.Address)
        assertEquals("illumidel.local", (result as DeepLink.Address).address)
    }

    @Test
    fun `parseUri with illumidel device host and MAC path returns MacAddress`() {
        val uri = mockUri("illumidel://device/aabbccddeeff")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.MacAddress)
        assertEquals("AABBCCDDEEFF", (result as DeepLink.MacAddress).mac)
    }

    @Test
    fun `parseUri with illumidel device host and colon-separated MAC path normalizes MacAddress`() {
        val uri = mockUri("illumidel://device/AA:BB:CC:DD:EE:01")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.MacAddress)
        assertEquals("AABBCCDDEE01", (result as DeepLink.MacAddress).mac)
    }

    @Test
    fun `parseUri with illumidel dash-separated MAC host normalizes MacAddress`() {
        val uri = mockUri("illumidel://aa-bb-cc-dd-ee-01")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.MacAddress)
        assertEquals("AABBCCDDEE01", (result as DeepLink.MacAddress).mac)
    }

    @Test
    fun `parseUri with illumidel colon-separated MAC authority normalizes MacAddress`() {
        val uri = mockUri("illumidel://AA:BB:CC:DD:EE:01")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.MacAddress)
        assertEquals("AABBCCDDEE01", (result as DeepLink.MacAddress).mac)
    }

    @Test
    fun `parseUri with illumidel colon-separated hex MAC authority normalizes MacAddress`() {
        val uri = mockUri("illumidel://aa:bb:cc:dd:ee:ff")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.MacAddress)
        assertEquals("AABBCCDDEEFF", (result as DeepLink.MacAddress).mac)
    }

    @Test
    fun `parseUri with illumidel device host and non-MAC path returns null`() {
        val uri = mockUri("illumidel://device/not-a-mac")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    @Test
    fun `parseUri with illumidel device host and empty path returns null`() {
        val uri = mockUri("illumidel://device")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    @Test
    fun `parseUri with illumidel device host and extra path segments returns null`() {
        val uri = mockUri("illumidel://device/AABBCCDDEEFF/extra")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    @Test
    fun `parseUri with illumidel direct identifier and extra path segments returns null`() {
        val uri = mockUri("illumidel://AABBCCDDEEFF/extra")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    @Test
    fun `parseUri with wled scheme returns null`() {
        val uri = mockUri("wled://192.168.1.50")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    @Test
    fun `parseUri with http scheme and AP mode IP returns ApMode`() {
        val uri = mockUri("http://4.3.2.1")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.ApMode)
    }

    @Test
    fun `parseUri with http scheme and AP mode IP with path returns ApMode`() {
        val uri = mockUri("http://4.3.2.1/edit")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.ApMode)
    }

    @Test
    fun `parseUri with http scheme and non-AP IP returns null`() {
        val uri = mockUri("http://192.168.1.50")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    @Test
    fun `parseUri with https scheme returns null`() {
        val uri = mockUri("https://4.3.2.1")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    @Test
    fun `parseUri with unsupported scheme returns null`() {
        val uri = mockUri("ftp://192.168.1.50")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    @Test
    fun `parseUri with null returns null`() {
        val result = handler.parseUri(null)

        assertNull(result)
    }

    @Test
    fun `parseUri with illumidel scheme and empty host returns null`() {
        val uri = mockUri("illumidel://")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    // --- parseIntent tests ---

    @Test
    fun `parseIntent with ACTION_VIEW and valid illumidel uri returns DeepLink`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.data } returns mockUri("illumidel://192.168.1.50")
        val result = handler.parseIntent(intent)

        assertTrue(result is DeepLink.Address)
    }

    @Test
    fun `parseIntent with ACTION_MAIN returns null`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_MAIN
        every { intent.data } returns mockUri("illumidel://192.168.1.50")
        val result = handler.parseIntent(intent)

        assertNull(result)
    }

    @Test
    fun `parseIntent with null intent returns null`() {
        val result = handler.parseIntent(null)

        assertNull(result)
    }

    @Test
    fun `parseIntent with ACTION_VIEW but no data returns null`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.data } returns null
        val result = handler.parseIntent(intent)

        assertNull(result)
    }

    // --- isMacAddress tests ---

    @Test
    fun `isMacAddress with valid 12 hex chars returns true`() {
        assertTrue(handler.isMacAddress("AABBCCDDEEFF"))
    }

    @Test
    fun `isMacAddress with valid lowercase 12 hex chars returns true`() {
        assertTrue(handler.isMacAddress("aabbccddeeff"))
    }

    @Test
    fun `isMacAddress with valid mixed case 12 hex chars returns true`() {
        assertTrue(handler.isMacAddress("AaBbCcDdEeFf"))
    }

    @Test
    fun `isMacAddress with 11 chars returns false`() {
        assertFalse(handler.isMacAddress("AABBCCDDEE"))
    }

    @Test
    fun `isMacAddress with 13 chars returns false`() {
        assertFalse(handler.isMacAddress("AABBCCDDEEFFF"))
    }

    @Test
    fun `isMacAddress with non-hex chars returns false`() {
        assertFalse(handler.isMacAddress("GGHHIIJJKKLL"))
    }

    @Test
    fun `isMacAddress with colons returns false`() {
        assertFalse(handler.isMacAddress("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `isMacAddress with dashes returns false`() {
        assertFalse(handler.isMacAddress("AA-BB-CC-DD-EE-FF"))
    }

    @Test
    fun `isMacAddress with IP address returns false`() {
        assertFalse(handler.isMacAddress("192.168.1.50"))
    }

    @Test
    fun `isMacAddress with empty string returns false`() {
        assertFalse(handler.isMacAddress(""))
    }

    @Test
    fun `isMacAddress with hostname returns false`() {
        assertFalse(handler.isMacAddress("illumidel.local"))
    }
}
