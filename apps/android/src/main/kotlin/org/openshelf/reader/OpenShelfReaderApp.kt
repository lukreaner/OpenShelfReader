package org.openshelf.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openshelf.reader.core.PublicationFormat
import org.openshelf.reader.source.api.RemoteBook
import org.openshelf.reader.source.api.RemoteBookDetails
import org.openshelf.reader.source.api.RemoteLibrary
import org.openshelf.reader.source.api.RemotePublicationFile
import org.openshelf.reader.source.api.RemoteSeries

@Composable
internal fun OpenShelfReaderApp(
    viewModel: KavitaBrowserViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = state.canNavigateBack) {
        viewModel.goBack()
    }

    OpenShelfTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            KavitaBrowserScreen(
                state = state,
                onServerUrlChanged = viewModel::onServerUrlChanged,
                onApiKeyChanged = viewModel::onApiKeyChanged,
                onTestConnection = viewModel::testConnection,
                onConnectAndLoadLibraries = viewModel::connectAndLoadLibraries,
                onLibrarySelected = viewModel::selectLibrary,
                onSeriesSelected = viewModel::selectSeries,
                onBookSelected = viewModel::selectBook,
                onRetry = viewModel::retryCurrentScreen,
                onBack = viewModel::goBack,
            )
        }
    }
}

@Composable
private fun OpenShelfTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}

