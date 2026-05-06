package org.openshelf.reader

import org.openshelf.reader.source.api.RemoteBook
import org.openshelf.reader.source.api.RemoteBookDetails
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

internal data class KavitaBrowserUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
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
) {
    val canNavigateBack: Boolean
        get() = screen != BrowserScreen.Connection

    override fun toString(): String {
        return "KavitaBrowserUiState(" +
            "serverUrl=$serverUrl, " +
            "apiKey=<redacted>, " +
            "connectionLoading=$connectionLoading, " +
            "connectionMessage=$connectionMessage, " +
            "connectionError=$connectionError, " +
            "screen=$screen" +
            ")"
    }
}

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
