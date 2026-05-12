package org.openshelf.reader.download

import kotlinx.coroutines.test.runTest
import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.source.api.AuthResult
import org.openshelf.reader.source.api.DownloadResult
import org.openshelf.reader.source.api.DownloadSink
import org.openshelf.reader.source.api.ProgressWriteResult
import org.openshelf.reader.source.api.RemoteBook
import org.openshelf.reader.source.api.RemoteBookDetails
import org.openshelf.reader.source.api.RemoteBookId
import org.openshelf.reader.source.api.RemoteDownloadRequest
import org.openshelf.reader.source.api.RemoteFileId
import org.openshelf.reader.source.api.RemoteLibrary
import org.openshelf.reader.source.api.RemoteLibraryId
import org.openshelf.reader.source.api.RemoteProgress
import org.openshelf.reader.source.api.RemotePublicationFile
import org.openshelf.reader.source.api.RemoteSearchResult
import org.openshelf.reader.source.api.RemoteSeries
import org.openshelf.reader.source.api.RemoteSeriesId
import org.openshelf.reader.source.api.SourceAdapter
import org.openshelf.reader.source.api.SourceCapabilities
import org.openshelf.reader.source.api.SourceCredentials
import org.openshelf.reader.source.api.SourceError
import org.openshelf.reader.source.api.SourceId
import org.openshelf.reader.source.api.SourceType
import org.openshelf.reader.storage.BookIdentityId
import org.openshelf.reader.storage.LocalBookIdentity
import org.openshelf.reader.storage.LocalPublicationFile
import org.openshelf.reader.storage.PublicationFileId
import org.openshelf.reader.storage.PublicationFileRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DownloadPublicationFileUseCaseTest {
    @Test
    fun cacheHitAvoidsNetworkDownload() = runTest {
        val repository = FakePublicationFileRepository()
        val fileStore = InMemoryPublicationFileStore()
        val cached = sampleLocalFile(fileSize = 5L)
        repository.files[cached.id] = cached
        fileStore.files[cached.localPath] = byteArrayOf(1, 2, 3, 4, 5)
        val adapter = FakeSourceAdapter()
        val progress = mutableListOf<DownloadProgress>()

        val result = useCase(repository, fileStore).download(
            sourceAdapter = adapter,
            book = sampleBook(),
            file = sampleRemoteFile(sizeBytes = 5L),
            onProgress = progress::add,
        )

        assertEquals(0, adapter.downloadCalls)
        assertEquals(DownloadPublicationFileResult.Cached(cached), result)
        assertEquals(listOf(DownloadProgress.CheckingCache, DownloadProgress.Cached(cached)), progress)
    }

    @Test
    fun missingCacheDownloadsAndUpsertsMetadata() = runTest {
        val repository = FakePublicationFileRepository()
        val fileStore = InMemoryPublicationFileStore()
        val adapter = FakeSourceAdapter(bytes = byteArrayOf(1, 2, 3, 4, 5))
        val progress = mutableListOf<DownloadProgress>()

        val result = useCase(repository, fileStore).download(
            sourceAdapter = adapter,
            book = sampleBook(),
            file = sampleRemoteFile(sizeBytes = 5L),
            onProgress = progress::add,
        )

        val downloaded = assertIs<DownloadPublicationFileResult.Downloaded>(result).file
        assertEquals(1, adapter.downloadCalls)
        assertEquals(RemoteDownloadRequest(sampleBook().id, RemoteFileId("file-1"), PublicationFormat.EPUB, "book.epub", 5L), adapter.lastRequest)
        assertEquals(downloaded, repository.files[downloaded.id])
        assertEquals(byteArrayOf(1, 2, 3, 4, 5).toList(), fileStore.files.getValue(downloaded.localPath).toList())
        assertTrue(progress.first() is DownloadProgress.CheckingCache)
        assertTrue(progress.any { it is DownloadProgress.Downloading && it.bytesWritten == 5L })
        assertEquals(DownloadProgress.Downloaded(downloaded), progress.last())
    }

    @Test
    fun unsupportedFormatFailsBeforeNetwork() = runTest {
        val repository = FakePublicationFileRepository()
        val fileStore = InMemoryPublicationFileStore()
        val adapter = FakeSourceAdapter()

        val result = useCase(repository, fileStore).download(
            sourceAdapter = adapter,
            book = sampleBook(),
            file = sampleRemoteFile(format = PublicationFormat.MOBI),
        )

        val failure = assertIs<DownloadPublicationFileResult.Failure>(result)
        assertEquals(DownloadPublicationFileError.UnsupportedFormat(PublicationFormat.MOBI), failure.error)
        assertEquals(0, adapter.downloadCalls)
        assertTrue(repository.files.isEmpty())
        assertTrue(fileStore.files.isEmpty())
    }

    @Test
    fun sizeMismatchLeavesExistingCacheUntouched() = runTest {
        val repository = FakePublicationFileRepository()
        val fileStore = InMemoryPublicationFileStore()
        val existing = sampleLocalFile(fileSize = 4L)
        repository.files[existing.id] = existing
        fileStore.files[existing.localPath] = byteArrayOf(9, 9, 9, 9)
        val adapter = FakeSourceAdapter(bytes = byteArrayOf(1, 2))

        val result = useCase(repository, fileStore).download(
            sourceAdapter = adapter,
            book = sampleBook(),
            file = sampleRemoteFile(sizeBytes = 5L),
        )

        val failure = assertIs<DownloadPublicationFileResult.Failure>(result)
        assertEquals(DownloadPublicationFileError.SizeMismatch(5L, 2L), failure.error)
        assertEquals(existing, repository.files[existing.id])
        assertEquals(byteArrayOf(9, 9, 9, 9).toList(), fileStore.files.getValue(existing.localPath).toList())
    }

    @Test
    fun sourceFailureLeavesExistingCacheUntouched() = runTest {
        val repository = FakePublicationFileRepository()
        val fileStore = InMemoryPublicationFileStore()
        val existing = sampleLocalFile(fileSize = 4L)
        repository.files[existing.id] = existing
        fileStore.files[existing.localPath] = byteArrayOf(9, 9, 9, 9)
        val adapter = FakeSourceAdapter(result = DownloadResult.Failure(SourceError.NetworkUnavailable))

        val result = useCase(repository, fileStore).download(
            sourceAdapter = adapter,
            book = sampleBook(),
            file = sampleRemoteFile(sizeBytes = 5L),
        )

        val failure = assertIs<DownloadPublicationFileResult.Failure>(result)
        assertEquals(DownloadPublicationFileError.SourceFailure(SourceError.NetworkUnavailable), failure.error)
        assertEquals(existing, repository.files[existing.id])
        assertEquals(byteArrayOf(9, 9, 9, 9).toList(), fileStore.files.getValue(existing.localPath).toList())
    }

    private fun useCase(
        repository: FakePublicationFileRepository,
        fileStore: InMemoryPublicationFileStore,
    ): DownloadPublicationFileUseCase =
        DownloadPublicationFileUseCase(
            repository = repository,
            fileStore = fileStore,
            clock = EpochMillisProvider { 1_700_000_000_000L },
        )

    private fun sampleBook(): RemoteBookDetails =
        RemoteBookDetails(
            id = RemoteBookId("book-1"),
            sourceId = SourceId("source-1"),
            title = "Sample Book",
            authors = listOf("Sample Author"),
            formats = setOf(PublicationFormat.EPUB),
            files = listOf(sampleRemoteFile()),
        )

    private fun sampleRemoteFile(
        format: PublicationFormat = PublicationFormat.EPUB,
        sizeBytes: Long? = 5L,
    ): RemotePublicationFile =
        RemotePublicationFile(
            id = RemoteFileId("file-1"),
            format = format,
            fileName = "book.${format.name.lowercase()}",
            sizeBytes = sizeBytes,
        )

    private fun sampleLocalFile(fileSize: Long?): LocalPublicationFile =
        LocalPublicationFile(
            id = PublicationFileId("publication-file:source-1:file-1:EPUB"),
            bookIdentityId = BookIdentityId("book:source-1:book-1:file-1"),
            sourceId = SourceId("source-1"),
            remoteFileId = RemoteFileId("file-1"),
            localPath = "publication-cache/source-1/book_source-1_book-1_file-1/publication-file_source-1_file-1_EPUB.epub",
            format = PublicationFormat.EPUB,
            fileSize = fileSize,
            downloadedAtEpochMillis = 1_000L,
        )
}

