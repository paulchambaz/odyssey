package xyz.chambaz.odyssey.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import xyz.chambaz.odyssey.model.Audiobook
import xyz.chambaz.odyssey.model.Chapter

private fun formatMs(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    val s = (ms % 60_000) / 1_000
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    book: Audiobook,
    chapters: List<Chapter>,
    cover: ImageBitmap?,
    playing: Boolean,
    onPlayingChanged: (Boolean) -> Unit,
    sliderValue: Float,
    onSliderValueChanged: (Float) -> Unit,
    onSliderSeekFinished: () -> Unit,
    currentChapter: Int,
    onCurrentChapterChange: (Int) -> Unit,
    speedRaw: Float,
    onSpeedRawChange: (Float) -> Unit,
    chapterDurationMs: Long,
    timerEndMs: Long?,
    onTimerSet: (Long?) -> Unit,
    onReplay30: () -> Unit,
    onForward30: () -> Unit,
    onBack: () -> Unit,
    onCarMode: () -> Unit,
    onNavigateSettings: () -> Unit,
) {
    BackHandler { onBack() }
    var showChapters by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showTrackDetail by remember { mutableStateOf(false) }
    val speedDisplay = (speedRaw * 10).roundToInt() / 10f
    val accent = Accent

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timerEndMs) {
        while (timerEndMs != null && timerEndMs != -1L && timerEndMs!! > System.currentTimeMillis()) {
            delay(1_000)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val elapsedMs = (sliderValue * chapterDurationMs).toLong()
    val remainingMs = ((1f - sliderValue) * chapterDurationMs).toLong()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(book.title, maxLines = 1) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Cover — takes all remaining space, centered
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null) {
                        Image(
                            bitmap = cover,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            // Chapter row: entire row is one clickable button
            Surface(
                onClick = { showChapters = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                color = MaterialTheme.colorScheme.background,
                shape = CircleShape,
            ) {
                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatListBulleted,
                        "Chapters",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                    Text(
                        chapters.getOrNull(currentChapter)?.title ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(),
                    )
                }
            }

            // Seekbar + time info
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Slider(
                    value = sliderValue,
                    onValueChange = { onSliderValueChanged(it) },
                    onValueChangeFinished = onSliderSeekFinished,
                    modifier = Modifier.fillMaxWidth(),
                    thumb = {
                        Box(
                            Modifier
                                .size(22.dp)
                                .shadow(3.dp, CircleShape)
                                .clip(CircleShape)
                                .background(accent),
                        )
                    },
                    track = { state ->
                        SliderDefaults.Track(
                            sliderState = state,
                            modifier = Modifier.height(6.dp),
                            drawStopIndicator = {},
                        )
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (chapterDurationMs > 0) formatMs(elapsedMs) else "--:--",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (chapterDurationMs > 0) "–${formatMs(remainingMs)}" else "--:--",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 5-button controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                @Composable fun NavButton(onClick: () -> Unit, content: @Composable () -> Unit) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 36.dp),
                                onClick = onClick,
                            ),
                        contentAlignment = Alignment.Center,
                    ) { content() }
                }
                NavButton(onClick = {
                    if (elapsedMs < 10_000L && currentChapter > 0) {
                        onCurrentChapterChange(currentChapter - 1)
                    } else {
                        onSliderValueChanged(0f)
                        onSliderSeekFinished()
                    }
                }) {
                    Icon(Icons.Default.SkipPrevious, "Previous Chapter", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(4.dp))
                NavButton(onClick = onReplay30) {
                    Icon(Icons.Default.Replay30, "Back 30s", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(28.dp))
                FilledIconButton(onClick = { onPlayingChanged(!playing) }, modifier = Modifier.size(64.dp)) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "Pause" else "Play",
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.width(28.dp))
                NavButton(onClick = onForward30) {
                    Icon(Icons.Default.Forward30, "Forward 30s", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(4.dp))
                NavButton(onClick = { if (currentChapter < chapters.lastIndex) onCurrentChapterChange(currentChapter + 1) }) {
                    Icon(Icons.Default.SkipNext, "Next Chapter", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp))
                }
            }

            // 4-button action row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 24.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showSpeed = true }, modifier = Modifier.size(56.dp)) { Text("${speedDisplay}×", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { showTimer = true }, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Default.Timer,
                        "Timer",
                        tint = if (timerEndMs != null) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCarMode, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.DirectionsCar, "Car Mode", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { showTrackDetail = true }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showChapters) {
        ModalBottomSheet(
            onDismissRequest = { showChapters = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    "Chapters",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn {
                    itemsIndexed(chapters) { index, chapter ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    chapter.title,
                                    color = if (index == currentChapter) Accent else MaterialTheme.colorScheme.onBackground,
                                    fontWeight = if (index == currentChapter) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.clickable { onCurrentChapterChange(index); showChapters = false },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (showSpeed) {
        val presets = listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
        ModalBottomSheet(
            onDismissRequest = { showSpeed = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Speed", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text("${speedDisplay}×", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val buttonGray = lerp(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurfaceVariant, 0.5f)
                    OutlinedIconButton(
                        onClick = { onSpeedRawChange((speedDisplay - 0.5f).coerceIn(0.5f, 3.5f)) },
                        border = BorderStroke(1.dp, buttonGray),
                    ) {
                        Icon(Icons.Default.Remove, "-", tint = buttonGray)
                    }
                    Slider(
                        value = speedRaw,
                        onValueChange = { onSpeedRawChange(it) },
                        valueRange = 0.5f..3.5f,
                        modifier = Modifier.weight(1f),
                        thumb = {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .shadow(3.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(Accent),
                            )
                        },
                        track = { state ->
                            Box(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                                SliderDefaults.Track(sliderState = state, modifier = Modifier.fillMaxSize(), drawStopIndicator = {})
                                Canvas(modifier = Modifier.fillMaxWidth().requiredHeight(20.dp)) {
                                    val fraction = (1.0f - 0.5f) / (3.5f - 0.5f)
                                    val x = fraction * size.width
                                    drawLine(
                                        color = Color.Gray.copy(alpha = 0.5f),
                                        start = Offset(x, 0f),
                                        end = Offset(x, size.height),
                                        strokeWidth = 2.dp.toPx(),
                                    )
                                }
                            }
                        },
                    )
                    OutlinedIconButton(
                        onClick = { onSpeedRawChange((speedDisplay + 0.5f).coerceIn(0.5f, 3.5f)) },
                        border = BorderStroke(1.dp, buttonGray),
                    ) {
                        Icon(Icons.Default.Add, "+", tint = buttonGray)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { preset ->
                        val selected = speedDisplay == preset
                        Surface(
                            onClick = { onSpeedRawChange(preset) },
                            modifier = Modifier.weight(1f).height(32.dp),
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "${preset}×",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTimer) {
        val options = listOf("Off", "5 min", "10 min", "15 min", "30 min", "45 min", "60 min", "End of chapter")
        val minutesMap = mapOf("5 min" to 5L, "10 min" to 10L, "15 min" to 15L, "30 min" to 30L, "45 min" to 45L, "60 min" to 60L)
        val currentLabel = when {
            timerEndMs == null -> "Off"
            timerEndMs == -1L -> "End of chapter"
            else -> options.firstOrNull { minutesMap[it] != null && timerEndMs == System.currentTimeMillis() + minutesMap[it]!! * 60_000 } ?: "Off"
        }
        ModalBottomSheet(
            onDismissRequest = { showTimer = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(bottom = 40.dp)) {
                val timerDisplay = when {
                    timerEndMs == null -> null
                    timerEndMs == -1L -> "end of chapter"
                    else -> "–${formatMs((timerEndMs - currentTimeMs).coerceAtLeast(0L))}"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Sleep Timer", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    if (timerDisplay != null) Text(timerDisplay, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                }
                options.forEach { opt ->
                    ListItem(
                        headlineContent = { Text(opt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground) },
                        leadingContent = { RadioButton(selected = opt == currentLabel, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable {
                            when (opt) {
                                "Off" -> onTimerSet(null)
                                "End of chapter" -> onTimerSet(-1L)
                                else -> {
                                    val mins = minutesMap[opt] ?: return@clickable
                                    onTimerSet(System.currentTimeMillis() + mins * 60_000L)
                                }
                            }
                            showTimer = false
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (showTrackDetail) {
        ModalBottomSheet(
            onDismissRequest = { showTrackDetail = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(book.author, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (book.date != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(book.date.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!book.genres.isNullOrEmpty()) {
                    Text(book.genres.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (chapterDurationMs > 0) {
                    Text(
                        "${formatMs(elapsedMs)} / ${formatMs(chapterDurationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!book.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(book.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

@Composable
fun CarModeScreen(
    book: Audiobook,
    chapters: List<Chapter>,
    cover: ImageBitmap?,
    playing: Boolean,
    onPlayingChanged: (Boolean) -> Unit,
    sliderValue: Float,
    currentChapter: Int,
    onCurrentChapterChange: (Int) -> Unit,
    chapterDurationMs: Long,
    onReplay30: () -> Unit,
    onBack: () -> Unit,
) {
    val activity = LocalContext.current as Activity
    DisposableEffect(Unit) {
        activity.setShowWhenLocked(true)
        activity.setTurnScreenOn(true)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity.setShowWhenLocked(false)
            activity.setTurnScreenOn(false)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler { onBack() }

    val remainingMs = ((1f - sliderValue) * chapterDurationMs).toLong()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(64.dp),
        ) {
            Icon(Icons.Default.Close, "Exit Car Mode", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    Image(
                        bitmap = cover,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.height(96.dp))
            Text(
                book.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(progress = { sliderValue }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    if (chapterDurationMs > 0) "–${formatMs(remainingMs)}" else "--:--",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(28.dp))
            val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
            val rippleAlpha = if (isLight) 0.20f else 0.10f
            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val replaySource = remember { MutableInteractionSource() }
                val replayPressed by replaySource.collectIsPressedAsState()
                val replayScale by animateFloatAsState(if (replayPressed) 0.82f else 1f, label = "replay")
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(replayScale)
                        .clickable(interactionSource = replaySource, indication = ripple(bounded = false, radius = 44.dp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = rippleAlpha)), onClick = onReplay30),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Replay30, "Back 30s", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(64.dp))
                }
                FilledIconButton(
                    onClick = { onPlayingChanged(!playing) },
                    modifier = Modifier.size(112.dp),
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "Pause" else "Play",
                        modifier = Modifier.size(72.dp),
                    )
                }
                val skipSource = remember { MutableInteractionSource() }
                val skipPressed by skipSource.collectIsPressedAsState()
                val skipScale by animateFloatAsState(if (skipPressed) 0.82f else 1f, label = "skip")
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(skipScale)
                        .clickable(
                            interactionSource = skipSource,
                            indication = ripple(bounded = false, radius = 44.dp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = rippleAlpha)),
                            onClick = { if (currentChapter < chapters.lastIndex) onCurrentChapterChange(currentChapter + 1) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.SkipNext, "Next Chapter", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(64.dp))
                }
            }
        }
    }
}
