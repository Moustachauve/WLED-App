package ca.cgagnier.wlednativeandroid.service.api.github

import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.exclude
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GithubApiEndpoints {

    override suspend fun getAllReleases(repoOwner: String, repoName: String): List<Release> {
        val url = "$baseUrl/repos/$repoOwner/$repoName/releases"
        return httpClient.get(url).body()
    }

    // Emits DownloadState.Failed on any HTTP, network, or file I/O error
    @Suppress("TooGenericExceptionCaught")
    override fun downloadReleaseBinary(
        repoOwner: String,
        repoName: String,
        assetId: Int,
        destinationPath: Path,
    ): Flow<DownloadState> = flow {
        val partPath = partPathFor(destinationPath)
        var committed = false
        try {
            emit(DownloadState.Downloading(0))
            val url = "$baseUrl/repos/$repoOwner/$repoName/releases/assets/$assetId"
            httpClient.prepareGet(url) {
                exclude(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.OctetStream.toString())
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw IOException(
                        "Download failed: HTTP ${response.status.value} ${response.status.description}",
                    )
                }
                val responseContentType = response.contentType()?.withoutParameters()
                if (
                    responseContentType == ContentType.Application.Json ||
                    responseContentType?.contentSubtype?.endsWith("+json", ignoreCase = true) == true
                ) {
                    throw IOException("Unexpected JSON response when downloading release binary from $url")
                }
                val channel = response.bodyAsChannel()
                val totalBytes = response.contentLength() ?: -1L
                channel.writeToPartAndCommit(partPath, destinationPath, totalBytes) { progress ->
                    emit(DownloadState.Downloading(progress))
                }
                committed = true
            }
            emit(DownloadState.Finished)
        } catch (e: CancellationException) {
            if (!committed) {
                cleanupFailedDownload(partPath, destinationPath)
            }
            throw e
        } catch (e: Exception) {
            if (!committed) {
                cleanupFailedDownload(partPath, destinationPath)
            }
            emit(DownloadState.Failed(e))
        }
    }.distinctUntilChanged().flowOn(ioDispatcher)

    private suspend fun ByteReadChannel.writeToPartAndCommit(
        partPath: Path,
        destinationPath: Path,
        totalBytes: Long,
        onProgress: suspend (Int) -> Unit,
    ) {
        destinationPath.parent?.let { parent ->
            fs.createDirectories(parent)
        }
        var progressBytes = 0L
        fs.sink(partPath).buffer().use { sink ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (!isClosedForRead) {
                val bytesRead = readAvailable(buffer, 0, buffer.size)
                if (bytesRead <= 0) break
                sink.write(buffer, 0, bytesRead)
                progressBytes += bytesRead
                if (totalBytes > 0) {
                    onProgress((progressBytes * PERCENT_MAX / totalBytes).toInt())
                }
            }
        }
        closedCause?.let { throw it }
        val mismatchError = when {
            progressBytes == 0L -> "Download returned empty response body"

            totalBytes >= 0 && progressBytes != totalBytes ->
                "Incomplete download: expected $totalBytes bytes, got $progressBytes bytes"

            else -> null
        }
        if (mismatchError != null) {
            throw IOException(mismatchError)
        }
        fs.delete(destinationPath, mustExist = false)
        fs.atomicMove(partPath, destinationPath)
    }

    private fun partPathFor(destinationPath: Path): Path =
        destinationPath.parent?.div("${destinationPath.name}.part") ?: "${destinationPath.name}.part".toPath()

    // Best-effort cleanup of temporary .part files and any pre-created empty or stale
    // destination file when a download fails or is cancelled before atomicMove completes.
    @Suppress("TooGenericExceptionCaught")
    private fun cleanupFailedDownload(partPath: Path, destinationPath: Path) {
        try {
            fs.delete(partPath, mustExist = false)
        } catch (_: Exception) {
        }
        try {
            fs.delete(destinationPath, mustExist = false)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val GITHUB_BASE_URL = "https://api.github.com"
        private const val PERCENT_MAX = 100
        private const val BUFFER_SIZE = 8192
    }
}
