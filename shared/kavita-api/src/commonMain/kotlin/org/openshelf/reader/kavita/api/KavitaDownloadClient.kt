package org.openshelf.reader.kavita.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import org.openshelf.reader.source.api.DownloadResult
import org.openshelf.reader.source.api.DownloadSink
import org.openshelf.reader.source.api.RemoteDownloadRequest
import org.openshelf.reader.source.api.SourceError

class KavitaDownloadClient(
    private val httpClient: HttpClient,
    private val baseUrl: KavitaBaseUrl,
    private val apiKey: String,
    private val bufferSize: Int = DefaultBufferSize,
) {
    init {
        require(apiKey.isNotBlank()) { "Kavita API key must not be blank." }
        require(bufferSize > 0) { "Download buffer size must be positive." }
    }

    suspend fun downloadFile(
        request: RemoteDownloadRequest,
        sink: DownloadSink,
    ): DownloadResult {
        return try {
            val response = httpClient.get("${baseUrl.value}/api/Download/chapter") {
                header(KavitaApiKeyHeader, apiKey)
                parameter("chapterId", request.bookId.value)
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val bytesWritten = response.streamTo(sink)
                    DownloadResult.Success(
                        fileId = request.fileId,
                        format = request.expectedFormat,
                        bytesWritten = bytesWritten,
                        fileName = request.expectedFileName,
                        contentType = response.headers[HttpHeaders.ContentType],
                    )
                }

                HttpStatusCode.NotFound,
                HttpStatusCode.NoContent,
                -> DownloadResult.Failure(SourceError.NotFound)

                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                -> DownloadResult.Failure(SourceError.Unauthorized)

                else -> DownloadResult.Failure(
                    SourceError.UnexpectedResponse(
                        status = response.status.value,
                        bodySnippet = response.redactedBodySnippet(apiKey),
                    ),
                )
            }
        } catch (error: IOException) {
            DownloadResult.Failure(SourceError.NetworkUnavailable)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            DownloadResult.Failure(SourceError.Unknown("Kavita download failed unexpectedly."))
        }
    }

    override fun toString(): String {
        return "KavitaDownloadClient(baseUrl=$baseUrl, apiKey=$KavitaRedactedSecret)"
    }

    private suspend fun io.ktor.client.statement.HttpResponse.streamTo(sink: DownloadSink): Long {
        val channel = bodyAsChannel()
        val buffer = ByteArray(bufferSize)
        var total = 0L

        while (true) {
            val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
            if (bytesRead == -1) break
            if (bytesRead == 0) continue

            sink.write(buffer.copyOf(bytesRead))
            total += bytesRead
        }

        return total
    }

    private companion object {
        const val DefaultBufferSize = 8 * 1024
    }
}
