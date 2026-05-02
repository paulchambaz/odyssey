package xyz.chambaz.odyssey.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import xyz.chambaz.odyssey.model.Audiobook
import xyz.chambaz.odyssey.store.DownloadState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    books: List<Audiobook>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    downloadStates: Map<String, DownloadState>,
    downloadProgress: Map<String, Float>,
    onBookSelected: (Audiobook) -> Unit,
    onDownload: (Audiobook) -> Unit,
    onCancelDownload: (Audiobook) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(books) { book ->
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

@Composable
fun BookListItem(
    book: Audiobook,
    downloadState: DownloadState,
    downloadProgress: Float,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    if (showCancelDialog) {
        Dialog(onDismissRequest = { showCancelDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Cancel download", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text("Cancel downloading \"${book.title}\"?", color = MaterialTheme.colorScheme.onBackground)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showCancelDialog = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) { Text("Keep", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Button(
                            onClick = { showCancelDialog = false; onCancelDownload() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) { Text("Cancel") }
                    }
                }
            }
        }
    }

    ListItem(
        headlineContent = { Text(book.title) },
        supportingContent = { Text(book.author) },
        trailingContent = when (downloadState) {
            DownloadState.REMOTE -> ({
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
            DownloadState.IN_PROGRESS -> ({
                Box(modifier = Modifier.size(40.dp).clickable { showCancelDialog = true }, contentAlignment = Alignment.Center) {
                    CircleArc(progress = downloadProgress)
                }
            })
            DownloadState.READY -> null
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CircleArc(progress: Float) {
    val accent = Accent
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke,
        )
        if (progress > 0f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = stroke,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailSheet(
    book: Audiobook,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    downloadState: DownloadState,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit = {},
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    if (showCancelDialog) {
        Dialog(onDismissRequest = { showCancelDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Cancel download", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text("Cancel downloading \"${book.title}\"?", color = MaterialTheme.colorScheme.onBackground)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showCancelDialog = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) { Text("Keep", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Button(
                            onClick = { showCancelDialog = false; onDismiss(); onCancelDownload() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) { Text("Cancel") }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        Dialog(onDismissRequest = { showDeleteDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Delete", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text("Remove \"${book.title}\" from your device?", color = MaterialTheme.colorScheme.onBackground)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Button(
                            onClick = { showDeleteDialog = false; onDismiss(); onDelete() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) { Text("Delete") }
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(book.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(book.author, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val bmp = coverBitmap ?: book.cover?.let { b64 ->
                remember(b64) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }
            if (bmp != null) {
                Spacer(Modifier.height(16.dp))
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(320.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .align(Alignment.CenterHorizontally),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.height(8.dp))
            book.date?.let { Text("Year: $it", color = MaterialTheme.colorScheme.onBackground) }
            book.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            book.genres?.let {
                Spacer(Modifier.height(4.dp))
                Text(it.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            book.duration?.let {
                Spacer(Modifier.height(4.dp))
                Text("${it / 3_600}h ${(it % 3_600) / 60}m", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            book.size?.let {
                Text("${it / 1_000_000} MB", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (downloadState) {
                    DownloadState.REMOTE -> OutlinedButton(
                        onClick = onDownload,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(2.dp, Accent),
                    ) { Text("Download", color = Accent) }
                    DownloadState.IN_PROGRESS -> OutlinedButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(2.dp, Accent),
                    ) { Text("Downloading…", color = Accent) }
                    DownloadState.READY -> OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(2.dp, Accent),
                    ) { Text("Delete", color = Accent) }
                }
                Button(
                    onClick = onPlay,
                    enabled = downloadState == DownloadState.READY,
                    modifier = Modifier.weight(1f),
                ) { Text("Play") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    initialDate: ClosedFloatingPointRange<Float>,
    initialDuration: ClosedFloatingPointRange<Float>,
    initialGenres: List<String>,
    minYear: Float,
    maxYear: Float,
    minDuration: Float,
    maxDuration: Float,
    availableGenres: List<String>,
    onApply: (ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var dateRange by remember { mutableStateOf(initialDate) }
    var durationRange by remember { mutableStateOf(initialDuration) }
    val selected = remember { mutableStateListOf<String>().also { it.addAll(initialGenres) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Filter", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))
            Text("Date: ${dateRange.start.toInt()} – ${dateRange.endInclusive.toInt()}", color = MaterialTheme.colorScheme.onBackground)
            RangeSlider(value = dateRange, onValueChange = { dateRange = it }, valueRange = minYear..maxYear)
            Spacer(Modifier.height(8.dp))
            Text("Duration: ${durationRange.start.toInt()} – ${durationRange.endInclusive.toInt()} hours", color = MaterialTheme.colorScheme.onBackground)
            RangeSlider(value = durationRange, onValueChange = { durationRange = it }, valueRange = minDuration..maxDuration)
            Spacer(Modifier.height(8.dp))
            Text("Tags", color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableGenres.forEach { genre ->
                    FilterChip(
                        selected = genre in selected,
                        onClick = { if (genre in selected) selected.remove(genre) else selected.add(genre) },
                        label = { Text(genre) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onApply(minYear..maxYear, minDuration..maxDuration, emptyList()); onDismiss() },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(2.dp, Accent),
                ) { Text("Reset", color = Accent) }
                Button(
                    onClick = { onApply(dateRange, durationRange, selected.toList()); onDismiss() },
                    modifier = Modifier.weight(1f),
                ) { Text("Apply") }
            }
        }
    }
}
