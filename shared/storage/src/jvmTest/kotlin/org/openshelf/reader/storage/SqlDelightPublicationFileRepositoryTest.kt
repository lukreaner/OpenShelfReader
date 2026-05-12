package org.openshelf.reader.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.source.api.RemoteBookId
import org.openshelf.reader.source.api.RemoteFileId
import org.openshelf.reader.source.api.SourceId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SqlDelightPublicationFileRepositoryTest {
    private lateinit var driver: SqlDriver
    private lateinit var repository: PublicationFileRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OpenShelfDatabase.Schema.create(driver)
        repository = SqlDelightPublicationFileRepository(OpenShelfDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun insertsAndReadsCachedPublicationMetadata() = runTest {
        val identity = sampleIdentity()
        val file = sampleFile()

        repository.upsertCachedPublication(identity, file)

        assertEquals(identity, repository.getBookIdentity(identity.id))
        assertEquals(file, repository.getPublicationFile(file.id))
        assertEquals(
            file,
            repository.findPublicationFile(
                sourceId = file.sourceId,
                remoteFileId = file.remoteFileId,
                format = file.format,
            ),
        )
    }

    @Test
    fun updatesPublicationFileAndPreservesLastOpenedWhenReplacementOmitsIt() = runTest {
        val identity = sampleIdentity()
        val openedFile = sampleFile(lastOpenedAtEpochMillis = 2_000L)
        val replacement = openedFile.copy(
            localPath = "publication-cache/source/book/file-v2.epub",
            fileSize = 456L,
            downloadedAtEpochMillis = 3_000L,
            lastOpenedAtEpochMillis = null,
        )

        repository.upsertCachedPublication(identity, openedFile)
        repository.upsertCachedPublication(identity.copy(updatedAtEpochMillis = 3_000L), replacement)

        assertEquals(
            replacement.copy(lastOpenedAtEpochMillis = 2_000L),
            repository.getPublicationFile(openedFile.id),
        )
        assertEquals(
            identity.copy(updatedAtEpochMillis = 3_000L),
            repository.getBookIdentity(identity.id),
        )
    }

    @Test
    fun cacheLookupIncludesFormat() = runTest {
        val identity = sampleIdentity()
        val epub = sampleFile(format = PublicationFormat.EPUB)

        repository.upsertCachedPublication(identity, epub)

        assertEquals(
            epub,
            repository.findPublicationFile(SourceId("source-1"), RemoteFileId("file-1"), PublicationFormat.EPUB),
        )
        assertNull(
            repository.findPublicationFile(SourceId("source-1"), RemoteFileId("file-1"), PublicationFormat.PDF),
        )
    }

    @Test
    fun validationRejectsInvalidPublicationMetadata() {
        assertFailsWith<IllegalArgumentException> { PublicationFileId(" ") }
        assertFailsWith<IllegalArgumentException> { sampleFile(localPath = " ") }
        assertFailsWith<IllegalArgumentException> { sampleFile(fileSize = -1L) }
        assertFailsWith<IllegalArgumentException> { sampleFile(downloadedAtEpochMillis = -1L) }
        assertFailsWith<IllegalArgumentException> { sampleFile(lastOpenedAtEpochMillis = -1L) }
        assertFailsWith<IllegalArgumentException> { sampleIdentity(fileSize = -1L) }
        assertFailsWith<IllegalArgumentException> { sampleIdentity(createdAtEpochMillis = -1L) }
    }

    private fun sampleIdentity(
        id: BookIdentityId = BookIdentityId("book-identity-1"),
        sourceId: SourceId = SourceId("source-1"),
        remoteBookId: RemoteBookId? = RemoteBookId("book-1"),
        remoteFileId: RemoteFileId? = RemoteFileId("file-1"),
        fileSize: Long? = 123L,
        createdAtEpochMillis: Long = 1_000L,
        updatedAtEpochMillis: Long = 1_000L,
    ): LocalBookIdentity =
        LocalBookIdentity(
            id = id,
            sourceId = sourceId,
            remoteBookId = remoteBookId,
            remoteFileId = remoteFileId,
            fileSize = fileSize,
            titleNormalized = "sample book",
            authorNormalized = "sample author",
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )

    private fun sampleFile(
        id: PublicationFileId = PublicationFileId("publication-file-1"),
        bookIdentityId: BookIdentityId = BookIdentityId("book-identity-1"),
        sourceId: SourceId = SourceId("source-1"),
        remoteFileId: RemoteFileId = RemoteFileId("file-1"),
        localPath: String = "publication-cache/source/book/file.epub",
        format: PublicationFormat = PublicationFormat.EPUB,
        fileSize: Long? = 123L,
        downloadedAtEpochMillis: Long = 1_000L,
        lastOpenedAtEpochMillis: Long? = null,
    ): LocalPublicationFile =
        LocalPublicationFile(
            id = id,
            bookIdentityId = bookIdentityId,
            sourceId = sourceId,
            remoteFileId = remoteFileId,
            localPath = localPath,
            format = format,
            fileSize = fileSize,
            downloadedAtEpochMillis = downloadedAtEpochMillis,
            lastOpenedAtEpochMillis = lastOpenedAtEpochMillis,
        )
}
