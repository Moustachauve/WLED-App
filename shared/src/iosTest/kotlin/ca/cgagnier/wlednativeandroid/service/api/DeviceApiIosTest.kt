package ca.cgagnier.wlednativeandroid.service.api

import ca.cgagnier.wlednativeandroid.model.Device
import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPBodyStream
import platform.Foundation.HTTPMethod
import platform.Foundation.NSCachedURLResponse
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSInputStream
import platform.Foundation.NSMutableData
import platform.Foundation.NSURLCacheStoragePolicy
import platform.Foundation.NSURLProtocol
import platform.Foundation.NSURLProtocolClientProtocol
import platform.Foundation.NSURLProtocolMeta
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalAndRemoteCacheData
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.allHTTPHeaderFields
import platform.Foundation.appendBytes
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val STREAM_BUFFER_SIZE = 4096
private const val HTTP_OK_STATUS = 200L

@OptIn(ExperimentalAtomicApi::class)
private val capturedRequestCount = AtomicInt(0)

@OptIn(ExperimentalAtomicApi::class)
private val lastCapturedRequest = AtomicReference<RecordedDarwinRequest?>(null)

class DeviceApiIosTest {

    /** Applies the client's session configuration to a fresh `NSURLSessionConfiguration`. */
    private fun resolvedSessionConfiguration(
        configureSession: (NSURLSessionConfiguration) -> Unit = {},
    ): NSURLSessionConfiguration =
        DeviceApiFactory.createHttpClient(configureSession = configureSession).use { client ->
            val engineConfig = assertIs<DarwinClientEngineConfig>(client.engine.config)
            NSURLSessionConfiguration.defaultSessionConfiguration.apply(engineConfig.sessionConfig)
        }

    @Test
    fun createHttpClientDisablesResponseCaching() {
        assertEquals(
            NSURLRequestReloadIgnoringLocalAndRemoteCacheData,
            resolvedSessionConfiguration().requestCachePolicy,
        )
    }

    @Test
    fun createHttpClientAppliesCustomSessionConfiguration() {
        val configuration = resolvedSessionConfiguration { it.HTTPMaximumConnectionsPerHost = 3 }

        assertEquals(3, configuration.HTTPMaximumConnectionsPerHost)
    }

    @OptIn(BetaInteropApi::class, ExperimentalAtomicApi::class)
    @Test
    fun updateDeviceSendsNonChunkedRequestWithContentLengthOnDarwin() = runTest {
        capturedRequestCount.store(0)
        lastCapturedRequest.store(null)
        val firmware = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val factory = DeviceApiFactory.create { config ->
            config.protocolClasses = listOf(CapturingUrlProtocol.`class`())
        }
        val api = factory.create(Device(macAddress = "AA:BB:CC:DD:EE:FF", address = "10.0.0.100"))

        val response = api.updateDevice(firmware, "wled.bin")

        assertTrue(response.isSuccessful)
        assertEquals("Update Success!", response.body)
        assertEquals(1, capturedRequestCount.load())
        val recorded = assertNotNull(lastCapturedRequest.load())
        assertEquals("POST", recorded.method)
        assertFalse(
            recorded.headers.keys.any { it.equals("Transfer-Encoding", ignoreCase = true) },
        )
        val contentLengthHeader = recorded.headers.entries.firstOrNull {
            it.key.equals("Content-Length", ignoreCase = true)
        }?.value
        assertNotNull(contentLengthHeader)
        assertEquals(recorded.bodyBytes.size.toString(), contentLengthHeader)
        val bodyText = recorded.bodyBytes.decodeToString()
        assertTrue(bodyText.contains("filename=\"wled.bin\""))
        assertTrue(bodyText.contains("Content-Type: application/octet-stream"))
    }
}

private class RecordedDarwinRequest(val method: String?, val headers: Map<String, String>, val bodyBytes: ByteArray)

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class, ExperimentalAtomicApi::class)
private class CapturingUrlProtocol : NSURLProtocol {
    @OverrideInit
    constructor(
        request: NSURLRequest,
        cachedResponse: NSCachedURLResponse?,
        client: NSURLProtocolClientProtocol?,
    ) : super(request, cachedResponse, client)

    override fun startLoading() {
        val bodyBytes = request.HTTPBody?.toByteArray()
            ?: request.HTTPBodyStream?.readAllBytes()
            ?: ByteArray(0)
        val headers = request.allHTTPHeaderFields.orEmpty().entries.associate { (key, value) ->
            key.toString() to value.toString()
        }
        lastCapturedRequest.store(
            RecordedDarwinRequest(
                method = request.HTTPMethod,
                headers = headers,
                bodyBytes = bodyBytes,
            ),
        )
        capturedRequestCount.incrementAndFetch()
        val url = request.URL
        if (url != null) {
            val response = NSHTTPURLResponse(
                uRL = url,
                statusCode = HTTP_OK_STATUS,
                HTTPVersion = "HTTP/1.1",
                headerFields = mapOf("Content-Type" to "text/html"),
            )
            client?.URLProtocol(
                this,
                didReceiveResponse = response,
                cacheStoragePolicy = NSURLCacheStoragePolicy.NSURLCacheStorageNotAllowed,
            )
        }
        val responseBytes = "Update Success!".encodeToByteArray()
        val data = responseBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = responseBytes.size.toULong())
        }
        client?.URLProtocol(this, didLoadData = data)
        client?.URLProtocolDidFinishLoading(this)
    }

    override fun stopLoading() = Unit

    companion object : NSURLProtocolMeta() {
        override fun canInitWithRequest(request: NSURLRequest): Boolean = true

        override fun canonicalRequestForRequest(request: NSURLRequest): NSURLRequest = request

        override fun requestIsCacheEquivalent(a: NSURLRequest, toRequest: NSURLRequest): Boolean = false
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSInputStream.readAllBytes(): ByteArray {
    open()
    try {
        val collected = NSMutableData()
        memScoped {
            val buffer = allocArray<UByteVar>(STREAM_BUFFER_SIZE)
            while (hasBytesAvailable) {
                val bytesRead = read(buffer, maxLength = STREAM_BUFFER_SIZE.toULong())
                if (bytesRead <= 0L) break
                collected.appendBytes(buffer, length = bytesRead.toULong())
            }
        }
        return collected.toByteArray()
    } finally {
        close()
    }
}
