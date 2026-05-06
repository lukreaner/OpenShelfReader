package org.openshelf.reader

import org.openshelf.reader.source.api.RemoteBook
import org.openshelf.reader.source.api.RemoteBookId
import org.openshelf.reader.source.api.RemoteLibrary
import org.openshelf.reader.source.api.RemoteLibraryId
import org.openshelf.reader.source.api.RemoteSeries
import org.openshelf.reader.source.api.RemoteSeriesId
import org.openshelf.reader.source.api.SourceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KavitaBrowserUiStateTest {
    @Test
    fun backNavigationFollowsLibrarySeriesBooksStack() {
        val detailsState = KavitaBrowserUiState(
            screen = BrowserScreen.BookDetails,
            selectedLibrary = TestLibrary,
            selectedSeries = TestSeries,
            selectedBook = TestBook,
        )

        val booksState = detailsState.previousScreen()
        assertEquals(BrowserScreen.Books, booksState.screen)
        assertEquals(TestBook, booksState.selectedBook)

        val seriesState = booksState.previousScreen()
        assertEquals(BrowserScreen.Series, seriesState.screen)
        assertNull(seriesState.selectedBook)

        val librariesState = seriesState.previousScreen()
        assertEquals(BrowserScreen.Libraries, librariesState.screen)
        assertEquals(TestLibrary, librariesState.selectedLibrary)
        assertNull(librariesState.selectedSeries)

        val connectionState = librariesState.previousScreen()
        assertEquals(BrowserScreen.Connection, connectionState.screen)
        assertNull(connectionState.selectedLibrary)
    }

    @Test
    fun canNavigateBackOnlyAfterConnectionScreen() {
        assertFalse(KavitaBrowserUiState(screen = BrowserScreen.Connection).canNavigateBack)
        assertTrue(KavitaBrowserUiState(screen = BrowserScreen.Libraries).canNavigateBack)
    }

    @Test
    fun stateStringRedactsApiKey() {
        val state = KavitaBrowserUiState(apiKey = TestApiKey)

        assertFalse(state.toString().contains(TestApiKey))
        assertTrue(state.toString().contains("apiKey=<redacted>"))
    }

    private companion object {
        val Source = SourceId("kavita-alpha")
        val TestLibrary = RemoteLibrary(
            id = RemoteLibraryId("1"),
            sourceId = Source,
            name = "Fiction",
        )
        val TestSeries = RemoteSeries(
            id = RemoteSeriesId("10"),
            sourceId = Source,
            libraryId = TestLibrary.id,
            title = "Leviathan",
        )
        val TestBook = RemoteBook(
            id = RemoteBookId("100"),
            sourceId = Source,
            title = "Chapter 1",
            seriesId = TestSeries.id,
        )
        const val TestApiKey = "kavita-test-api-key"
    }
}
