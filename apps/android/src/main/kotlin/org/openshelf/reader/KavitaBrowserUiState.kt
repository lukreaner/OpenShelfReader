package org.openshelf.reader

import org.openshelf.reader.source.api.RemoteBook
import org.openshelf.reader.source.api.RemoteBookDetails
import org.openshelf.reader.source.api.RemoteFileId
import org.openshelf.reader.source.api.RemoteLibrary
import org.openshelf.reader.source.api.RemoteSeries

internal sealed interface BrowserScreen {
    data object Connection : BrowserScreen
    data object Libraries : BrowserScreen
    data object Series : BrowserScreen
    data object Books : BrowserScreen
    data object BookDetails : BrowserScreen
}

internal sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Success<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

internal sealed interface FileDownloadUiState {
    data object Idle : FileDownloadUiState
    data object Checking : FileDownloadUiState
    data class Downloading(val bytesWritten: Long, val totalBytes: Long?) : FileDownloadUiState
    data class Downloaded(
        val localPath: String,
        val bookIdentityId: String,
        val publicationFileId: String,
    ) : FileDownloadUiState
    data class Error(val message: String) : FileDownloadUiState
}

internal data class KavitaBrowserUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val allowInsecureHttp: Boolean = false,
    val savedConnection: SavedConnectionUiState? = null,
    val connectionLoading: Boolean = false,
    val connectionMessage: String? = null,
    val connectionError: String? = null,
    val screen: BrowserScreen = BrowserScreen.Connection,
    val selectedLibrary: RemoteLibrary? = null,
    val selectedSeries: RemoteSeries? = null,
    val selectedBook: RemoteBook? = null,
    val libraries: LoadState<List<RemoteLibrary>> = LoadState.Idle,
    val series: LoadState<List<RemoteSeries>> = LoadState.Idle,
    val books: LoadState<List<RemoteBook>> = LoadState.Idle,
    val bookDetails: LoadState<RemoteBookDetails> = LoadState.Idle,
    val downloadStates: Map<String, FileDownloadUiState> = emptyMap(),
) {
    val canNavigateBack: Boolean
        get() = screen != BrowserScreen.Connection

    override fun toString(): String {
        return "KavitaBrowserUiState(" +
            "serverUrl=$serverUrl, " +
            "apiKey=<redacted>, " +
            "allowInsecureHttp=$allowInsecureHttp, " +
            "savedConnection=$savedConnection, " +
            "connectionLoading=$connectionLoading, " +
            "connectionMessage=$connectionMessage, " +
            "connectionError=$connectionError, " +
            "screen=$screen" +
            ")"
    }
}

internal data class SavedConnectionUiState(
    val displayName: String,
    val baseUrl: String,
    val allowInsecureHttp: Boolean,
) {
    init {
        require(displayName.isNotBlank()) { "Saved connection display name must not be blank." }
        require(baseUrl.isNotBlank()) { "Saved connection base URL must not be blank." }
    }
}

internal fun KavitaBrowserUiState.downloadStateFor(fileId: RemoteFileId): FileDownloadUiState =
    downloadStates[fileId.value] ?: FileDownloadUiState.Idle

internal fun KavitaBrowserUiState.previousScreen(): KavitaBrowserUiState {
    return when (screen) {
        BrowserScreen.Connection -> this
        BrowserScreen.Libraries -> copy(
            screen = BrowserScreen.Connection,
            selectedLibrary = null,
            selectedSeries = null,
            selectedBook = null,
            series = LoadState.Idle,
            books = LoadState.Idle,
            bookDetails = LoadState.Idle,
        )

        BrowserScreen.Series -> copy(
            screen = BrowserScreen.Libraries,
            selectedSeries = null,
            selectedBook = null,
            books = LoadState.Idle,
            bookDetails = LoadState.Idle,
        )

        BrowserScreen.Books -> copy(
            screen = BrowserScreen.Series,
            selectedBook = null,
            bookDetails = LoadState.Idle,
        )

        BrowserScreen.BookDetails -> copy(screen = BrowserScreen.Books)
    }
}
