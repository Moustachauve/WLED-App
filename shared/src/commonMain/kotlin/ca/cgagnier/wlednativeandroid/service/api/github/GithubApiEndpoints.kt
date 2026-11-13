package ca.cgagnier.wlednativeandroid.service.api.github

import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.shared.IllumidelSecrets
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.exclude
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import okio.use
import ca.cgagnier.wlednativeandroid.shared.fileSystem as defaultFileSystem

interface GithubApiEndpoints {
    suspend fun getAllReleases(repoOwner: String, repoName: String): List<Release>

    fun downloadReleaseBinary(
        repoOwner: String,
        repoName: String,
        assetId: Int,
        destinationPath: Path,
    ): Flow<DownloadState>
}

class KtorGithubApiEndpoints(
    private val httpClient: HttpClient,
    private val baseUrl: String = GITHUB_BASE_URL,
    private val fs: FileSystem = defaultFileSystem,
    private val githubToken: String = IllumidelSecrets.GITHUB_TOKEN,
) : GithubApiEndpoints {

    override suspend fun getAllReleases(repoOwner: String, repoName: String): List<Release> {
        val url = "$baseUrl/repos/$repoOwner/$repoName/releases"
        return httpClient.get(url) {
            applyAuthHeaderIfNeeded(repoOwner, repoName)
        }.body()
    }

    // Emits DownloadState.Failed on any HTTP, network, or file I/O error
    @Suppress("TooGenericExceptionCaught")
    override fun downloadReleaseBinary(
        repoOwner: String,
        repoName: String,
        assetId: Int,
        destinationPath: Path,
    ): Flow<DownloadState> = flow {
        try {
            emit(DownloadState.Downloading(0))
            val url = "$baseUrl/repos/$repoOwner/$repoName/releases/assets/$assetId"
            httpClient.prepareGet(url) {
                exclude(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.OctetStream.toString())
                applyAuthHeaderIfNeeded(repoOwner, repoName)
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw IOException("Download failed: HTTP ${response.status.value} ${response.status.description}")
                }
                if (response.contentType()?.match(ContentType.Application.Json) == true) {
                    throw IOException("Download failed: received JSON metadata instead of binary stream")
                }
                val channel = response.bodyAsChannel()
                val totalBytes = response.contentLength() ?: -1L
                emitAll(channel.saveFile(destinationPath, totalBytes))
            }
        } catch (e: CancellationException) {
            deleteQuietly(destinationPath)
            throw e
        } catch (e: Exception) {
            deleteQuietly(destinationPath)
            emit(DownloadState.Failed(e))
        }
    }.flowOn(Dispatchers.IO)

    // Emits DownloadState.Failed on any I/O or buffer write error
    @Suppress("TooGenericExceptionCaught")
    private fun ByteReadChannel.saveFile(destinationPath: Path, totalBytes: Long): Flow<DownloadState> =
        flow<DownloadState> {
            val parent = destinationPath.parent
            val tempPath = if (parent != null) {
                parent / "${destinationPath.name}.part"
            } else {
                destinationPath.resolve("${destinationPath.name}.part")
            }
            try {
                parent?.let(fs::createDirectories)
                fs.sink(tempPath).buffer().use { sink ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var progressBytes = 0L
                    while (!isClosedForRead) {
                        val bytesRead = readAvailable(buffer, 0, buffer.size)
                        if (bytesRead <= 0) break
                        sink.write(buffer, 0, bytesRead)
                        progressBytes += bytesRead
                        if (totalBytes > 0) {
                            emit(DownloadState.Downloading((progressBytes * PERCENT_MAX / totalBytes).toInt()))
                        }
                    }
                }
                deleteQuietly(destinationPath)
                fs.atomicMove(tempPath, destinationPath)
                emit(DownloadState.Finished)
            } catch (e: CancellationException) {
                deleteQuietly(tempPath)
                deleteQuietly(destinationPath)
                throw e
            } catch (e: Exception) {
                deleteQuietly(tempPath)
                deleteQuietly(destinationPath)
                emit(DownloadState.Failed(e))
            }
        }.flowOn(Dispatchers.IO).distinctUntilChanged()

    @Suppress("TooGenericExceptionCaught") // Best-effort cleanup of incomplete or stale cache files
    private fun deleteQuietly(path: Path) {
        try {
            fs.delete(path)
        } catch (_: Exception) {
        }
    }

    private fun HttpRequestBuilder.applyAuthHeaderIfNeeded(repoOwner: String, repoName: String) {
        if (githubToken.isNotBlank() &&
            "$repoOwner/$repoName".equals(Repository.DEFAULT_OWNER_REPO, ignoreCase = true)
        ) {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
        }
    }

    companion object {
        const val GITHUB_BASE_URL = "https://api.github.com"
        private const val PERCENT_MAX = 100
        private const val BUFFER_SIZE = 8192
    }
}
