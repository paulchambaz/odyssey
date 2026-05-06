package xyz.chambaz.odyssey.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.ImageBitmap
import xyz.chambaz.odyssey.model.Audiobook
import xyz.chambaz.odyssey.model.Position
import xyz.chambaz.odyssey.store.DownloadState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    books: List<Audiobook>,
    filteredBooks: List<Audiobook>,
    localBooks: List<Audiobook>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    positions: Map<String, Position?>,
    covers: Map<String, ImageBitmap?>,
    chapterCounts: Map<String, Int>,
    downloadTimestamps: Map<String, Long>,
    downloadStates: Map<String, DownloadState>,
    downloadProgress: Map<String, Float>,
    store: xyz.chambaz.odyssey.store.Store,
    filesDir: java.io.File,
    onDownloadStateChange: (String, DownloadState) -> Unit,
    onDownload: (Audiobook) -> Unit,
    onCancelDownload: (Audiobook) -> Unit,
    onDelete: (Audiobook) -> Unit,
    onBookSelected: (Audiobook) -> Unit,
    onLocalBookSelected: (Audiobook) -> Unit,
    filterDate: ClosedFloatingPointRange<Float>,
    filterDuration: ClosedFloatingPointRange<Float>,
    filterGenres: List<String>,
    bookMinYear: Float,
    bookMaxYear: Float,
    bookMinDuration: Float,
    bookMaxDuration: Float,
    onFilterChange: (ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>, List<String>) -> Unit,
    onNavigateAudiobooksSearch: () -> Unit,
    onNavigateLibrarySearch: () -> Unit,
    onNavigatePlayer: (Audiobook) -> Unit,
    onNavigateSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })
    var showFilter by remember { mutableStateOf(false) }

    if (pagerState.currentPage == 1) {
        BackHandler { scope.launch { pagerState.animateScrollToPage(0) } }
    }

    val availableGenres = remember(books) {
        books.flatMap { it.genres ?: emptyList() }.distinct().sorted()
    }

    LaunchedEffect(pagerState.currentPage) { onPageChange(pagerState.currentPage) }

    val accent = Accent
    val background = MaterialTheme.colorScheme.background
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(if (pagerState.currentPage == 0) "Audiobooks" else "Library") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    if (pagerState.currentPage == 0) {
                        IconButton(onClick = onNavigateAudiobooksSearch) { Icon(Icons.Default.Search, "Search") }
                        IconButton(onClick = onNavigateSettings) { Icon(Icons.Default.Settings, "Settings") }
                    } else {
                        IconButton(onClick = onNavigateLibrarySearch) { Icon(Icons.Default.Search, "Search") }
                        IconButton(onClick = { showFilter = true }) { Icon(Icons.Default.FilterList, "Filter") }
                        IconButton(onClick = onNavigateSettings) { Icon(Icons.Default.Settings, "Settings") }
                    }
                },
            )
        },
        bottomBar = {
            val density = LocalDensity.current
            var boxWindowLeft by remember { mutableStateOf(0f) }
            var tab0X by remember { mutableStateOf(0f) }
            var tab0W by remember { mutableStateOf(0f) }
            var tab1X by remember { mutableStateOf(0f) }
            var tab1W by remember { mutableStateOf(0f) }
            val continuousPage = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, 1f)

            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(background)
                        .onGloballyPositioned { boxWindowLeft = it.boundsInWindow().left },
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(72.dp),
                    ) {
                        Text(
                            "Audiobooks",
                            modifier = Modifier
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { scope.launch { pagerState.animateScrollToPage(0) } }
                                .onGloballyPositioned {
                                    val b = it.boundsInWindow()
                                    tab0X = b.left
                                    tab0W = b.width
                                },
                            color = if (pagerState.currentPage == 0) accent else onSurfaceVariant,
                            fontSize = 16.sp,
                        )
                        Text(
                            "Library",
                            modifier = Modifier
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { scope.launch { pagerState.animateScrollToPage(1) } }
                                .onGloballyPositioned {
                                    val b = it.boundsInWindow()
                                    tab1X = b.left
                                    tab1W = b.width
                                },
                            color = if (pagerState.currentPage == 1) accent else onSurfaceVariant,
                            fontSize = 16.sp,
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                    ) {
                        if (tab0W > 0f && tab1W > 0f) {
                            val extraPx = with(density) { 8.dp.toPx() }
                            val ind0L = tab0X - boxWindowLeft - extraPx
                            val ind0W = tab0W + extraPx * 2
                            val ind1L = tab1X - boxWindowLeft - extraPx
                            val ind1W = tab1W + extraPx * 2
                            val indL = lerp(ind0L, ind1L, continuousPage)
                            val indW = lerp(ind0W, ind1W, continuousPage)
                            drawRoundRect(
                                color = accent,
                                topLeft = Offset(indL, 0f),
                                size = Size(indW, size.height),
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { page ->
            when (page) {
                0 -> AudiobooksContent(
                    books = localBooks,
                    positions = positions,
                    chapterCounts = chapterCounts,
                    downloadTimestamps = downloadTimestamps,
                    store = store,
                    filesDir = filesDir,
                    onNavigatePlayer = onNavigatePlayer,
                    onLongPress = { onLocalBookSelected(it) },
                )
                1 -> LibraryContent(
                    books = filteredBooks,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    downloadStates = downloadStates,
                    downloadProgress = downloadProgress,
                    onBookSelected = { onBookSelected(it) },
                    onDownload = { onDownload(it) },
                    onCancelDownload = { onCancelDownload(it) },
                )
                else -> Unit
            }
        }
    }

    if (showFilter) {
        FilterSheet(
            initialDate = filterDate,
            initialDuration = filterDuration,
            initialGenres = filterGenres,
            minYear = bookMinYear,
            maxYear = bookMaxYear,
            minDuration = bookMinDuration,
            maxDuration = bookMaxDuration,
            availableGenres = availableGenres,
            onApply = { d, dur, g -> onFilterChange(d, dur, g); showFilter = false },
            onDismiss = { showFilter = false },
        )
    }
}
