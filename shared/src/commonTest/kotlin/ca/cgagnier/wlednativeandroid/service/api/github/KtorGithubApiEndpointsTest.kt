package ca.cgagnier.wlednativeandroid.service.api.github

import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.service.api.installDeviceHttpClientDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorGithubApiEndpointsTest {

    private val fs = FileSystem.SYSTEM

    private fun withTempDir(block: suspend (Path) -> Unit) = runTest {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
            "illumidel_github_test_${Random.nextLong(0, Long.MAX_VALUE)}"
        try {
            block(dir)
        } finally {
            fs.deleteRecursively(dir)
        }
    }

    @Test
    fun downloadReleaseBinarySendsOnlyOctetStreamAcceptHeaderWithDefaultClientPlugins() = withTempDir { dir ->
        val recordedRequests = mutableListOf<HttpRequestData>()
        val firmwareBytes = byteArrayOf(0xE9.toByte(), 0x05, 0x02, 0x40)
        val client = HttpClient(
            MockEngine { request ->
                recordedRequests += request
                respond(
                    content = firmwareBytes,
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf(ContentType.Application.OctetStream.toString()),
                        HttpHeaders.ContentLength to listOf(firmwareBytes.size.toString()),
                    ),
                )
            },
        ) {
            installDeviceHttpClientDefaults()
        }
        val endpoints = KtorGithubApiEndpoints(
            httpClient = client,
            fs = fs,
            githubToken = "test-token",
        )
        val target = dir / "v1.6.1" / "v1.6.1_ESP32_fr.bin"

        val states = endpoints.downloadReleaseBinary("Illumidel", "App-Releases", 740317386, target).toList()

        assertEquals(DownloadState.Finished, states.last())
        val request = recordedRequests.single()
        assertEquals(listOf("application/octet-stream"), request.headers.getAll(HttpHeaders.Accept))
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
        assertContentEquals(firmwareBytes, fs.read(target) { readByteArray() })
    }

    @Test
    fun downloadReleaseBinaryExcludesJsonAcceptHeaderEvenWithVanillaContentNegotiation() = withTempDir { dir ->
        val recordedRequests = mutableListOf<HttpRequestData>()
        val firmwareBytes = byteArrayOf(0xE9.toByte(), 0x01, 0x02, 0x03)
        val client = HttpClient(
            MockEngine { request ->
                recordedRequests += request
                respond(
                    content = firmwareBytes,
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf(ContentType.Application.OctetStream.toString()),
                    ),
                )
            },
        ) {
            install(ContentNegotiation) { json() }
        }
        val endpoints = KtorGithubApiEndpoints(httpClient = client, fs = fs, githubToken = "")
        val target = dir / "v1.5.2_ESP32_en.bin"

        val states = endpoints.downloadReleaseBinary("Illumidel", "App-Releases", 42, target).toList()

        assertEquals(DownloadState.Finished, states.last())
        assertEquals(listOf("application/octet-stream"), recordedRequests.single().headers.getAll(HttpHeaders.Accept))
    }

    @Test
    fun downloadReleaseBinaryFollowsCrossOriginRedirectWithoutLeakingAuthHeader() = withTempDir { dir ->
        val recordedRequests = mutableListOf<HttpRequestData>()
        val firmwareBytes = ByteArray(16_384) { index -> (index and 0xFF).toByte() }
        val redirectUrl = "https://objects.githubusercontent.com/github-production-release-asset/v1.6.1_ESP32_fr.bin"
        val client = HttpClient(
            MockEngine { request ->
                recordedRequests += request
                if (request.url.host == "api.github.com") {
                    respond(
                        content = "",
                        status = HttpStatusCode.Found,
                        headers = headersOf(HttpHeaders.Location, redirectUrl),
                    )
                } else {
                    respond(
                        content = firmwareBytes,
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.OctetStream.toString()),
                            HttpHeaders.ContentLength to listOf(firmwareBytes.size.toString()),
                        ),
                    )
                }
            },
        ) {
            installDeviceHttpClientDefaults()
        }
        val (owner, repo) = Repository.DEFAULT_OWNER_REPO.split("/")
        val endpoints = KtorGithubApiEndpoints(httpClient = client, fs = fs, githubToken = "secret-pat")
        val target = dir / "v1.6.1_ESP32_fr.bin"

        val states = endpoints.downloadReleaseBinary(owner, repo, 99, target).toList()

        assertEquals(DownloadState.Finished, states.last())
        assertEquals(2, recordedRequests.size)
        assertEquals("Bearer secret-pat", recordedRequests[0].headers[HttpHeaders.Authorization])
        assertNull(recordedRequests[1].headers[HttpHeaders.Authorization])
        assertContentEquals(firmwareBytes, fs.read(target) { readByteArray() })
        assertFalse(fs.exists(dir / "v1.6.1_ESP32_fr.bin.part"))
    }

    @Test
    fun downloadReleaseBinaryRejectsJsonMetadataResponseWithoutSavingCorruptFile() = withTempDir { dir ->
        val client = HttpClient(
            MockEngine {
                respond(
                    content = """{"url":"https://api.github.com/repos/Illumidel/App-Releases/releases/assets/1"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    ),
                )
            },
        ) {
            installDeviceHttpClientDefaults()
        }
        val endpoints = KtorGithubApiEndpoints(httpClient = client, fs = fs, githubToken = "test-token")
        val target = dir / "v1.5.2_ESP32_en.bin"

        val states = endpoints.downloadReleaseBinary("Illumidel", "App-Releases", 1, target).toList()

        assertIs<DownloadState.Failed>(states.last())
        assertFalse(fs.exists(target))
        assertTrue(fs.listOrNull(dir).orEmpty().isEmpty())
    }
}
