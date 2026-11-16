package ca.cgagnier.wlednativeandroid.service.api.github

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.service.api.installDeviceHttpClientDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.io.IOException

class GithubApiTest {

    private val testJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    // Real release structure from https://api.github.com/repos/wled/WLED/releases/tags/v16.0.1
    private val sampleReleasesJson = """
        [
            {
                "url": "https://api.github.com/repos/wled/WLED/releases/347085524",
                "assets_url": "https://api.github.com/repos/wled/WLED/releases/347085524/assets",
                "upload_url": "https://uploads.github.com/repos/wled/WLED/releases/347085524/assets{?name,label}",
                "html_url": "https://github.com/wled/WLED/releases/tag/v16.0.1",
                "id": 347085524,
                "node_id": "RE_kwDOBJbHAc4UsHDU",
                "tag_name": "v16.0.1",
                "target_commitish": "main",
                "name": "WLED Release 16.0.1",
                "draft": false,
                "prerelease": false,
                "created_at": "2026-06-30T17:15:37Z",
                "published_at": "2026-06-30T21:12:47Z",
                "tarball_url": "https://api.github.com/repos/wled/WLED/tarball/v16.0.1",
                "zipball_url": "https://api.github.com/repos/wled/WLED/zipball/v16.0.1",
                "body": "# WLED Version 16.0.1 Announcement",
                "author": {
                    "login": "github-actions[bot]",
                    "id": 41898282,
                    "node_id": "MDM6Qm90NDE4OTgyODI=",
                    "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                    "gravatar_id": "",
                    "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                    "html_url": "https://github.com/apps/github-actions",
                    "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                    "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                    "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                    "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                    "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                    "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                    "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                    "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                    "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                    "type": "Bot",
                    "site_admin": false
                },
                "assets": [
                    {
                        "url": "https://api.github.com/repos/wled/WLED/releases/assets/462495760",
                        "id": 462495760,
                        "node_id": "RA_kwDOBJbHAc4bkSAQ",
                        "name": "WLED_16.0.1_ESP32.bin",
                        "label": "",
                        "uploader": {
                            "login": "github-actions[bot]",
                            "id": 41898282,
                            "node_id": "MDM6Qm90NDE4OTgyODI=",
                            "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                            "gravatar_id": "",
                            "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                            "html_url": "https://github.com/apps/github-actions",
                            "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                            "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                            "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                            "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                            "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                            "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                            "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                            "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                            "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                            "type": "Bot",
                            "site_admin": false
                        },
                        "content_type": "application/octet-stream",
                        "state": "uploaded",
                        "size": 1729440,
                        "download_count": 5218,
                        "created_at": "2026-06-30T20:28:04Z",
                        "updated_at": "2026-06-30T20:28:04Z",
                        "browser_download_url": "https://github.com/wled/WLED/releases/download/v16.0.1/WLED_16.0.1_ESP32.bin"
                    }
                ]
            }
        ]
    """.trimIndent()

