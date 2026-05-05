package org.openshelf.reader.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import org.openshelf.reader.core.PublicationFormat
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlDelightReadingPositionRepositoryTest {
    private lateinit var driver: SqlDriver
    private lateinit var database: OpenShelfDatabase
    private lateinit var repository: ReadingPositionRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OpenShelfDatabase.Schema.create(driver)
        database = OpenShelfDatabase(driver)
        repository = SqlDelightReadingPositionRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun createsDatabase() {
        assertEquals(0L, database.storageQueries.countReadingPositions().executeAsOne())
    }

    @Test
    fun insertsAndReadsReadingPosition() = runTest {
        val position = samplePosition()

        repository.upsert(position)

        assertEquals(position, repository.getById(position.id))
    }

    @Test
    fun upsertsNewerPositionForSameId() = runTest {
        val original = samplePosition(progression = 0.25, updatedAtEpochMillis = 1_000L)
        val newer = original.copy(
            locatorJson = """{"href":"chapter-2.xhtml"}""",
            progression = 0.75,
            chapterHref = "chapter-2.xhtml",
            chapterIndex = 1,
            chapterProgression = 0.15,
            updatedAtEpochMillis = 2_000L,
            finished = true,
        )

        repository.upsert(original)
        repository.upsert(newer)

        assertEquals(newer, repository.getById(original.id))
    }

    @Test
    fun getsLatestReadingPositionForBookByUpdatedTimestamp() = runTest {
        val bookIdentityId = BookIdentityId("book-1")
        val older = samplePosition(
            id = ReadingPositionId("position-older"),
            bookIdentityId = bookIdentityId,
            progression = 0.2,
            updatedAtEpochMillis = 1_000L,
        )
        val newer = samplePosition(
            id = ReadingPositionId("position-newer"),
            bookIdentityId = bookIdentityId,
            progression = 0.8,
            updatedAtEpochMillis = 3_000L,
        )
        val otherBook = samplePosition(
            id = ReadingPositionId("position-other-book"),
            bookIdentityId = BookIdentityId("book-2"),
            progression = 0.95,
            updatedAtEpochMillis = 4_000L,
        )

        repository.upsert(older)
        repository.upsert(newer)
        repository.upsert(otherBook)

        assertEquals(newer, repository.getLatestForBook(bookIdentityId))
    }

    @Test
    fun validationRejectsInvalidProgressValues() {
        assertFailsWith<IllegalArgumentException> { samplePosition(progression = -0.01) }
        assertFailsWith<IllegalArgumentException> { samplePosition(progression = 1.01) }
        assertFailsWith<IllegalArgumentException> { samplePosition(chapterProgression = -0.01) }
        assertFailsWith<IllegalArgumentException> { samplePosition(chapterProgression = 1.01) }
        assertFailsWith<IllegalArgumentException> { samplePosition(pdfPageIndex = -1) }
        assertFailsWith<IllegalArgumentException> { samplePosition(pdfPageOffset = -0.01) }
        assertFailsWith<IllegalArgumentException> { samplePosition(pdfPageOffset = 1.01) }
        assertFailsWith<IllegalArgumentException> { samplePosition(updatedAtEpochMillis = -1L) }
        assertFailsWith<IllegalArgumentException> { ReadingPositionId(" ") }
        assertFailsWith<IllegalArgumentException> { BookIdentityId(" ") }
        assertFailsWith<IllegalArgumentException> { DeviceId(" ") }
        assertFailsWith<IllegalArgumentException> { ReadingSessionId(" ") }
    }

    @Test
    fun productionSchemaAndSourceFilesDoNotDeclareSensitiveColumns() {
        val sourceRoot = productionSourceRoot()
        val blockedColumnPattern = Regex(
            pattern = """\b(api_key|auth_key|token|password|secret|credential)\b""",
            option = RegexOption.IGNORE_CASE,
        )
        val matches = mutableListOf<String>()

        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) }
                .filter { path -> path.toString().endsWith(".kt") || path.toString().endsWith(".sq") }
                .forEach { path ->
                    Files.readAllLines(path).forEachIndexed { index, line ->
                        if (blockedColumnPattern.containsMatchIn(line)) {
                            matches += "${sourceRoot.relativize(path)}:${index + 1}:$line"
                        }
                    }
                }
        }

        assertTrue(matches.isEmpty(), matches.joinToString(separator = "\n"))
    }

    private fun samplePosition(
        id: ReadingPositionId = ReadingPositionId("position-1"),
        bookIdentityId: BookIdentityId = BookIdentityId("book-1"),
        format: PublicationFormat = PublicationFormat.EPUB,
        locatorJson: String = """{"href":"chapter-1.xhtml"}""",
        progression: Double? = 0.4,
        chapterHref: String? = "chapter-1.xhtml",
        chapterIndex: Int? = 0,
        chapterProgression: Double? = 0.5,
        pdfPageIndex: Int? = null,
        pdfPageOffset: Double? = null,
        source: ReadingPositionSource = ReadingPositionSource.READER,
        deviceId: DeviceId = DeviceId("device-1"),
        sessionId: ReadingSessionId = ReadingSessionId("session-1"),
        updatedAtEpochMillis: Long = 1_700_000_000_000L,
        finished: Boolean = false,
    ): LocalReadingPosition =
        LocalReadingPosition(
            id = id,
            bookIdentityId = bookIdentityId,
            format = format,
            locatorJson = locatorJson,
            progression = progression,
            chapterHref = chapterHref,
            chapterIndex = chapterIndex,
            chapterProgression = chapterProgression,
            pdfPageIndex = pdfPageIndex,
            pdfPageOffset = pdfPageOffset,
            source = source,
            deviceId = deviceId,
            sessionId = sessionId,
            updatedAtEpochMillis = updatedAtEpochMillis,
            finished = finished,
        )

    private fun productionSourceRoot(): Path {
        val candidates = listOf(
            Path.of("src", "commonMain"),
            Path.of("shared", "storage", "src", "commonMain"),
        )
        return candidates.firstOrNull(Files::exists) ?: error("Could not find storage production source root.")
    }
}