@Composable
private fun KavitaBrowserScreen(
    state: KavitaBrowserUiState,
    onServerUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onTestConnection: () -> Unit,
    onConnectAndLoadLibraries: () -> Unit,
    onLibrarySelected: (RemoteLibrary) -> Unit,
    onSeriesSelected: (RemoteSeries) -> Unit,
    onBookSelected: (RemoteBook) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = state.title(),
                canNavigateBack = state.canNavigateBack,
                onBack = onBack,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (state.screen) {
                BrowserScreen.Connection -> ConnectionScreen(
                    state = state,
                    onServerUrlChanged = onServerUrlChanged,
                    onApiKeyChanged = onApiKeyChanged,
                    onTestConnection = onTestConnection,
                    onConnectAndLoadLibraries = onConnectAndLoadLibraries,
                )

                BrowserScreen.Libraries -> LibrariesScreen(
                    libraries = state.libraries,
                    onLibrarySelected = onLibrarySelected,
                    onRetry = onRetry,
                )

                BrowserScreen.Series -> SeriesScreen(
                    series = state.series,
                    onSeriesSelected = onSeriesSelected,
                    onRetry = onRetry,
                )

                BrowserScreen.Books -> BooksScreen(
                    books = state.books,
                    onBookSelected = onBookSelected,
                    onRetry = onRetry,
                )

                BrowserScreen.BookDetails -> BookDetailsScreen(
                    fallbackBook = state.selectedBook,
                    details = state.bookDetails,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun AppTopBar(
    title: String,
    canNavigateBack: Boolean,
    onBack: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canNavigateBack) {
                TextButton(onClick = onBack) {
                    Text("Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun ConnectionScreen(
    state: KavitaBrowserUiState,
    onServerUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onTestConnection: () -> Unit,
    onConnectAndLoadLibraries: () -> Unit,
) {
    ScreenColumn(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = onServerUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server URL") },
            placeholder = { Text("https://kavita.example") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
        )

        OutlinedTextField(
            value = state.apiKey,
            onValueChange = onApiKeyChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        )

        OutlinedButton(
            onClick = onTestConnection,
            enabled = !state.connectionLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test connection")
        }

        Button(
            onClick = onConnectAndLoadLibraries,
            enabled = !state.connectionLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Connect and load libraries")
        }

        if (state.connectionLoading) {
            LoadingRow(message = state.connectionMessage ?: "Connecting...")
        }

        state.connectionMessage
            ?.takeIf { !state.connectionLoading }
            ?.let { StatusText(message = it, isError = false) }

        state.connectionError?.let { StatusText(message = it, isError = true) }
    }
}

@Composable
private fun LibrariesScreen(
    libraries: LoadState<List<RemoteLibrary>>,
    onLibrarySelected: (RemoteLibrary) -> Unit,
    onRetry: () -> Unit,
) {
    LoadableContent(
        state = libraries,
        emptyMessage = "No Kavita libraries were returned.",
        loadingMessage = "Loading libraries...",
        onRetry = onRetry,
    ) { items ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(items, key = { it.id.value }) { library ->
                ListItem(
                    headlineContent = { Text(library.name) },
                    supportingContent = { Text("Kavita library") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLibrarySelected(library) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SeriesScreen(
    series: LoadState<List<RemoteSeries>>,
    onSeriesSelected: (RemoteSeries) -> Unit,
    onRetry: () -> Unit,
) {
    LoadableContent(
        state = series,
        emptyMessage = "No series were found in this library.",
        loadingMessage = "Loading series...",
        onRetry = onRetry,
    ) { items ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(items, key = { it.id.value }) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = {
                        item.bookCount?.let { count ->
                            Text("$count ${if (count == 1) "book" else "books"}")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeriesSelected(item) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BooksScreen(
    books: LoadState<List<RemoteBook>>,
    onBookSelected: (RemoteBook) -> Unit,
    onRetry: () -> Unit,
) {
    LoadableContent(
        state = books,
        emptyMessage = "No books or chapters were found in this series.",
        loadingMessage = "Loading books...",
        onRetry = onRetry,
    ) { items ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(items, key = { it.id.value }) { book ->
                ListItem(
                    headlineContent = { Text(book.title) },
                    supportingContent = {
                        BookMetadataText(
                            authors = book.authors,
                            formats = book.formats,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBookSelected(book) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BookDetailsScreen(
    fallbackBook: RemoteBook?,
    details: LoadState<RemoteBookDetails>,
    onRetry: () -> Unit,
) {
    when (details) {
        LoadState.Idle,
        LoadState.Loading,
        -> ScreenColumn {
            Text(
                text = fallbackBook?.title ?: "Loading book...",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            LoadingRow(message = "Loading book details...")
        }

        is LoadState.Error -> ErrorState(
            message = details.message,
            onRetry = onRetry,
        )

        is LoadState.Success -> BookDetails(details.value)
    }
}

@Composable
private fun BookDetails(details: RemoteBookDetails) {
    ScreenColumn(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = details.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        DetailText(label = "Authors", value = details.authors.joinReadable())
        DetailText(label = "Formats", value = formatsText(details.formats))

        details.publishedYear?.let { year ->
            DetailText(label = "Published", value = year.toString())
        }

        if (!details.summary.isNullOrBlank()) {
            DetailText(label = "Summary", value = details.summary.trim())
        }

        Text(
            text = "Files",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (details.files.isEmpty()) {
            Text(
                text = "No file metadata available.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            details.files.forEach { file ->
                PublicationFileRow(file)
            }
        }

        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reader not implemented yet")
        }
    }
}

@Composable
private fun PublicationFileRow(file: RemotePublicationFile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = file.fileName ?: "Unnamed file",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = listOfNotNull(
                file.format.takeUnless { it == PublicationFormat.UNKNOWN }?.name,
                file.sizeBytes?.let(::formatBytes),
            ).joinToString(" | ").ifBlank { "Format unavailable" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun <T> LoadableContent(
    state: LoadState<List<T>>,
    emptyMessage: String,
    loadingMessage: String,
    onRetry: () -> Unit,
    content: @Composable (List<T>) -> Unit,
) {
    when (state) {
        LoadState.Idle,
        LoadState.Loading,
        -> LoadingState(message = loadingMessage)

        is LoadState.Error -> ErrorState(
            message = state.message,
            onRetry = onRetry,
        )

        is LoadState.Success -> {
            if (state.value.isEmpty()) {
                EmptyState(message = emptyMessage)
            } else {
                content(state.value)
            }
        }
    }
}

@Composable
private fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun LoadingState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingRow(message = message)
    }
}

@Composable
private fun LoadingRow(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    ScreenColumn {
        StatusText(message = message, isError = true)
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun StatusText(
    message: String,
    isError: Boolean,
) {
    val color = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Text(
        text = message,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun BookMetadataText(
    authors: List<String>,
    formats: Set<PublicationFormat>,
) {
    val lines = listOfNotNull(
        authors.joinReadable(),
        formatsText(formats),
    )

    Text(
        text = lines.ifEmpty { listOf("No extra metadata available") }.joinToString("\n"),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DetailText(
    label: String,
    value: String?,
) {
    if (value.isNullOrBlank()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun KavitaBrowserUiState.title(): String {
    return when (screen) {
        BrowserScreen.Connection -> "OpenShelf Reader"
        BrowserScreen.Libraries -> "Libraries"
        BrowserScreen.Series -> selectedLibrary?.name ?: "Series"
        BrowserScreen.Books -> selectedSeries?.title ?: "Books"
        BrowserScreen.BookDetails -> selectedBook?.title ?: "Book details"
    }
}

private fun List<String>.joinReadable(): String? {
    return filter { it.isNotBlank() }
        .joinToString(", ")
        .takeIf { it.isNotBlank() }
}

private fun formatsText(formats: Set<PublicationFormat>): String? {
    return formats
        .filterNot { it == PublicationFormat.UNKNOWN }
        .map { it.name }
        .sorted()
        .joinToString(", ")
        .takeIf { it.isNotBlank() }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024.0) return "${kib.formatOneDecimal()} KiB"
    val mib = kib / 1024.0
    if (mib < 1024.0) return "${mib.formatOneDecimal()} MiB"
    val gib = mib / 1024.0
    return "${gib.formatOneDecimal()} GiB"
}

private fun Double.formatOneDecimal(): String {
    val rounded = kotlin.math.round(this * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}