    private fun makeGithubApi(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GithubApi {
        val httpClient = HttpClient(MockEngine { request -> handler(request) }) {
            installDeviceHttpClientDefaults(testJson)
        }
        return GithubApi(KtorGithubApiEndpoints(httpClient))
    }

    private fun makeTestAsset() = Asset(
        versionId = 1L,
        name = "WLED_16.0.1_ESP32.bin",
        size = 1729440L,
        downloadUrl = "https://github.com/wled/WLED/releases/download/v16.0.1/WLED_16.0.1_ESP32.bin",
        assetId = 462495760,
    )

    @Test
    fun `getAllReleases returns successful release list`() = runTest {
        val githubApi = makeGithubApi { request ->
            assertEquals("/repos/wled/WLED/releases", request.url.encodedPath)
            respond(
                content = sampleReleasesJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = githubApi.getAllReleases("wled", "WLED")

        assertTrue(result.isSuccess)
        val releases = result.getOrNull()
        assertEquals(1, releases?.size)
        assertEquals("v16.0.1", releases?.first()?.tagName)
    }

    @Test
    fun `getAllReleases handles network error gracefully`() = runTest {
        val githubApi = makeGithubApi { _ ->
            respond(
                content = "Rate limited",
                status = HttpStatusCode.Forbidden,
            )
        }

        val result = githubApi.getAllReleases("wled", "WLED")

        assertTrue(result.isFailure)
    }

    private val sampleBinary = ByteArray(1024) { (it % 256).toByte() }

    private fun MockRequestHandleScope.respondBinary(bytes: ByteArray = sampleBinary): HttpResponseData = respond(
        content = bytes,
        status = HttpStatusCode.OK,
        headers = headersOf(
            HttpHeaders.ContentType to listOf("application/octet-stream"),
            HttpHeaders.ContentLength to listOf(bytes.size.toString()),
        ),
    )

    private fun assertDownloadCleanedUp(targetFile: File) {
        assertFalse(targetFile.exists())
        assertFalse(File("${targetFile.absolutePath}.part").exists())
    }

    @Test
    fun `downloadReleaseBinary requests octet-stream without JSON Accept`() = runTest {
        var acceptHeaders: List<String>? = null
        val githubApi = makeGithubApi { request ->
            acceptHeaders = request.headers.getAll(HttpHeaders.Accept)
            respondBinary()
        }

        val targetFile = File.createTempFile("github_test_accept_header", ".bin").apply {
            deleteOnExit()
        }

        githubApi.downloadReleaseBinary(
            asset = makeTestAsset(),
            repoOwner = "wled",
            repoName = "WLED",
            targetFile = targetFile,
        ).toList()

        assertEquals(listOf(ContentType.Application.OctetStream.toString()), acceptHeaders)
    }

    @Test
    fun `downloadReleaseBinary streams bytes and reports progress`() = runTest {
        val githubApi = makeGithubApi { request ->
            assertEquals("/repos/wled/WLED/releases/assets/462495760", request.url.encodedPath)
            respondBinary()
        }

        val targetFile = File.createTempFile("github_test_download", ".bin").apply {
            deleteOnExit()
        }

        val states = githubApi.downloadReleaseBinary(
            asset = makeTestAsset(),
            repoOwner = "wled",
            repoName = "WLED",
            targetFile = targetFile,
        ).toList()

        assertTrue(states.any { it is DownloadState.Downloading })
        assertTrue(states.last() is DownloadState.Finished)
        assertEquals(1024L, targetFile.length())
        assertFalse(File("${targetFile.absolutePath}.part").exists())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failedDownloadCases")
    fun `downloadReleaseBinary fails and cleans up files on invalid response`(case: FailedDownloadCase) = runTest {
        val githubApi = makeGithubApi { request -> case.respondWith(this, request) }
        val targetFile = File.createTempFile("github_test_download_fail", ".bin")

        val states = githubApi.downloadReleaseBinary(
            asset = makeTestAsset(),
            repoOwner = "wled",
            repoName = "WLED",
            targetFile = targetFile,
        ).toList()

        val lastState = states.last()
        assertTrue(lastState is DownloadState.Failed)
        assertEquals(case.expectedErrorMessage, (lastState as DownloadState.Failed).error?.message)
        assertDownloadCleanedUp(targetFile)
    }

    @Test
    fun `downloadReleaseBinary cleans up file and rethrows CancellationException on cancellation`() = runTest {
        val githubApi = makeGithubApi { respondBinary() }

        val targetFile = File.createTempFile("github_test_download_cancel", ".bin")

        assertThrows<CancellationException> {
            githubApi.downloadReleaseBinary(
                asset = makeTestAsset(),
                repoOwner = "wled",
                repoName = "WLED",
                targetFile = targetFile,
            ).collect {
                throw CancellationException("User cancelled download")
            }
        }

        assertDownloadCleanedUp(targetFile)
    }

    @Test
    fun `downloadReleaseBinary preserves file when collector cancels after Finished`() = runTest {
        val githubApi = makeGithubApi { respondBinary() }

        val targetFile = File.createTempFile("github_test_download_first_finished", ".bin").apply {
            deleteOnExit()
        }

        githubApi.downloadReleaseBinary(
            asset = makeTestAsset(),
            repoOwner = "wled",
            repoName = "WLED",
            targetFile = targetFile,
        ).first { it is DownloadState.Finished }

        assertTrue(targetFile.exists())
        assertEquals(1024L, targetFile.length())
        assertFalse(File("${targetFile.absolutePath}.part").exists())
    }

    data class FailedDownloadCase(
        val label: String,
        val expectedErrorMessage: String,
        val respondWith: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        override fun toString(): String = label
    }

    companion object {
        private const val SAMPLE_ASSET_URL =
            "https://api.github.com/repos/wled/WLED/releases/assets/462495760"

        private fun staticFailureCase(
            label: String,
            expectedErrorMessage: String,
            content: ByteArray,
            status: HttpStatusCode = HttpStatusCode.OK,
            headers: Headers = headersOf(
                HttpHeaders.ContentType to listOf("application/octet-stream"),
                HttpHeaders.ContentLength to listOf(content.size.toString()),
            ),
        ) = FailedDownloadCase(label = label, expectedErrorMessage = expectedErrorMessage) {
            respond(content = content, status = status, headers = headers)
        }

        @JvmStatic
        fun failedDownloadCases(): List<FailedDownloadCase> {
            val jsonMetadata = """{"url":"$SAMPLE_ASSET_URL"}""".padEnd(1538, ' ')
            return listOf(
                staticFailureCase(
                    label = "HTTP error",
                    expectedErrorMessage = "Download failed: HTTP 404 Not Found",
                    content = "404 Not Found".encodeToByteArray(),
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                ),
                staticFailureCase(
                    label = "JSON metadata response",
                    expectedErrorMessage = "Unexpected JSON response when downloading release binary from " +
                        SAMPLE_ASSET_URL,
                    content = jsonMetadata.encodeToByteArray(),
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.ContentLength to listOf(jsonMetadata.length.toString()),
                    ),
                ),
                staticFailureCase(
                    label = "truncated before Content-Length",
                    expectedErrorMessage = "Incomplete download: expected 2048 bytes, got 1024 bytes",
                    content = ByteArray(1024) { (it % 256).toByte() },
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/octet-stream"),
                        HttpHeaders.ContentLength to listOf("2048"),
                    ),
                ),
                FailedDownloadCase(
                    label = "chunked stream closed with error",
                    expectedErrorMessage = "Stream reset mid-transfer",
                ) {
                    val channel = ByteChannel(autoFlush = true)
                    channel.writeFully(ByteArray(512))
                    channel.close(IOException("Stream reset mid-transfer"))
                    respond(
                        content = channel,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"),
                    )
                },
                staticFailureCase(
                    label = "empty response body",
                    expectedErrorMessage = "Download returned empty response body",
                    content = ByteArray(0),
                ),
            )
        }
    }
}
