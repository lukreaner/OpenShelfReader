package org.openshelf.reader.kavita.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.source.api.RemoteBook
import org.openshelf.reader.source.api.RemoteBookDetails
import org.openshelf.reader.source.api.RemoteBookId
import org.openshelf.reader.source.api.RemoteFileId
import org.openshelf.reader.source.api.RemoteLibrary
import org.openshelf.reader.source.api.RemoteLibraryId
import org.openshelf.reader.source.api.RemotePublicationFile
import org.openshelf.reader.source.api.RemoteSeries
import org.openshelf.reader.source.api.RemoteSeriesId
import org.openshelf.reader.source.api.SourceAdapterException
import org.openshelf.reader.source.api.SourceError
import org.openshelf.reader.source.api.SourceId

class KavitaBrowsingClient(
    private val httpClient: HttpClient,
    private val sourceId: SourceId,
    private val baseUrl: KavitaBaseUrl,
    private val apiKey: String,
    private val pageSize: Int = 200,
) {
    init {
        require(apiKey.isNotBlank()) { "Kavita API key must not be blank." }
        require(pageSize > 0) { "Kavita page size must be positive." }
    }

    suspend fun listLibraries(): List<RemoteLibrary> {
        val payload = getJson("/api/Library/libraries")
        return payload.arrayObjects().map { library ->
            RemoteLibrary(
                id = RemoteLibraryId(library.requiredInt("id", payload.bodySnippet).toString()),
                sourceId = sourceId,
                name = library.requiredNonBlankString("name", payload.bodySnippet),
            )
        }
    }

    suspend fun listSeries(libraryId: RemoteLibraryId): List<RemoteSeries> {
        val series = postPagedJsonArray(
            route = "/api/Series/all-v2",
            body = libraryFilterBody(libraryId),
        )

        return series.map { seriesObject ->
            RemoteSeries(
                id = RemoteSeriesId(seriesObject.requiredInt("id", null).toString()),
                sourceId = sourceId,
                libraryId = libraryId,
                title = seriesObject.requiredTitle(null),
            )
        }
    }

    suspend fun listBooks(seriesId: RemoteSeriesId): List<RemoteBook> {
        val payload = getJson("/api/Series/volumes") {
            parameter("seriesId", seriesId.value)
        }

        return payload.arrayObjects()
            .flatMap { volume -> volume.objectArray("chapters", payload.bodySnippet) }
            .map { chapter -> chapter.toRemoteBook(seriesId, payload.bodySnippet) }
    }

    suspend fun getBook(bookId: RemoteBookId): RemoteBookDetails {
        val chapterPayload = getJson("/api/Series/chapter") {
            parameter("chapterId", bookId.value)
        }
        val chapter = chapterPayload.objectValue()

        val infoPayload = getJson("/api/Book/${bookId.value}/book-info")
        val bookInfo = infoPayload.objectValue()
        val files = chapter.files(chapterPayload.bodySnippet)
        val formats = files.mapTo(mutableSetOf()) { it.format }
            .plus(bookInfo.mangaFormat("seriesFormat"))
            .filterTo(mutableSetOf()) { it != PublicationFormat.UNKNOWN }

        return RemoteBookDetails(
            id = bookId,
            sourceId = sourceId,
            title = firstNonBlank(
                bookInfo.stringOrNull("bookTitle"),
                bookInfo.stringOrNull("chapterTitle"),
                chapter.chapterTitle(chapterPayload.bodySnippet),
            ) ?: unexpected(infoPayload.bodySnippet),
            authors = chapter.personNames("writers", chapterPayload.bodySnippet),
            libraryId = bookInfo.intOrNull("libraryId")?.toString()?.let(::RemoteLibraryId),
            seriesId = bookInfo.intOrNull("seriesId")?.toString()?.let(::RemoteSeriesId),
            formats = formats,
            files = files,
            summary = chapter.stringOrNull("summary"),
            publishedYear = chapter.yearOrNull("releaseDate"),
        )
    }

    suspend fun getCover(bookId: RemoteBookId): ByteArray? {
        return getBytes("/api/Image/chapter-cover") {
            parameter("chapterId", bookId.value)
        }
    }

    override fun toString(): String {
        return "KavitaBrowsingClient(sourceId=$sourceId, baseUrl=$baseUrl, apiKey=$KavitaRedactedSecret)"
    }

    private suspend fun postPagedJsonArray(
        route: String,
        body: JsonObject,
    ): List<JsonObject> {
        val allItems = mutableListOf<JsonObject>()
        var pageNumber = 1

        do {
            val payload = postJson(route, body) {
                parameter("PageNumber", pageNumber)
                parameter("PageSize", pageSize)
            }
            val pageItems = payload.arrayObjects()
            allItems += pageItems

            val totalPages = payload.totalPages()
            pageNumber += 1
        } while (totalPages != null && pageNumber <= totalPages && pageItems.isNotEmpty())

        return allItems
    }

    private suspend fun getJson(
        route: String,
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): KavitaJsonPayload {
        return requestJson {
            httpClient.get("${baseUrl.value}$route") {
                header(KavitaApiKeyHeader, apiKey)
                accept(ContentType.Application.Json)
                configure()
            }
        }
    }

    private suspend fun postJson(
        route: String,
        body: JsonObject,
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): KavitaJsonPayload {
        return requestJson {
            httpClient.post("${baseUrl.value}$route") {
                header(KavitaApiKeyHeader, apiKey)
                accept(ContentType.Application.Json)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(body.toString())
                configure()
            }
        }
    }

    private suspend fun getBytes(
        route: String,
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): ByteArray? {
        return try {
            val response = httpClient.get("${baseUrl.value}$route") {
                header(KavitaApiKeyHeader, apiKey)
                configure()
            }

            when (response.status) {
                HttpStatusCode.OK -> response.body<ByteArray>()
                HttpStatusCode.NotFound,
                HttpStatusCode.NoContent,
                -> null

                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                -> throw SourceAdapterException(SourceError.Unauthorized)

                else -> throw SourceAdapterException(
                    SourceError.UnexpectedResponse(
                        status = response.status.value,
                        bodySnippet = response.redactedBodySnippet(apiKey),
                    ),
                )
            }
        } catch (error: IOException) {
            throw SourceAdapterException(SourceError.NetworkUnavailable)
        } catch (error: CancellationException) {
            throw error
        }
    }

    private suspend fun requestJson(request: suspend () -> HttpResponse): KavitaJsonPayload {
        return try {
            val response = request()
            response.toJsonPayload()
        } catch (error: IOException) {
            throw SourceAdapterException(SourceError.NetworkUnavailable)
        } catch (error: CancellationException) {
            throw error
        }
    }

    private suspend fun HttpResponse.toJsonPayload(): KavitaJsonPayload {
        return when (status) {
            HttpStatusCode.OK -> {
                val body = bodyAsText()
                val bodySnippet = body.toRedactedBodySnippet(apiKey)
                val element = runCatching { Json.parseToJsonElement(body) }
                    .getOrElse { unexpected(bodySnippet) }

                KavitaJsonPayload(
                    element = element,
                    bodySnippet = bodySnippet,
                    headers = headers,
                )
            }

            HttpStatusCode.Unauthorized,
            HttpStatusCode.Forbidden,
            -> throw SourceAdapterException(SourceError.Unauthorized)

            else -> throw SourceAdapterException(
                SourceError.UnexpectedResponse(
                    status = status.value,
                    bodySnippet = redactedBodySnippet(apiKey),
                ),
            )
        }
    }

    private fun libraryFilterBody(libraryId: RemoteLibraryId): JsonObject {
        return buildJsonObject {
            putJsonArray("statements") {
                addJsonObject {
                    put("comparison", KavitaFilterComparisonEqual)
                    put("field", KavitaSeriesFilterFieldLibraries)
                    put("value", libraryId.value)
                }
            }
            put("combination", KavitaFilterCombinationAnd)
            put("limitTo", 0)
        }
    }

    private fun JsonObject.toRemoteBook(
        seriesId: RemoteSeriesId,
        bodySnippet: String?,
    ): RemoteBook {
        val files = files(bodySnippet)
        val formats = files.mapTo(mutableSetOf()) { it.format }
            .filterTo(mutableSetOf()) { it != PublicationFormat.UNKNOWN }

        return RemoteBook(
            id = RemoteBookId(requiredInt("id", bodySnippet).toString()),
            sourceId = sourceId,
            title = chapterTitle(bodySnippet) ?: unexpected(bodySnippet),
            authors = personNames("writers", bodySnippet),
            seriesId = seriesId,
            formats = formats,
        )
    }

    private fun JsonObject.files(bodySnippet: String?): List<RemotePublicationFile> {
        return objectArray("files", bodySnippet).map { file ->
            val filePath = file.stringOrNull("filePath")
            val extension = file.stringOrNull("extension") ?: filePath?.extensionOrNull()
            val sizeBytes = file.longOrNull("bytes")

            RemotePublicationFile(
                id = RemoteFileId(file.requiredInt("id", bodySnippet).toString()),
                format = kavitaFormatToPublicationFormat(file.intOrNull("format"), extension),
                fileName = filePath?.fileNameOrNull(),
                sizeBytes = sizeBytes,
            )
        }
    }

    private fun JsonObject.chapterTitle(bodySnippet: String?): String? {
        return firstNonBlank(
            stringOrNull("titleName"),
            stringOrNull("title"),
            stringOrNull("chapterTitle"),
            files(bodySnippet).firstNotNullOfOrNull { it.fileName?.withoutExtensionOrNull() },
        )
    }

    private fun JsonObject.requiredTitle(bodySnippet: String?): String {
        return firstNonBlank(
            stringOrNull("localizedName"),
            stringOrNull("name"),
            stringOrNull("originalName"),
            stringOrNull("sortName"),
        ) ?: unexpected(bodySnippet)
    }

    private fun JsonObject.personNames(
        field: String,
        bodySnippet: String?,
    ): List<String> {
        return objectArray(field, bodySnippet)
            .mapNotNull { person -> person.stringOrNull("name") }
            .distinct()
    }

    private fun JsonObject.objectArray(
        field: String,
        bodySnippet: String?,
    ): List<JsonObject> {
        return when (val value = this[field]) {
            null,
            JsonNull,
            -> emptyList()

            is JsonArray -> value.map { element ->
                element as? JsonObject ?: unexpected(bodySnippet)
            }

            else -> unexpected(bodySnippet)
        }
    }

    private fun JsonObject.requiredNonBlankString(
        field: String,
        bodySnippet: String?,
    ): String {
        return stringOrNull(field) ?: unexpected(bodySnippet)
    }

    private fun JsonObject.stringOrNull(field: String): String? {
        return (this[field] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.requiredInt(
        field: String,
        bodySnippet: String?,
    ): Int {
        return intOrNull(field) ?: unexpected(bodySnippet)
    }

    private fun JsonObject.intOrNull(field: String): Int? {
        val value = (this[field] as? JsonPrimitive)?.intOrNull ?: return null
        return value.takeIf { it > 0 }
    }

    private fun JsonObject.longOrNull(field: String): Long? {
        val value = (this[field] as? JsonPrimitive)?.longOrNull ?: return null
        if (value < 0) unexpected(null)
        return value
    }

    private fun JsonObject.mangaFormat(field: String): PublicationFormat {
        return kavitaFormatToPublicationFormat(intOrNull(field), extension = null)
    }

    private fun JsonObject.yearOrNull(field: String): Int? {
        val value = stringOrNull(field) ?: return null
        val year = value.take(4).toIntOrNull() ?: return null
        return year.takeIf { it > 0 }
    }

    private fun KavitaJsonPayload.arrayObjects(): List<JsonObject> {
        val array = element as? JsonArray ?: unexpected(bodySnippet)
        return array.map { item -> item as? JsonObject ?: unexpected(bodySnippet) }
    }

    private fun KavitaJsonPayload.objectValue(): JsonObject {
        return element as? JsonObject ?: unexpected(bodySnippet)
    }

    private fun KavitaJsonPayload.totalPages(): Int? {
        val pagination = headers["Pagination"] ?: return null
        val metadata = runCatching { Json.parseToJsonElement(pagination) as? JsonObject }.getOrNull()
            ?: return null
        return (metadata["totalPages"] as? JsonPrimitive)?.intOrNull
    }

    private fun unexpected(bodySnippet: String?): Nothing {
        throw SourceAdapterException(
            SourceError.UnexpectedResponse(
                status = HttpStatusCode.OK.value,
                bodySnippet = bodySnippet,
            ),
        )
    }

    private companion object {
        const val KavitaFilterComparisonEqual = 0
        const val KavitaFilterCombinationAnd = 1
        const val KavitaSeriesFilterFieldLibraries = 19
    }
}

private data class KavitaJsonPayload(
    val element: JsonElement,
    val bodySnippet: String?,
    val headers: Headers,
)

private fun kavitaFormatToPublicationFormat(
    format: Int?,
    extension: String?,
): PublicationFormat {
    return extension?.trimStart('.')?.lowercase()?.let { normalizedExtension ->
        when (normalizedExtension) {
            "epub" -> PublicationFormat.EPUB
            "pdf" -> PublicationFormat.PDF
            "cbz" -> PublicationFormat.CBZ
            "cbr" -> PublicationFormat.CBR
            "mobi" -> PublicationFormat.MOBI
            "azw3" -> PublicationFormat.AZW3
            else -> null
        }
    } ?: when (format) {
        3 -> PublicationFormat.EPUB
        4 -> PublicationFormat.PDF
        else -> PublicationFormat.UNKNOWN
    }
}

private fun String.fileNameOrNull(): String? {
    return replace('\\', '/')
        .substringAfterLast('/')
        .takeIf { it.isNotBlank() }
}

private fun String.extensionOrNull(): String? {
    return fileNameOrNull()
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
}

private fun String.withoutExtensionOrNull(): String? {
    return substringBeforeLast('.', missingDelimiterValue = this)
        .trim()
        .takeIf { it.isNotBlank() }
}

private fun firstNonBlank(vararg values: String?): String? {
    return values.firstOrNull { !it.isNullOrBlank() }?.trim()
}
