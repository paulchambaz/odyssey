package xyz.chambaz.odyssey.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.chambaz.odyssey.model.Audiobook
import xyz.chambaz.odyssey.model.Position
import java.io.File

@Composable
fun LazyBookCover(hash: String, bookDir: File) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(hash) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val infoFile = File(bookDir, "info.yml")
                val lines = infoFile.readLines()
                val coverPath = lines.firstOrNull { it.startsWith("cover:") }
                    ?.removePrefix("cover:")?.trim()?.trim('"')
                    ?: return@withContext null
                val coverFile = File(bookDir, coverPath)
                BitmapFactory.decodeFile(coverFile.absolutePath)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun progressCategory(progress: Float?): Int = when {
    progress == null || progress == 0f -> 1
    progress >= 0.95f -> 2
    else -> 0
}

private fun bookProgress(position: Position, chapterCount: Int, duration: Long): Float {
    if (chapterCount == 0 || duration == 0L) return 0f
    val totalMs = duration * 1000L
    val chapterMs = totalMs / chapterCount
    val posMs = position.chapterIndex * chapterMs + position.chapterPosition
    return (posMs.toFloat() / totalMs).coerceIn(0f, 1f)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudiobooksContent(
    books: List<Audiobook>,
    positions: Map<String, Position?>,
    chapterCounts: Map<String, Int>,
    downloadTimestamps: Map<String, Long>,
    store: xyz.chambaz.odyssey.store.Store,
    filesDir: File,
    onNavigatePlayer: (Audiobook) -> Unit,
    onLongPress: (Audiobook) -> Unit,
) {
    val sorted = remember(books, positions, chapterCounts, downloadTimestamps) {
        books.sortedWith(
            compareBy<Audiobook> { book ->
                val pos = positions[book.hash]
                val p = pos?.let { bookProgress(it, chapterCounts[book.hash] ?: 0, book.duration ?: 0L).takeIf { (chapterCounts[book.hash] ?: 0) > 0 && (book.duration ?: 0L) > 0L } }
                progressCategory(p)
            }
            .thenByDescending { positions[it.hash]?.clientTimestamp ?: 0L }
            .thenByDescending { downloadTimestamps[it.hash] ?: 0L }
            .thenBy { it.title }
        )
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(sorted, key = { it.hash }) { book ->
            val position = positions[book.hash]
            val progress = position?.let {
                val c = chapterCounts[book.hash] ?: 0
                val d = book.duration ?: 0L
                if (c > 0 && d > 0) bookProgress(it, c, d) else null
            }
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(book.title)
                            Text(
                                book.author,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (progress != null) {
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(0.67f)) {
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(2.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(3.dp),
                                    color = Accent,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    drawStopIndicator = {},
                                )
                            }
                        }
                    }
                },
                leadingContent = {
                    LazyBookCover(book.hash, store.libraryDir(book.hash, filesDir))
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.combinedClickable(
                    onClick = { onNavigatePlayer(book) },
                    onLongClick = { onLongPress(book) },
                ),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun fuzzyMatch(query: String, target: String): Boolean {
    val q = query.lowercase()
    val t = target.lowercase()
    var qi = 0
    for (c in t) { if (qi < q.length && c == q[qi]) qi++ }
    return qi == q.length
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AudiobooksSearchScreen(
    books: List<Audiobook>,
    positions: Map<String, Position?>,
    chapterCounts: Map<String, Int>,
    downloadTimestamps: Map<String, Long>,
    store: xyz.chambaz.odyssey.store.Store,
    filesDir: File,
    onBack: () -> Unit,
    onNavigatePlayer: (Audiobook) -> Unit,
    onLongPress: (Audiobook) -> Unit,
) {
    BackHandler { onBack() }
    var query by remember { mutableStateOf("") }
    val results = remember(query, books, positions, chapterCounts, downloadTimestamps) {
        val filtered = if (query.isBlank()) books else books.filter { fuzzyMatch(query, it.title) || fuzzyMatch(query, it.author) }
        filtered.sortedWith(
            compareBy<Audiobook> { book ->
                val pos = positions[book.hash]
                val p = pos?.let { bookProgress(it, chapterCounts[book.hash] ?: 0, book.duration ?: 0L).takeIf { (chapterCounts[book.hash] ?: 0) > 0 && (book.duration ?: 0L) > 0L } }
                progressCategory(p)
            }
            .thenByDescending { positions[it.hash]?.clientTimestamp ?: 0L }
            .thenByDescending { downloadTimestamps[it.hash] ?: 0L }
            .thenBy { it.title }
        )
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
                val position = positions[book.hash]
                val progress = position?.let {
                    val c = chapterCounts[book.hash] ?: 0
                    val d = book.duration ?: 0L
                    if (c > 0 && d > 0) bookProgress(it, c, d) else null
                }
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(book.title)
                                Text(
                                    book.author,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (progress != null) {
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(0.67f)) {
                                    Text(
                                        "${(progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                        color = Accent,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        drawStopIndicator = {},
                                    )
                                }
                            }
                        }
                    },
                    leadingContent = {
                        LazyBookCover(book.hash, store.libraryDir(book.hash, filesDir))
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.combinedClickable(
                        onClick = { onNavigatePlayer(book) },
                        onLongClick = { onLongPress(book) },
                    ),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
