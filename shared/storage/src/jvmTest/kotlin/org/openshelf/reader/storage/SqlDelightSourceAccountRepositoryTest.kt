package org.openshelf.reader.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import org.openshelf.reader.source.api.SourceId
import org.openshelf.reader.source.api.SourceType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightSourceAccountRepositoryTest {
    private lateinit var driver: SqlDriver
    private lateinit var repository: SourceAccountRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OpenShelfDatabase.Schema.create(driver)
        repository = SqlDelightSourceAccountRepository(OpenShelfDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun upsertsAndReadsSavedSourceAccountWithoutApiKeyField() = runTest {
        val account = sampleAccount()

        repository.upsert(account)

        assertEquals(account, repository.getById(account.id))
        assertEquals(account, repository.getLatestByType(SourceType.KAVITA))
    }

    @Test
    fun updatePreservesCreatedTimestampAndPersistsHttpOptIn() = runTest {
        val account = sampleAccount(allowInsecureHttp = false)
        val updated = account.copy(
            baseUrl = "http://library.local",
            allowInsecureHttp = true,
            createdAtEpochMillis = 99L,
            updatedAtEpochMillis = 2_000L,
        )

        repository.upsert(account)
        repository.upsert(updated)

        val stored = repository.getById(account.id)
        assertEquals(1_000L, stored?.createdAtEpochMillis)
        assertEquals(2_000L, stored?.updatedAtEpochMillis)
        assertEquals("http://library.local", stored?.baseUrl)
        assertTrue(stored?.allowInsecureHttp == true)
    }

    @Test
    fun deleteRemovesSavedAccountMetadata() = runTest {
        val account = sampleAccount()
        repository.upsert(account)

        repository.delete(account.id)

        assertNull(repository.getById(account.id))
        assertNull(repository.getLatestByType(SourceType.KAVITA))
    }

    @Test
    fun validationRejectsInvalidAccountMetadata() {
        assertFailsWith<IllegalArgumentException> { sampleAccount(displayName = " ") }
        assertFailsWith<IllegalArgumentException> { sampleAccount(baseUrl = " ") }
        assertFailsWith<IllegalArgumentException> { sampleAccount(createdAtEpochMillis = -1L) }
        assertFailsWith<IllegalArgumentException> { sampleAccount(updatedAtEpochMillis = -1L) }
        assertFalse(sampleAccount().allowInsecureHttp)
    }

    private fun sampleAccount(
        id: SourceId = SourceId("kavita-alpha"),
        type: SourceType = SourceType.KAVITA,
        displayName: String = "Kavita (library.example)",
        baseUrl: String = "https://library.example",
        allowInsecureHttp: Boolean = false,
        createdAtEpochMillis: Long = 1_000L,
        updatedAtEpochMillis: Long = 1_000L,
    ): SavedSourceAccount =
        SavedSourceAccount(
            id = id,
            type = type,
            displayName = displayName,
            baseUrl = baseUrl,
            allowInsecureHttp = allowInsecureHttp,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
}
