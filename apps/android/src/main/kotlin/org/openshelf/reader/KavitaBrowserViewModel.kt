package org.openshelf.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openshelf.reader.credentials.AndroidKeystoreApiKeyStore
import org.openshelf.reader.credentials.SecureApiKeyStore
import org.openshelf.reader.download.AndroidPublicationFileStore
import org.openshelf.reader.download.DownloadProgress
import org.openshelf.reader.download.DownloadPublicationFileResult
import org.openshelf.reader.download.DownloadPublicationFileUseCase
import org.openshelf.reader.download.EpochMillisProvider
import org.openshelf.reader.kavita.api.KavitaSourceAdapter
import org.openshelf.reader.source.api.AuthResult
import org.openshelf.reader.source.api.RemoteBook
import org.openshelf.reader.source.api.RemotePublicationFile
import org.openshelf.reader.source.api.RemoteLibrary
import org.openshelf.reader.source.api.RemoteSeries
import org.openshelf.reader.source.api.SourceAdapter
import org.openshelf.reader.source.api.SourceCredentials
import org.openshelf.reader.source.api.SourceError
import org.openshelf.reader.source.api.SourceId
import org.openshelf.reader.source.api.SourceType
import org.openshelf.reader.storage.LocalPublicationFile
import org.openshelf.reader.storage.OpenShelfDatabase
import org.openshelf.reader.storage.SavedSourceAccount
import org.openshelf.reader.storage.SqlDelightPublicationFileRepository
import org.openshelf.reader.storage.SqlDelightSourceAccountRepository

