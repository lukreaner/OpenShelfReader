package org.openshelf.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openshelf.reader.kavita.api.KavitaSourceAdapter
import org.openshelf.reader.source.api.AuthResult
import org.openshelf.reader.source.api.RemoteBook
import org.openshelf.reader.source.api.RemoteLibrary
import org.openshelf.reader.source.api.RemoteSeries
import org.openshelf.reader.source.api.SourceAdapter
import org.openshelf.reader.source.api.SourceCredentials
import org.openshelf.reader.source.api.SourceId

internal class KavitaBrowserViewModel : ViewModel() {
    private val httpClient = HttpClient(OkHttp)
    private val _uiState = MutableStateFlow(KavitaBrowserUiState())
    private var adapter: SourceAdapter? = null
    private var connectedApiKey: String = ""
    private var activeJob: Job? = null

    val uiState: StateFlow<KavitaBrowserUiState> = _uiState.asStateFlow()

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

    fun testConnection() {
        authenticate(loadLibraries = false)
    }

    fun connectAndLoadLibraries() {
        authenticate(loadLibraries = true)
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
            )
        }

        activeJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    sourceAdapter.getBook(book.id)
                }
            }

            result.fold(
                onSuccess = { details -> _uiState.update { it.copy(bookDetails = LoadState.Success(details)) } },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(bookDetails = LoadState.Error(messageForThrowable(error, connectedApiKey)))
                    }
                },
            )
        }
    }

    fun goBack() {
        activeJob?.cancel()
        _uiState.update { it.previousScreen() }
    }

    override fun onCleared() {
        httpClient.close()
        super.onCleared()
    }

    private fun authenticate(loadLibraries: Boolean) {
        val credentials = credentialsOrNull() ?: return
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                connectionLoading = true,
                connectionError = null,
                connectionMessage = if (loadLibraries) "Connecting to Kavita..." else "Testing Kavita connection...",
            )
        }

        activeJob = viewModelScope.launch {
            val sourceAdapter = runCatching { createAdapter(credentials) }
                .getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            connectionLoading = false,
                            connectionMessage = null,
                            connectionError = messageForThrowable(error, credentials.apiKey),
                        )
                    }
                    return@launch
                }

            val authResult = runCatching {
                withContext(Dispatchers.IO) {
                    sourceAdapter.authenticate(credentials)
                }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        connectionLoading = false,
                        connectionMessage = null,
                        connectionError = messageForThrowable(error, credentials.apiKey),
                    )
                }
                return@launch
            }

            when (authResult) {
                AuthResult.Success -> {
                    adapter = sourceAdapter
                    connectedApiKey = credentials.apiKey
                    if (loadLibraries) {
                        _uiState.update {
                            it.copy(
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
                                connectionLoading = false,
                                connectionMessage = "Connection looks good.",
                                connectionError = null,
                            )
                        }
                    }
                }

                is AuthResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            connectionLoading = false,
                            connectionMessage = null,
                            connectionError = messageForSourceError(authResult.error, credentials.apiKey),
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

    private fun credentialsOrNull(): SourceCredentials.ApiKey? {
        val state = _uiState.value
        val serverUrl = state.serverUrl.trim()
        val apiKey = state.apiKey.trim()

        val error = when {
            serverUrl.isBlank() -> "Enter your Kavita server URL."
            apiKey.isBlank() -> "Enter your Kavita API key."
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

        return runCatching {
            SourceCredentials.ApiKey(
                serverUrl = serverUrl,
                apiKey = apiKey,
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

    private companion object {
        const val KavitaAlphaSourceId = "kavita-alpha"
    }
}