private class FakeSourceAdapter(
    private val bytes: ByteArray = byteArrayOf(1, 2, 3, 4, 5),
    private val result: DownloadResult? = null,
) : SourceAdapter {
    var downloadCalls: Int = 0
        private set
    var lastRequest: RemoteDownloadRequest? = null
        private set

    override val sourceId: SourceId = SourceId("source-1")
    override val sourceType: SourceType = SourceType.KAVITA
    override val capabilities: SourceCapabilities = SourceCapabilities(supportsDownloads = true)

    override suspend fun downloadFile(
        request: RemoteDownloadRequest,
        sink: DownloadSink,
    ): DownloadResult {
        downloadCalls += 1
        lastRequest = request
        val configuredResult = result
        if (configuredResult != null) return configuredResult

        sink.write(bytes)
        return DownloadResult.Success(
            fileId = request.fileId,
            format = request.expectedFormat,
            bytesWritten = bytes.size.toLong(),
            fileName = request.expectedFileName,
        )
    }

    override suspend fun authenticate(credentials: SourceCredentials): AuthResult = unused()
    override suspend fun refreshCapabilities(): SourceCapabilities = capabilities
    override suspend fun listLibraries(): List<RemoteLibrary> = unused()
    override suspend fun listSeries(libraryId: RemoteLibraryId): List<RemoteSeries> = unused()
    override suspend fun listBooks(seriesId: RemoteSeriesId): List<RemoteBook> = unused()
    override suspend fun search(query: String): List<RemoteSearchResult> = unused()
    override suspend fun getBook(bookId: RemoteBookId): RemoteBookDetails = unused()
    override suspend fun getCover(bookId: RemoteBookId): ByteArray? = unused()
    override suspend fun getRemoteProgress(bookId: RemoteBookId): RemoteProgress? = unused()
    override suspend fun setRemoteProgress(bookId: RemoteBookId, progress: RemoteProgress): ProgressWriteResult = unused()

    private fun unused(): Nothing = error("Unused in this test.")
}