internal class KavitaBrowserViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val httpClient = HttpClient(OkHttp)
    private val databaseDriver = AndroidSqliteDriver(
        schema = OpenShelfDatabase.Schema,
        context = application,
        name = "openshelf-reader.db",
    )
    private val publicationFileRepository = SqlDelightPublicationFileRepository(databaseDriver)
    private val sourceAccountRepository = SqlDelightSourceAccountRepository(databaseDriver)
    private val secureApiKeyStore: SecureApiKeyStore = AndroidKeystoreApiKeyStore(application)
    private val connectionPolicy = KavitaConnectionPolicy()
    private val publicationFileStore = AndroidPublicationFileStore(application)
    private val downloadUseCase = DownloadPublicationFileUseCase(
        repository = publicationFileRepository,
        fileStore = publicationFileStore,
        clock = EpochMillisProvider { System.currentTimeMillis() },
    )
    private val _uiState = MutableStateFlow(KavitaBrowserUiState())
    private var adapter: SourceAdapter? = null
    private var connectedApiKey: String = ""
    private var activeJob: Job? = null
    private val downloadJobs = mutableMapOf<String, Job>()

    val uiState: StateFlow<KavitaBrowserUiState> = _uiState.asStateFlow()

    init {
        restoreSavedConnection()
    }

    fun onServerUrlChanged(value: String) {
        _uiState.update {
            it.copy(
                serverUrl = value,
                connectionError = null,
                connectionMessage = null,
            )
        }
    }

    fun onApiKeyChanged(value: String) {
        _uiState.update {
            it.copy(
                apiKey = value,
                connectionError = null,
                connectionMessage = null,
            )
        }
    }

    fun onAllowInsecureHttpChanged(value: Boolean) {
        _uiState.update {
            it.copy(
                allowInsecureHttp = value,
                connectionError = null,
                connectionMessage = null,
            )
        }
    }

    fun testConnection() {
        authenticate(loadLibraries = false)
    }

    fun connectAndLoadLibraries() {
        authenticate(loadLibraries = true)
    }

    fun forgetSavedConnection() {
        activeJob?.cancel()
        adapter = null
        connectedApiKey = ""

        _uiState.update {
            KavitaBrowserUiState(
                connectionMessage = "Saved Kavita connection removed.",
            )
        }

        activeJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                secureApiKeyStore.delete(SourceId(KavitaAlphaSourceId))
                sourceAccountRepository.delete(SourceId(KavitaAlphaSourceId))
            }
        }
    }

    fun retryCurrentScreen() {
        when (_uiState.value.screen) {
            BrowserScreen.Connection -> connectAndLoadLibraries()
            BrowserScreen.Libraries -> loadLibraries()
            BrowserScreen.Series -> _uiState.value.selectedLibrary?.let(::selectLibrary)
            BrowserScreen.Books -> _uiState.value.selectedSeries?.let(::selectSeries)
            BrowserScreen.BookDetails -> _uiState.value.selectedBook?.let(::selectBook)
        }
    }

    fun selectLibrary(library: RemoteLibrary) {
        val sourceAdapter = adapter ?: run {
            returnToConnection("Connect to Kavita before browsing libraries.")
            return
        }
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                screen = BrowserScreen.Series,
                selectedLibrary = library,
                selectedSeries = null,
                selectedBook = null,
                series = LoadState.Loading,
                books = LoadState.Idle,
                bookDetails = LoadState.Idle,
            )
        }

        activeJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    sourceAdapter.listSeries(library.id)
                }
            }

            result.fold(
                onSuccess = { series -> _uiState.update { it.copy(series = LoadState.Success(series)) } },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(series = LoadState.Error(messageForThrowable(error, connectedApiKey)))
                    }
                },
            )
        }
    }

    fun selectSeries(series: RemoteSeries) {
        val sourceAdapter = adapter ?: run {
            returnToConnection("Connect to Kavita before browsing books.")
            return
        }
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                screen = BrowserScreen.Books,
                selectedSeries = series,
                selectedBook = null,
                books = LoadState.Loading,
                bookDetails = LoadState.Idle,
            )
        }

        activeJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    sourceAdapter.listBooks(series.id)
                }
            }

            result.fold(
                onSuccess = { books -> _uiState.update { it.copy(books = LoadState.Success(books)) } },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(books = LoadState.Error(messageForThrowable(error, connectedApiKey)))
                    }
                },
            )
        }
    }

    fun selectBook(book: RemoteBook) {
        val sourceAdapter = adapter ?: run {
            returnToConnection("Connect to Kavita before viewing book details.")
            return
        }
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                screen = BrowserScreen.BookDetails,
                selectedBook = book,
                bookDetails = LoadState.Loading,
                downloadStates = emptyMap(),
            )
        }

        activeJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    sourceAdapter.getBook(book.id)
                }
            }

            result.fold(
                onSuccess = { details ->
                    _uiState.update { it.copy(bookDetails = LoadState.Success(details)) }
                    markCachedFiles(sourceAdapter, details.files)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(bookDetails = LoadState.Error(messageForThrowable(error, connectedApiKey)))
                    }
                },
            )
        }
    }

    fun downloadSelectedBookFile(file: RemotePublicationFile) {
        val sourceAdapter = adapter ?: run {
            returnToConnection("Connect to Kavita before downloading books.")
            return
        }
        val details = (_uiState.value.bookDetails as? LoadState.Success)?.value ?: run {
            setDownloadState(file.id.value, FileDownloadUiState.Error("Load book details before downloading."))
            return
        }
        if (downloadJobs[file.id.value]?.isActive == true) return

        val job = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    downloadUseCase.download(
                        sourceAdapter = sourceAdapter,
                        book = details,
                        file = file,
                        onProgress = { progress -> setDownloadState(file.id.value, progress.toUiState()) },
                    )
                }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                setDownloadState(
                    file.id.value,
                    FileDownloadUiState.Error(messageForThrowable(error, connectedApiKey)),
                )
                return@launch
            }

            when (result) {
                is DownloadPublicationFileResult.Cached -> {
                    setDownloadState(file.id.value, result.file.toDownloadedUiState())
                }

                is DownloadPublicationFileResult.Downloaded -> {
                    setDownloadState(file.id.value, result.file.toDownloadedUiState())
                }

                is DownloadPublicationFileResult.Failure -> {
                    setDownloadState(
                        file.id.value,
                        FileDownloadUiState.Error(messageForDownloadError(result.error, connectedApiKey)),
                    )
                }
            }
        }
        downloadJobs[file.id.value] = job
    }

    fun goBack() {
        activeJob?.cancel()
        _uiState.update { it.previousScreen() }
    }

    override fun onCleared() {
        httpClient.close()
        databaseDriver.close()
        super.onCleared()
    }

    private fun authenticate(loadLibraries: Boolean) {
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                connectionLoading = true,
                connectionError = null,
                connectionMessage = if (loadLibraries) "Connecting to Kavita..." else "Testing Kavita connection...",
            )
        }

        activeJob = viewModelScope.launch {
            val prepared = prepareManualConnection() ?: return@launch
            val sourceAdapter = runCatching { createAdapter(prepared.credentials) }
                .getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            connectionLoading = false,
                            connectionMessage = null,
                            connectionError = messageForThrowable(error, prepared.credentials.apiKey),
                        )
                    }
                    return@launch
                }

            val authResult = runCatching {
                withContext(Dispatchers.IO) {
                    sourceAdapter.authenticate(prepared.credentials)
                }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        connectionLoading = false,
                        connectionMessage = null,
                        connectionError = messageForThrowable(error, prepared.credentials.apiKey),
                    )
                }
                return@launch
            }

            when (authResult) {
                AuthResult.Success -> {
                    val savedAccount = runCatching {
                        withContext(Dispatchers.IO) {
                            saveConnection(prepared)
                        }
                    }.getOrElse { error ->
                        if (error is CancellationException) throw error
                        _uiState.update {
                            it.copy(
                                connectionLoading = false,
                                connectionMessage = null,
                                connectionError = "Connection worked, but the API key could not be saved securely.",
                            )
                        }
                        return@launch
                    }

                    adapter = sourceAdapter
                    connectedApiKey = prepared.credentials.apiKey
                    if (loadLibraries) {
                        _uiState.update {
                            it.copy(
                                serverUrl = savedAccount.baseUrl,
                                apiKey = "",
                                allowInsecureHttp = savedAccount.allowInsecureHttp,
                                savedConnection = savedAccount.toUiState(),
                                connectionLoading = false,
                                connectionMessage = "Connected.",
                                connectionError = null,
                                screen = BrowserScreen.Libraries,
                                libraries = LoadState.Loading,
                            )
                        }
                        fetchLibraries(sourceAdapter)
                    } else {
                        _uiState.update {
                            it.copy(
                                serverUrl = savedAccount.baseUrl,
                                apiKey = "",
                                allowInsecureHttp = savedAccount.allowInsecureHttp,
                                savedConnection = savedAccount.toUiState(),
                                connectionLoading = false,
                                connectionMessage = "Connection looks good and was saved.",
                                connectionError = null,
                            )
                        }
                    }
                }

                is AuthResult.Failure -> {
                    if (prepared.apiKeyCameFromSavedStore && authResult.error.shouldClearSavedApiKey()) {
                        withContext(Dispatchers.IO) {
                            secureApiKeyStore.delete(SourceId(KavitaAlphaSourceId))
                        }
                    }
                    _uiState.update {
                        it.copy(
                            connectionLoading = false,
                            connectionMessage = null,
                            connectionError = messageForSourceError(authResult.error, prepared.credentials.apiKey),
                        )
                    }
                }
            }
        }
    }

    private fun loadLibraries(sourceAdapter: SourceAdapter? = adapter) {
        val activeAdapter = sourceAdapter ?: run {
            returnToConnection("Connect to Kavita before loading libraries.")
            return
        }
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                screen = BrowserScreen.Libraries,
                selectedLibrary = null,
                selectedSeries = null,
                selectedBook = null,
                libraries = LoadState.Loading,
                series = LoadState.Idle,
                books = LoadState.Idle,
                bookDetails = LoadState.Idle,
            )
        }

        activeJob = viewModelScope.launch {
            fetchLibraries(activeAdapter)
        }
    }

    private suspend fun fetchLibraries(sourceAdapter: SourceAdapter) {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                sourceAdapter.listLibraries()
            }
        }

        result.fold(
            onSuccess = { libraries -> _uiState.update { it.copy(libraries = LoadState.Success(libraries)) } },
            onFailure = { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(libraries = LoadState.Error(messageForThrowable(error, connectedApiKey)))
                }
            },
        )
    }

    private fun restoreSavedConnection() {
        activeJob = viewModelScope.launch {
            val account = withContext(Dispatchers.IO) {
                sourceAccountRepository.getLatestByType(SourceType.KAVITA)
            } ?: return@launch

            _uiState.update {
                it.copy(
                    serverUrl = account.baseUrl,
                    apiKey = "",
                    allowInsecureHttp = account.allowInsecureHttp,
                    savedConnection = account.toUiState(),
                    connectionLoading = true,
                    connectionMessage = "Connecting to saved Kavita server...",
                    connectionError = null,
                )
            }

            val apiKey = runCatching {
                withContext(Dispatchers.IO) {
                    secureApiKeyStore.load(account.id)
                }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                withContext(Dispatchers.IO) {
                    secureApiKeyStore.delete(account.id)
                }
                _uiState.update {
                    it.copy(
                        connectionLoading = false,
                        connectionMessage = null,
                        connectionError = "Saved API key could not be read. Enter a new Kavita API key.",
                    )
                }
                return@launch
            }

            if (apiKey.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        connectionLoading = false,
                        connectionMessage = null,
                        connectionError = "Saved Kavita server found. Enter your API key to reconnect.",
                    )
                }
                return@launch
            }

            val accepted = when (val policy = connectionPolicy.validate(account.baseUrl, account.allowInsecureHttp)) {
                is KavitaConnectionPolicyResult.Accepted -> policy
                KavitaConnectionPolicyResult.InsecureHttpRequiresOptIn,
                KavitaConnectionPolicyResult.InvalidUrl,
                -> {
                    withContext(Dispatchers.IO) {
                        sourceAccountRepository.delete(account.id)
                        secureApiKeyStore.delete(account.id)
                    }
                    _uiState.update {
                        KavitaBrowserUiState(
                            connectionError = "Saved Kavita connection was invalid. Enter the server URL again.",
                        )
                    }
                    return@launch
                }
            }

            val credentials = SourceCredentials.ApiKey(
                serverUrl = accepted.normalizedServerUrl,
                apiKey = apiKey,
            )
            val sourceAdapter = runCatching { createAdapter(credentials) }
                .getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            connectionLoading = false,
                            connectionMessage = null,
                            connectionError = messageForThrowable(error, apiKey),
                        )
                    }
                    return@launch
                }

            when (val authResult = authenticate(sourceAdapter, credentials)) {
                AuthResult.Success -> {
                    adapter = sourceAdapter
                    connectedApiKey = apiKey
                    _uiState.update {
                        it.copy(
                            serverUrl = accepted.normalizedServerUrl,
                            apiKey = "",
                            allowInsecureHttp = accepted.allowInsecureHttp,
                            savedConnection = account.copy(
                                baseUrl = accepted.normalizedServerUrl,
                                allowInsecureHttp = accepted.allowInsecureHttp,
                            ).toUiState(),
                            connectionLoading = false,
                            connectionMessage = "Connected.",
                            connectionError = null,
                            screen = BrowserScreen.Libraries,
                            libraries = LoadState.Loading,
                        )
                    }
                    fetchLibraries(sourceAdapter)
                }

                is AuthResult.Failure -> {
                    if (authResult.error.shouldClearSavedApiKey()) {
                        withContext(Dispatchers.IO) {
                            secureApiKeyStore.delete(account.id)
                        }
                    }
                    _uiState.update {
                        it.copy(
                            apiKey = "",
                            connectionLoading = false,
                            connectionMessage = null,
                            connectionError = if (authResult.error.shouldClearSavedApiKey()) {
                                "Saved API key no longer works. Enter a new Kavita API key."
                            } else {
                                messageForSourceError(authResult.error, apiKey)
                            },
                        )
                    }
                }
            }
        }
    }

    private suspend fun authenticate(
        sourceAdapter: SourceAdapter,
        credentials: SourceCredentials.ApiKey,
    ): AuthResult =
        runCatching {
            withContext(Dispatchers.IO) {
                sourceAdapter.authenticate(credentials)
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            AuthResult.Failure(SourceError.Unknown(messageForThrowable(error, credentials.apiKey)))
        }

    private suspend fun prepareManualConnection(): PreparedConnection? {
        val state = _uiState.value
        val serverUrl = state.serverUrl.trim()
        val typedApiKey = state.apiKey.trim()

        val error = when {
            serverUrl.isBlank() -> "Enter your Kavita server URL."
            else -> null
        }

        if (error != null) {
            _uiState.update {
                it.copy(
                    connectionLoading = false,
                    connectionMessage = null,
                    connectionError = error,
                )
            }
            return null
        }

        val accepted = when (val policy = connectionPolicy.validate(serverUrl, state.allowInsecureHttp)) {
            is KavitaConnectionPolicyResult.Accepted -> policy
            KavitaConnectionPolicyResult.InvalidUrl -> {
                _uiState.update {
                    it.copy(
                        connectionLoading = false,
                        connectionMessage = null,
                        connectionError = "Enter a valid HTTP or HTTPS Kavita server URL.",
                    )
                }
                return null
            }

            KavitaConnectionPolicyResult.InsecureHttpRequiresOptIn -> {
                _uiState.update {
                    it.copy(
                        connectionLoading = false,
                        connectionMessage = null,
                        connectionError = "HTTP Kavita servers require the insecure HTTP opt-in.",
                    )
                }
                return null
            }
        }

        val apiKeyFromSavedStore = typedApiKey.isBlank()
        val apiKey = if (typedApiKey.isNotBlank()) {
            typedApiKey
        } else {
            val savedApiKey = loadSavedApiKeyFor(accepted.normalizedServerUrl)
            if (savedApiKey == null) {
                _uiState.update {
                    if (it.connectionError != null) {
                        it
                    } else {
                        it.copy(
                            connectionLoading = false,
                            connectionMessage = null,
                            connectionError = "Enter your Kavita API key.",
                        )
                    }
                }
                return null
            }
            savedApiKey
        }

        return runCatching {
            PreparedConnection(
                credentials = SourceCredentials.ApiKey(
                    serverUrl = accepted.normalizedServerUrl,
                    apiKey = apiKey,
                ),
                normalizedServerUrl = accepted.normalizedServerUrl,
                allowInsecureHttp = accepted.allowInsecureHttp,
                apiKeyCameFromSavedStore = apiKeyFromSavedStore,
            )
        }.getOrElse {
            _uiState.update {
                it.copy(
                    connectionLoading = false,
                    connectionMessage = null,
                    connectionError = "Enter a valid HTTP or HTTPS Kavita server URL.",
                )
            }
            null
        }
    }

    private suspend fun loadSavedApiKeyFor(normalizedServerUrl: String): String? {
        val saved = _uiState.value.savedConnection ?: return null
        if (saved.baseUrl != normalizedServerUrl) return null

        return runCatching {
            withContext(Dispatchers.IO) {
                secureApiKeyStore.load(SourceId(KavitaAlphaSourceId))
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            withContext(Dispatchers.IO) {
                secureApiKeyStore.delete(SourceId(KavitaAlphaSourceId))
            }
            _uiState.update {
                it.copy(
                    connectionLoading = false,
                    connectionMessage = null,
                    connectionError = "Saved API key could not be read. Enter a new Kavita API key.",
                )
            }
            null
        }
    }

    private suspend fun saveConnection(prepared: PreparedConnection): SavedSourceAccount {
        val now = System.currentTimeMillis()
        val account = SavedSourceAccount(
            id = SourceId(KavitaAlphaSourceId),
            type = SourceType.KAVITA,
            displayName = displayNameFor(prepared.normalizedServerUrl),
            baseUrl = prepared.normalizedServerUrl,
            allowInsecureHttp = prepared.allowInsecureHttp,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )

        sourceAccountRepository.upsert(account)
        secureApiKeyStore.save(account.id, prepared.credentials.apiKey)
        return sourceAccountRepository.getById(account.id) ?: account
    }

    private fun createAdapter(credentials: SourceCredentials.ApiKey): SourceAdapter {
        return KavitaSourceAdapter(
            httpClient = httpClient,
            sourceId = SourceId(KavitaAlphaSourceId),
            serverUrl = credentials.serverUrl,
            apiKey = credentials.apiKey,
        )
    }

    private fun returnToConnection(message: String) {
        _uiState.update {
            it.copy(
                screen = BrowserScreen.Connection,
                connectionLoading = false,
                connectionMessage = null,
                connectionError = message.redactApiKey(it.apiKey),
            )
        }
    }

    private fun setDownloadState(
        fileId: String,
        downloadState: FileDownloadUiState,
    ) {
        _uiState.update { state ->
            state.copy(downloadStates = state.downloadStates + (fileId to downloadState))
        }
    }

    private suspend fun markCachedFiles(
        sourceAdapter: SourceAdapter,
        files: List<RemotePublicationFile>,
    ) {
        val cachedStates = withContext(Dispatchers.IO) {
            files.mapNotNull { file ->
                val cached = publicationFileRepository.findPublicationFile(
                    sourceId = sourceAdapter.sourceId,
                    remoteFileId = file.id,
                    format = file.format,
                ) ?: return@mapNotNull null

                if (!isUsableCachedFile(cached, file.sizeBytes)) {
                    return@mapNotNull null
                }

                file.id.value to cached.toDownloadedUiState()
            }.toMap()
        }

        if (cachedStates.isNotEmpty()) {
            _uiState.update { state ->
                state.copy(downloadStates = state.downloadStates + cachedStates)
            }
        }
    }

    private suspend fun isUsableCachedFile(
        cached: LocalPublicationFile,
        expectedSizeBytes: Long?,
    ): Boolean {
        if (!publicationFileStore.exists(cached.localPath)) return false
        val expectedSize = expectedSizeBytes ?: cached.fileSize ?: return true
        return publicationFileStore.sizeBytes(cached.localPath) == expectedSize
    }

    private fun LocalPublicationFile.toDownloadedUiState(): FileDownloadUiState.Downloaded =
        FileDownloadUiState.Downloaded(
            localPath = localPath,
            bookIdentityId = bookIdentityId.value,
            publicationFileId = id.value,
        )

    private fun DownloadProgress.toUiState(): FileDownloadUiState {
        return when (this) {
            DownloadProgress.CheckingCache -> FileDownloadUiState.Checking
            is DownloadProgress.Cached -> file.toDownloadedUiState()
            is DownloadProgress.Downloaded -> file.toDownloadedUiState()
            is DownloadProgress.Downloading -> FileDownloadUiState.Downloading(
                bytesWritten = bytesWritten,
                totalBytes = totalBytes,
            )
        }
    }

    private fun SavedSourceAccount.toUiState(): SavedConnectionUiState =
        SavedConnectionUiState(
            displayName = displayName,
            baseUrl = baseUrl,
            allowInsecureHttp = allowInsecureHttp,
        )

    private fun SourceError.shouldClearSavedApiKey(): Boolean =
        this == SourceError.Unauthorized || this == SourceError.ApiKeyExpired

    private fun displayNameFor(normalizedServerUrl: String): String {
        val host = runCatching { URI(normalizedServerUrl).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: normalizedServerUrl

        return "Kavita ($host)"
    }

    private companion object {
        const val KavitaAlphaSourceId = "kavita-alpha"
    }
}

private data class PreparedConnection(
    val credentials: SourceCredentials.ApiKey,
    val normalizedServerUrl: String,
    val allowInsecureHttp: Boolean,
    val apiKeyCameFromSavedStore: Boolean,
)
