package org.openshelf.reader.storage

import app.cash.sqldelight.db.SqlDriver
import org.openshelf.reader.source.api.SourceId
import org.openshelf.reader.source.api.SourceType

class SqlDelightSourceAccountRepository(
    private val database: OpenShelfDatabase,
) : SourceAccountRepository {
    constructor(driver: SqlDriver) : this(OpenShelfDatabase(driver))

    override suspend fun upsert(account: SavedSourceAccount) {
        val existing = getById(account.id)
        val accountToStore = existing?.let {
            account.copy(createdAtEpochMillis = it.createdAtEpochMillis)
        } ?: account

        if (existing == null) {
            insert(accountToStore)
        } else {
            update(accountToStore)
        }
    }

    override suspend fun getById(id: SourceId): SavedSourceAccount? =
        database.storageQueries
            .selectSourceAccountById(id.value, ::mapSourceAccount)
            .executeAsOneOrNull()

    override suspend fun getLatestByType(type: SourceType): SavedSourceAccount? =
        database.storageQueries
            .selectLatestSourceAccountByType(type.toStorageValue(), ::mapSourceAccount)
            .executeAsOneOrNull()

    override suspend fun delete(id: SourceId) {
        database.storageQueries.deleteSourceAccountById(id.value)
    }

    private fun insert(account: SavedSourceAccount) {
        database.storageQueries.insertSourceAccount(
            id = account.id.value,
            type = account.type.toStorageValue(),
            display_name = account.displayName,
            base_url = account.baseUrl,
            allow_insecure_http = account.allowInsecureHttp.toStorageValue(),
            created_at = account.createdAtEpochMillis,
            updated_at = account.updatedAtEpochMillis,
        )
    }

    private fun update(account: SavedSourceAccount) {
        database.storageQueries.updateSourceAccount(
            type = account.type.toStorageValue(),
            display_name = account.displayName,
            base_url = account.baseUrl,
            allow_insecure_http = account.allowInsecureHttp.toStorageValue(),
            updated_at = account.updatedAtEpochMillis,
            id = account.id.value,
        )
    }

    private fun mapSourceAccount(
        id: String,
        type: String,
        displayName: String,
        baseUrl: String,
        allowInsecureHttp: Long,
        createdAt: Long,
        updatedAt: Long,
    ): SavedSourceAccount =
        SavedSourceAccount(
            id = SourceId(id),
            type = type.toSourceType(),
            displayName = displayName,
            baseUrl = baseUrl,
            allowInsecureHttp = allowInsecureHttp != 0L,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = updatedAt,
        )
}

private fun SourceType.toStorageValue(): String = name

private fun String.toSourceType(): SourceType =
    SourceType.entries.firstOrNull { it.name == uppercase() } ?: SourceType.KAVITA

private fun Boolean.toStorageValue(): Long = if (this) 1L else 0L