private class FakePublicationFileRepository : PublicationFileRepository {
    val identities = mutableMapOf<BookIdentityId, LocalBookIdentity>()
    val files = mutableMapOf<PublicationFileId, LocalPublicationFile>()

    override suspend fun upsertBookIdentity(identity: LocalBookIdentity) {
        identities[identity.id] = identity
    }

    override suspend fun getBookIdentity(id: BookIdentityId): LocalBookIdentity? = identities[id]

    override suspend fun upsertPublicationFile(file: LocalPublicationFile) {
        files[file.id] = file
    }

    override suspend fun upsertCachedPublication(
        identity: LocalBookIdentity,
        file: LocalPublicationFile,
    ) {
        identities[identity.id] = identity
        files[file.id] = file
    }

    override suspend fun getPublicationFile(id: PublicationFileId): LocalPublicationFile? = files[id]

    override suspend fun findPublicationFile(
        sourceId: SourceId,
        remoteFileId: RemoteFileId,
        format: PublicationFormat,
    ): LocalPublicationFile? =
        files.values.firstOrNull {
            it.sourceId == sourceId && it.remoteFileId == remoteFileId && it.format == format
        }
}

private class InMemoryPublicationFileStore : PublicationFileStore {
    val files = mutableMapOf<String, ByteArray>()

    override suspend fun exists(relativePath: String): Boolean = files.containsKey(relativePath)

    override suspend fun sizeBytes(relativePath: String): Long? = files[relativePath]?.size?.toLong()

    override suspend fun delete(relativePath: String) {
        files.remove(relativePath)
    }

    override suspend fun writeAtomically(
        relativePath: String,
        expectedSizeBytes: Long?,
        onBytesWritten: (Long) -> Unit,
        downloader: suspend (DownloadSink) -> DownloadResult,
    ): FileStoreWriteResult {
        val temp = mutableListOf<Byte>()
        val result = downloader { chunk ->
            temp += chunk.toList()
            onBytesWritten(temp.size.toLong())
        }

        return when (result) {
            is DownloadResult.Failure -> FileStoreWriteResult.DownloadFailure(result.error)
            is DownloadResult.Success -> {
                if (expectedSizeBytes != null && expectedSizeBytes != temp.size.toLong()) {
                    FileStoreWriteResult.SizeMismatch(
                        expectedBytes = expectedSizeBytes,
                        actualBytes = temp.size.toLong(),
                    )
                } else {
                    files[relativePath] = temp.toByteArray()
                    FileStoreWriteResult.Success(
                        bytesWritten = temp.size.toLong(),
                        download = result,
                    )
                }
            }
        }
    }
}
