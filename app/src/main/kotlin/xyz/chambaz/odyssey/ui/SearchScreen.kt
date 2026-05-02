package xyz.chambaz.odyssey.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import xyz.chambaz.odyssey.model.Audiobook
import xyz.chambaz.odyssey.store.DownloadState

private fun fuzzyMatch(query: String, target: String): Boolean {
    val q = query.lowercase()
    val t = target.lowercase()
    var qi = 0
    for (c in t) { if (qi < q.length && c == q[qi]) qi++ }
    return qi == q.length
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    books: List<Audiobook>,
    downloadStates: Map<String, DownloadState>,
    downloadProgress: Map<String, Float>,
    onDownload: (Audiobook) -> Unit,
    onCancelDownload: (Audiobook) -> Unit,
    onBookSelected: (Audiobook) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    var query by remember { mutableStateOf("") }
    val results = remember(query, books) {
        if (query.isBlank()) books
        else books.filter { fuzzyMatch(query, it.title) || fuzzyMatch(query, it.author) }
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboard?.show() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = Accent,
                        ),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(results) { book ->
                BookListItem(
                    book = book,
                    downloadState = downloadStates[book.hash] ?: DownloadState.REMOTE,
                    downloadProgress = downloadProgress[book.hash] ?: 0f,
                    onClick = { onBookSelected(book) },
                    onDownload = { onDownload(book) },
                    onCancelDownload = { onCancelDownload(book) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
