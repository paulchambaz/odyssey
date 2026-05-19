package xyz.chambaz.odyssey

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.LoudnessEnhancer
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import kotlin.math.sqrt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import xyz.chambaz.odyssey.api.IliadApi
import xyz.chambaz.odyssey.model.Audiobook
import xyz.chambaz.odyssey.model.Chapter
import xyz.chambaz.odyssey.model.Credentials
import xyz.chambaz.odyssey.model.Position
import xyz.chambaz.odyssey.player.parseInfoYml
import xyz.chambaz.odyssey.store.DownloadState
import xyz.chambaz.odyssey.store.SharedPrefsAdapter
import xyz.chambaz.odyssey.store.Store
import xyz.chambaz.odyssey.ui.*
import java.io.File

private fun isCellular(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
}

private fun uriToPath(uri: Uri): String? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":", limit = 2)
        val type = parts[0]
        val sub = parts.getOrElse(1) { "" }
        val base = if (type == "primary") Environment.getExternalStorageDirectory().absolutePath
                   else "/storage/$type"
        if (sub.isEmpty()) base else "$base/$sub"
    } catch (_: Exception) { null }
}

private fun extractTarGz(archive: File, destDir: File) {
    destDir.mkdirs()
    GzipCompressorInputStream(archive.inputStream().buffered()).use { gzip ->
        TarArchiveInputStream(gzip).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val out = File(destDir, entry.name)
                if (entry.isDirectory) out.mkdirs()
                else { out.parentFile?.mkdirs(); out.outputStream().use { tar.copyTo(it) } }
                entry = tar.nextEntry
            }
        }
    }
}

enum class Screen { Login, Main, LibrarySearch, AudiobooksSearch, Player, CarMode, Settings }

const val NOTIF_CHANNEL_ID = "downloads"
const val ACTION_CANCEL_DOWNLOAD = "xyz.chambaz.odyssey.CANCEL_DOWNLOAD"
const val NOTIF_CHANNEL_PLAYBACK_ID = "playback"
const val ACTION_PLAYER_TOGGLE = "xyz.chambaz.odyssey.PLAYER_TOGGLE"
const val ACTION_PLAYER_REPLAY30 = "xyz.chambaz.odyssey.PLAYER_REPLAY30"
const val ACTION_PLAYER_FORWARD30 = "xyz.chambaz.odyssey.PLAYER_FORWARD30"
const val NOTIF_ID_PLAYBACK = 1001

class MainActivity : AppCompatActivity() {
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val requestBtPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(NOTIF_CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(NOTIF_CHANNEL_PLAYBACK_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestBtPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        }

        val store = Store(SharedPrefsAdapter(this))

        setContent {
            var theme by remember { mutableStateOf(store.loadTheme()) }
            var accentIndex by remember { mutableIntStateOf(store.loadAccentColorIndex()) }
            OdysseyTheme(theme = theme, accentIndex = accentIndex) {
                val initialCreds = store.loadCredentials()
                var screen by remember { mutableStateOf(if (initialCreds != null) Screen.Main else Screen.Login) }
                var settingsReturnScreen by remember { mutableStateOf(Screen.Main) }
                var api by remember { mutableStateOf(initialCreds?.let { IliadApi(it) }) }
                var mainPage by remember { mutableIntStateOf(0) }
                var books by remember { mutableStateOf<List<Audiobook>>(emptyList()) }
                var localAudiobooks by remember { mutableStateOf<List<Audiobook>>(emptyList()) }

                LaunchedEffect(Unit) {
                    localAudiobooks = withContext(Dispatchers.IO) {
                        store.loadLocalAudiobooks(filesDir).map { it.second }
                    }
                }
                var playerBook by remember { mutableStateOf<Audiobook?>(null) }
                var playerChapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
                var playing by remember { mutableStateOf(false) }
                var sliderValue by remember { mutableStateOf(0f) }
                var currentChapter by remember { mutableIntStateOf(0) }
                var speedRaw by remember { mutableStateOf(store.loadPlaybackSpeed()) }
                var chapterDurationMs by remember { mutableLongStateOf(0L) }
                var chapterDurationsMs by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
                var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
                var timerEndMs by remember { mutableStateOf<Long?>(null) }
                var originalTimerDurationMs by remember { mutableStateOf<Long?>(null) }
                var timerGraceEndMs by remember { mutableStateOf<Long?>(null) }
                var positionConflict by remember { mutableStateOf<Triple<String, Position, Position>?>(null) }
                var volumeNormEnabled by remember { mutableStateOf(store.loadVolumeNormalization()) }
                val loudnessEnhancer = remember { mutableStateOf<LoudnessEnhancer?>(null) }
                var isRefreshing by remember { mutableStateOf(false) }
                var connectedBtDevices by remember { mutableStateOf<Set<String>>(emptySet()) }
                var carDevices by remember { mutableStateOf(store.loadCarDevices()) }
                var pairedBtDevices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
                val bookMinYear = remember(books) { (books.mapNotNull { it.date }.minOrNull()?.minus(1) ?: 1900).toFloat() }
                val bookMaxYear = remember(books) { (books.mapNotNull { it.date }.maxOrNull()?.plus(1) ?: 2030).toFloat() }
                val bookMinDuration = remember(books) { maxOf(0f, (books.mapNotNull { it.duration }.minOrNull()?.div(3_600f) ?: 0f) - 1f) }
                val bookMaxDuration = remember(books) { (books.mapNotNull { it.duration }.maxOrNull()?.div(3_600f) ?: 100f) + 1f }
                var filterDate by remember { mutableStateOf(bookMinYear..bookMaxYear) }
                LaunchedEffect(bookMinYear, bookMaxYear) { filterDate = bookMinYear..bookMaxYear }
                var filterDuration by remember { mutableStateOf(bookMinDuration..bookMaxDuration) }
                LaunchedEffect(bookMinDuration, bookMaxDuration) { filterDuration = bookMinDuration..bookMaxDuration }
                var filterGenres by remember { mutableStateOf<List<String>>(emptyList()) }
                val filteredBooks = remember(books, filterDate, filterDuration, filterGenres, bookMinYear, bookMaxYear, bookMinDuration, bookMaxDuration) {
                    val dateActive = filterDate.start > bookMinYear || filterDate.endInclusive < bookMaxYear
                    val durationActive = filterDuration.start > bookMinDuration || filterDuration.endInclusive < bookMaxDuration
                    val genresActive = filterGenres.isNotEmpty()
                    if (!dateActive && !durationActive && !genresActive) books
                    else books.filter { book ->
                        val dateMatch = dateActive && book.date != null &&
                            book.date in filterDate.start.toInt()..filterDate.endInclusive.toInt()
                        val durationMatch = durationActive && book.duration != null &&
                            book.duration / 3_600f in filterDuration
                        val genreMatch = genresActive && book.genres?.any { it in filterGenres } == true
                        dateMatch || durationMatch || genreMatch
                    }
                }
                var downloadStates by remember { mutableStateOf<Map<String, DownloadState>>(emptyMap()) }
                LaunchedEffect(books) {
                    downloadStates = books.associate { book ->
                        val state = store.loadDownloadState(book.hash)
                        val resolved = if (state == DownloadState.IN_PROGRESS) DownloadState.REMOTE else state
                        if (resolved != state) store.saveDownloadState(book.hash, resolved)
                        book.hash to resolved
                    }
                }
                fun setDownloadState(hash: String, state: DownloadState) {
                    downloadStates = downloadStates + (hash to state)
                    store.saveDownloadState(hash, state)
                }
                val localBooks = remember(localAudiobooks, books) {
                    localAudiobooks.map { local ->
                        val remote = books.find { it.hash == local.hash }
                        local.copy(duration = remote?.duration ?: store.loadDuration(local.hash))
                    }
                }
                var positions by remember { mutableStateOf<Map<String, Position?>>(emptyMap()) }
                LaunchedEffect(localBooks, screen) {
                    positions = localBooks.associate { it.hash to store.loadPosition(it.hash) }
                    if (screen == Screen.Main) {
                        while (isActive) {
                            delay(30_000)
                            positions = localBooks.associate { it.hash to store.loadPosition(it.hash) }
                        }
                    }
                }
                var covers by remember { mutableStateOf<Map<String, ImageBitmap?>>(emptyMap()) }
                var chapterCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
                var downloadTimestamps by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
                var descriptions by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
                LaunchedEffect(localBooks) {
                    data class BookAssets(val hash: String, val cover: ImageBitmap?, val chapterCount: Int, val downloadTime: Long, val description: String?)
                    val loaded = withContext(Dispatchers.IO) {
                        localBooks.map { book ->
                            val bookDir = store.libraryDir(book.hash, filesDir)
                            val infoFile = File(bookDir, "info.yml")
                            val lines = if (infoFile.exists()) infoFile.readLines() else emptyList()
                            val bitmap = lines.firstOrNull { it.startsWith("cover:") }
                                ?.removePrefix("cover:")?.trim()?.trim('"')
                                ?.let { BitmapFactory.decodeFile(File(bookDir, it).absolutePath)?.asImageBitmap() }
                            val count = lines.count { it.trim().startsWith("- title:") }
                            val desc = lines.firstOrNull { it.startsWith("description:") }
                                ?.removePrefix("description:")?.trim()?.trim('"')
                            BookAssets(book.hash, bitmap, count, bookDir.lastModified(), desc)
                        }
                    }
                    covers = loaded.associate { it.hash to it.cover }
                    chapterCounts = loaded.associate { it.hash to it.chapterCount }
                    downloadTimestamps = loaded.associate { it.hash to it.downloadTime }
                    descriptions = loaded.associate { it.hash to it.description }
                }
                var downloadProgress by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
                var selectedBook by remember { mutableStateOf<Audiobook?>(null) }
                var pendingCellularDownload by remember { mutableStateOf<Audiobook?>(null) }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                var downloadLocation by remember { mutableStateOf(store.loadDownloadLocation()) }
                val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) {
                        val newPath = uriToPath(uri)
                        if (newPath != null) {
                            scope.launch(Dispatchers.IO) {
                                val oldLoc = store.loadDownloadLocation()
                                val oldBase = if (oldLoc.isEmpty()) File(filesDir, "library") else File(oldLoc)
                                val newBase = File(newPath)
                                newBase.mkdirs()
                                oldBase.listFiles()?.forEach { f ->
                                    val dest = File(newBase, f.name)
                                    if (!f.renameTo(dest)) {
                                        if (f.isDirectory) f.copyRecursively(dest, overwrite = true)
                                        else f.copyTo(dest, overwrite = true)
                                        f.deleteRecursively()
                                    }
                                }
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                )
                                store.saveDownloadLocation(newPath)
                                withContext(Dispatchers.Main) { downloadLocation = newPath }
                            }
                        }
                    }
                }

                // ── MediaPlayer ──────────────────────────────────────────────────────────

                val mediaPlayer = remember { MediaPlayer() }

                suspend fun syncPosition(hash: String) {
                    val pos = Position(
                        chapterIndex = currentChapter,
                        chapterPosition = mediaPlayer.currentPosition.toLong(),
                        clientTimestamp = System.currentTimeMillis() / 1000L,
                    )
                    store.savePosition(hash, pos)
                    try {
                        api?.putPosition(hash, pos)
                        store.saveServerPosition(hash, pos)
                    } catch (_: Exception) {}
                }

                DisposableEffect(Unit) {
                    onDispose {
                        if (playing) {
                            val pos = Position(currentChapter, mediaPlayer.currentPosition.toLong(), System.currentTimeMillis() / 1000L)
                            playerBook?.hash?.let { store.savePosition(it, pos) }
                        }
                        loudnessEnhancer.value?.release()
                        mediaPlayer.release()
                    }
                }

                // Chapter setup: prepare MediaPlayer whenever book/chapter/chapters change
                LaunchedEffect(playerBook, currentChapter, playerChapters) {
                    val book = playerBook ?: return@LaunchedEffect
                    if (playerChapters.isEmpty()) return@LaunchedEffect
                    val chapter = playerChapters.getOrNull(currentChapter) ?: return@LaunchedEffect
                    val file = File(store.libraryDir(book.hash, filesDir), chapter.path)
                    if (!file.exists()) return@LaunchedEffect

                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(file.absolutePath)
                    mediaPlayer.prepare()
                    chapterDurationMs = mediaPlayer.duration.toLong().coerceAtLeast(0L)
                    chapterDurationsMs = chapterDurationsMs + (currentChapter to chapterDurationMs)
                    try { mediaPlayer.playbackParams = PlaybackParams().setSpeed(speedRaw).setPitch(1.0f) } catch (_: Exception) {}

                    loudnessEnhancer.value?.release()
                    loudnessEnhancer.value = if (volumeNormEnabled) {
                        LoudnessEnhancer(mediaPlayer.audioSessionId).also {
                            it.setTargetGain(store.loadLoudnessGain())
                            it.enabled = true
                        }
                    } else null

                    val seekMs = pendingSeekMs ?: 0L
                    pendingSeekMs = null
                    Log.d("Odyssey/Seek", "ch=$currentChapter seekMs=$seekMs durationMs=$chapterDurationMs")
                    mediaPlayer.seekTo(seekMs.toInt())
                    sliderValue = if (chapterDurationMs > 0) seekMs.toFloat() / chapterDurationMs else 0f

                    if (playing) mediaPlayer.start()

                    mediaPlayer.setOnCompletionListener {
                        if (currentChapter < playerChapters.lastIndex) {
                            if (timerEndMs == -1L) {
                                timerEndMs = null
                                scope.launch { syncPosition(book.hash) }
                                currentChapter++
                                pendingSeekMs = 0L
                                playing = false
                                if (store.loadShakeToExtend()) {
                                    timerGraceEndMs = System.currentTimeMillis() + 60_000L
                                } else {
                                    originalTimerDurationMs = null
                                }
                            } else {
                                scope.launch { syncPosition(book.hash) }
                                currentChapter++
                                pendingSeekMs = 0L
                            }
                        } else {
                            playing = false
                            scope.launch { syncPosition(book.hash) }
                        }
                    }
                }

                // Foreground service: keep process alive during background playback and grace window
                LaunchedEffect(playing, playerBook, timerGraceEndMs) {
                    if ((playing || timerGraceEndMs != null) && playerBook != null) {
                        val intent = Intent(context, PlaybackService::class.java)
                            .putExtra("title", playerBook!!.title)
                        context.startForegroundService(intent)
                    } else {
                        context.stopService(Intent(context, PlaybackService::class.java))
                    }
                }

                // Play/pause: rewind on resume (if paused long enough), pause + sync on stop
                LaunchedEffect(playing) {
                    if (playing) {
                        if (chapterDurationMs > 0 && !mediaPlayer.isPlaying) {
                            mediaPlayer.start()
                        }
                    } else {
                        if (mediaPlayer.isPlaying) {
                            mediaPlayer.pause()
                            playerBook?.hash?.let { h -> scope.launch { syncPosition(h) } }
                        }
                    }
                }

                // Slider update (500ms) + position sync (30s) during playback
                LaunchedEffect(playing) {
                    if (!playing) return@LaunchedEffect
                    val sliderJob = launch {
                        while (isActive) {
                            delay(500)
                            if (chapterDurationMs > 0 && mediaPlayer.isPlaying)
                                sliderValue = mediaPlayer.currentPosition.toFloat() / chapterDurationMs
                        }
                    }
                    val syncJob = launch {
                        while (isActive) {
                            delay(30_000)
                            playerBook?.hash?.let { syncPosition(it) }
                        }
                    }
                    sliderJob.join()
                    syncJob.join()
                }

                // Speed: apply to MediaPlayer + persist
                LaunchedEffect(speedRaw) {
                    store.savePlaybackSpeed(speedRaw)
                    if (chapterDurationMs > 0) {
                        try { mediaPlayer.playbackParams = PlaybackParams().setSpeed(speedRaw).setPitch(1.0f) } catch (_: Exception) {}
                        if (mediaPlayer.isPlaying && !playing) playing = true
                    }
                }

                // Volume normalization: reattach or release on toggle
                LaunchedEffect(volumeNormEnabled) {
                    if (chapterDurationMs == 0L) return@LaunchedEffect
                    loudnessEnhancer.value?.release()
                    loudnessEnhancer.value = if (volumeNormEnabled) {
                        LoudnessEnhancer(mediaPlayer.audioSessionId).also {
                            it.setTargetGain(store.loadLoudnessGain())
                            it.enabled = true
                        }
                    } else null
                }

                // Sleep timer
                LaunchedEffect(timerEndMs) {
                    val end = timerEndMs ?: return@LaunchedEffect
                    if (end == -1L) return@LaunchedEffect  // "End of chapter" handled in completion listener
                    val remaining = end - System.currentTimeMillis()
                    if (remaining > 0) delay(remaining)
                    timerEndMs = null
                    if (playing) { playing = false; playerBook?.hash?.let { syncPosition(it) } }
                    if (store.loadShakeToExtend() && originalTimerDurationMs != null) {
                        timerGraceEndMs = System.currentTimeMillis() + 60_000L
                    } else {
                        originalTimerDurationMs = null
                    }
                }

                // Grace window: 60s to shake; if no shake, stay paused
                LaunchedEffect(timerGraceEndMs) {
                    val end = timerGraceEndMs ?: return@LaunchedEffect
                    delay((end - System.currentTimeMillis()).coerceAtLeast(0L))
                    if (timerGraceEndMs != null) {
                        timerGraceEndMs = null
                        originalTimerDurationMs = null
                    }
                }

                // Accelerometer shake detection during grace window
                DisposableEffect(timerGraceEndMs) {
                    if (timerGraceEndMs == null) return@DisposableEffect onDispose {}
                    val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                    val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(e: SensorEvent) {
                            val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
                            val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                            if (magnitude >= 15f) {
                                val dur = originalTimerDurationMs ?: return
                                timerEndMs = if (dur == -1L) -1L else System.currentTimeMillis() + dur
                                timerGraceEndMs = null
                                playing = true
                            }
                        }
                        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                    }
                    sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                    onDispose { sm.unregisterListener(listener) }
                }

                // BT connected device tracking for auto car mode
                DisposableEffect(Unit) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@DisposableEffect onDispose {}
                    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@DisposableEffect onDispose {}
                    val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                        ?: return@DisposableEffect onDispose {}
                    btAdapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                            connectedBtDevices = proxy.connectedDevices.map { it.address }.toSet()
                            btAdapter.closeProfileProxy(profile, proxy)
                        }
                        override fun onServiceDisconnected(profile: Int) {}
                    }, BluetoothProfile.A2DP)
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent) {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            val addr = device?.address ?: return
                            connectedBtDevices = when (intent.action) {
                                BluetoothDevice.ACTION_ACL_CONNECTED -> connectedBtDevices + addr
                                BluetoothDevice.ACTION_ACL_DISCONNECTED -> connectedBtDevices - addr
                                else -> connectedBtDevices
                            }
                        }
                    }
                    val filter = IntentFilter().apply {
                        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    }
                    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
                    onDispose { context.unregisterReceiver(receiver) }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                        pairedBtDevices = btAdapter?.bondedDevices?.map { it.name to it.address } ?: emptyList()
                    }
                }

                // Background sync
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_PAUSE && playing) {
                            scope.launch { playerBook?.hash?.let { syncPosition(it) } }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // Replay30 / Forward30 / NextChapter action lambdas (captured by notification receivers)
                val replay30Action = remember { mutableStateOf<() -> Unit>({}) }
                val forward30Action = remember { mutableStateOf<() -> Unit>({}) }

                val onReplay30: () -> Unit = {
                    if (chapterDurationMs > 0) {
                        val seekMs = (mediaPlayer.currentPosition - 30_000L).coerceAtLeast(0L)
                        mediaPlayer.seekTo(seekMs.toInt())
                        sliderValue = seekMs.toFloat() / chapterDurationMs
                        playerBook?.hash?.let { h -> scope.launch { syncPosition(h) } }
                    }
                }
                val onForward30: () -> Unit = {
                    if (chapterDurationMs > 0) {
                        val seekMs = (mediaPlayer.currentPosition + 30_000L).coerceAtMost(chapterDurationMs)
                        mediaPlayer.seekTo(seekMs.toInt())
                        sliderValue = seekMs.toFloat() / chapterDurationMs
                        playerBook?.hash?.let { h -> scope.launch { syncPosition(h) } }
                    }
                }
                val onNextChapterFromNotif: () -> Unit = {
                    if (currentChapter < playerChapters.lastIndex) {
                        playerBook?.hash?.let { h -> scope.launch { syncPosition(h) } }
                        pendingSeekMs = 0L
                        currentChapter++
                    }
                }

                SideEffect {
                    replay30Action.value = onReplay30
                    forward30Action.value = onNextChapterFromNotif
                }

                // ── Broadcast receivers ───────────────────────────────────────────────

                val cancelCallbacks = remember { mutableMapOf<String, () -> Unit>() }
                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent) {
                            val hash = intent.getStringExtra("hash") ?: return
                            cancelCallbacks[hash]?.invoke()
                        }
                    }
                    ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_CANCEL_DOWNLOAD), ContextCompat.RECEIVER_NOT_EXPORTED)
                    onDispose { context.unregisterReceiver(receiver) }
                }

                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent) { playing = !playing }
                    }
                    ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_PLAYER_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED)
                    onDispose { context.unregisterReceiver(receiver) }
                }

                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent) { replay30Action.value() }
                    }
                    ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_PLAYER_REPLAY30), ContextCompat.RECEIVER_NOT_EXPORTED)
                    onDispose { context.unregisterReceiver(receiver) }
                }

                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent) { forward30Action.value() }
                    }
                    ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_PLAYER_FORWARD30), ContextCompat.RECEIVER_NOT_EXPORTED)
                    onDispose { context.unregisterReceiver(receiver) }
                }

                val mediaSession = remember { MediaSession(context, "OdysseyPlayer") }
                DisposableEffect(Unit) {
                    onDispose {
                        mediaSession.isActive = false
                        mediaSession.release()
                        notificationManager.cancel(NOTIF_ID_PLAYBACK)
                    }
                }

                LaunchedEffect(playing, playerBook, sliderValue, currentChapter) {
                    val book = playerBook
                    if (book != null && playing) {
                        mediaSession.isActive = true
                        val currentPositionMs = (sliderValue * chapterDurationMs).toLong()
                        mediaSession.setPlaybackState(
                            PlaybackState.Builder()
                                .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO)
                                .setState(
                                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                                    currentPositionMs,
                                    if (playing) speedRaw else 0f,
                                ).build()
                        )
                        val coverBitmap = withContext(Dispatchers.IO) {
                            val bookDir = store.libraryDir(book.hash, filesDir)
                            val infoFile = File(bookDir, "info.yml")
                            if (!infoFile.exists()) return@withContext null
                            val rel = infoFile.readLines()
                                .firstOrNull { it.startsWith("cover:") }
                                ?.removePrefix("cover:")?.trim()?.trim('"') ?: return@withContext null
                            BitmapFactory.decodeFile(File(bookDir, rel).absolutePath)
                        }
                        val chapterTitle = playerChapters.getOrNull(currentChapter)?.title ?: book.author
                        mediaSession.setMetadata(
                            MediaMetadata.Builder()
                                .putString(MediaMetadata.METADATA_KEY_TITLE, book.title)
                                .putString(MediaMetadata.METADATA_KEY_ARTIST, chapterTitle)
                                .putLong(MediaMetadata.METADATA_KEY_DURATION, chapterDurationMs)
                                .apply { if (coverBitmap != null) putBitmap(MediaMetadata.METADATA_KEY_ART, coverBitmap) }
                                .build()
                        )
                        val tapIntent = PendingIntent.getActivity(
                            context, 0,
                            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        val replay30Intent = PendingIntent.getBroadcast(
                            context, 0,
                            Intent(ACTION_PLAYER_REPLAY30),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        val toggleIntent = PendingIntent.getBroadcast(
                            context, 1,
                            Intent(ACTION_PLAYER_TOGGLE),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        val nextChapterIntent = PendingIntent.getBroadcast(
                            context, 2,
                            Intent(ACTION_PLAYER_FORWARD30),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        val playPauseIcon = if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow
                        val notif = Notification.Builder(context, NOTIF_CHANNEL_PLAYBACK_ID)
                            .setSmallIcon(R.drawable.ic_odyssey)
                            .setContentTitle(book.title)
                            .setContentText(chapterTitle)
                            .setContentIntent(tapIntent)
                            .setOngoing(playing)
                            .setOnlyAlertOnce(true)
                            .apply { if (coverBitmap != null) setLargeIcon(coverBitmap) }
                            .addAction(
                                Notification.Action.Builder(
                                    android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_replay_30),
                                    "Back 30s", replay30Intent,
                                ).build()
                            )
                            .addAction(
                                Notification.Action.Builder(
                                    android.graphics.drawable.Icon.createWithResource(context, playPauseIcon),
                                    if (playing) "Pause" else "Play", toggleIntent,
                                ).build()
                            )
                            .addAction(
                                Notification.Action.Builder(
                                    android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_skip_next),
                                    "Next Chapter", nextChapterIntent,
                                ).build()
                            )
                            .setStyle(
                                Notification.MediaStyle()
                                    .setMediaSession(mediaSession.sessionToken)
                                    .setShowActionsInCompactView(0, 1, 2)
                            )
                            .build()
                        notificationManager.notify(NOTIF_ID_PLAYBACK, notif)
                    } else {
                        mediaSession.isActive = false
                        notificationManager.cancel(NOTIF_ID_PLAYBACK)
                    }
                }

                // ── Navigation helpers ────────────────────────────────────────────────

                fun navigateToPlayer(book: Audiobook) {
                    val targetScreen = if (store.loadAutoCarMode() && carDevices.any { it in connectedBtDevices }) Screen.CarMode else Screen.Player
                    if (playing && playerBook?.hash == book.hash) {
                        screen = targetScreen
                        return
                    }
                    playerBook = book.copy(description = descriptions[book.hash] ?: book.description)
                    playerChapters = emptyList()
                    currentChapter = 0
                    sliderValue = 0f
                    chapterDurationMs = 0L
                    chapterDurationsMs = emptyMap()
                    pendingSeekMs = null
                    playing = true
                    screen = targetScreen
                    scope.launch {
                        val chapters = withContext(Dispatchers.IO) {
                            val f = File(store.libraryDir(book.hash, filesDir), "info.yml")
                            if (f.exists()) parseInfoYml(f) else emptyList()
                        }
                        val clamp = { idx: Int -> idx.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)) }

                        // Resolve position before setting playerChapters so chapter setup fires once
                        try {
                            val serverPos = api!!.getPosition(book.hash)
                            val localPos = store.loadPosition(book.hash)
                            val lastServerPos = store.loadServerPosition(book.hash)
                            val serverTs = serverPos.clientTimestamp ?: 0L
                            val localTs = localPos?.clientTimestamp ?: 0L
                            val lastServerTs = lastServerPos?.clientTimestamp ?: -1L
                            Log.d("Odyssey/Merge", "S=$serverTs L=$localTs LS=$lastServerTs | server=(ch${serverPos.chapterIndex},${serverPos.chapterPosition}ms) local=(ch${localPos?.chapterIndex},${localPos?.chapterPosition}ms) lastServer=(ch${lastServerPos?.chapterIndex},${lastServerPos?.chapterPosition}ms)")
                            val nearlyIdentical = localPos != null &&
                                serverPos.chapterIndex == localPos.chapterIndex &&
                                kotlin.math.abs(serverPos.chapterPosition - localPos.chapterPosition) < 30_000L
                            if (nearlyIdentical) {
                                val earlier = if (serverPos.chapterPosition <= localPos!!.chapterPosition) serverPos else localPos
                                val toSync = earlier.copy(clientTimestamp = System.currentTimeMillis() / 1000L)
                                currentChapter = clamp(earlier.chapterIndex)
                                pendingSeekMs = earlier.chapterPosition
                                store.savePosition(book.hash, toSync)
                                try {
                                    api!!.putPosition(book.hash, toSync)
                                    store.saveServerPosition(book.hash, toSync)
                                } catch (_: Exception) { store.saveServerPosition(book.hash, serverPos) }
                            } else when {
                                serverTs == lastServerTs && localTs > serverTs -> {
                                    currentChapter = clamp(localPos!!.chapterIndex)
                                    pendingSeekMs = localPos.chapterPosition
                                    try {
                                        api!!.putPosition(book.hash, localPos)
                                        store.saveServerPosition(book.hash, localPos)
                                    } catch (_: Exception) {}
                                }
                                serverTs > localTs -> {
                                    currentChapter = clamp(serverPos.chapterIndex)
                                    pendingSeekMs = serverPos.chapterPosition
                                    store.savePosition(book.hash, serverPos)
                                    store.saveServerPosition(book.hash, serverPos)
                                }
                                localTs > serverTs -> {
                                    store.saveServerPosition(book.hash, serverPos)
                                    currentChapter = clamp(localPos!!.chapterIndex)
                                    pendingSeekMs = localPos.chapterPosition
                                    positionConflict = Triple(book.hash, serverPos, localPos)
                                }
                                else -> {
                                    currentChapter = clamp(serverPos.chapterIndex)
                                    pendingSeekMs = serverPos.chapterPosition
                                    store.savePosition(book.hash, serverPos)
                                    store.saveServerPosition(book.hash, serverPos)
                                }
                            }
                        } catch (_: Exception) {
                            val cached = store.loadPosition(book.hash)
                            if (cached != null) {
                                currentChapter = clamp(cached.chapterIndex)
                                pendingSeekMs = cached.chapterPosition
                            }
                        }

                        val rewindMs = store.loadRewindOnResume() * 1000L
                        pendingSeekMs = pendingSeekMs?.let { (it - rewindMs).coerceAtLeast(0L) }

                        // Set chapters last: chapter setup fires once with correct position already set
                        playerChapters = chapters
                    }
                }

                fun selectBook(book: Audiobook) = scope.launch {
                    try { selectedBook = api!!.getAudiobook(book.hash) } catch (_: Exception) {}
                }

                fun onDelete(book: Audiobook) = scope.launch {
                    setDownloadState(book.hash, DownloadState.REMOTE)
                    downloadProgress = downloadProgress - book.hash
                    val bookDir = store.libraryDir(book.hash, filesDir)
                    bookDir.deleteRecursively()
                    File(bookDir.parentFile ?: File(filesDir, "library"), "${book.hash}.tar.gz").delete()
                }

                fun startDownload(book: Audiobook) {
                    var userCancelled = false
                    val job = scope.launch {
                        val destDir = store.libraryDir(book.hash, filesDir)
                        val archiveFile = File(destDir.parentFile ?: File(filesDir, "library"), "${book.hash}.tar.gz")
                        val notifId = book.hash.hashCode()
                        val cancelIntent = PendingIntent.getBroadcast(
                            context, notifId,
                            Intent(ACTION_CANCEL_DOWNLOAD).putExtra("hash", book.hash),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.stat_sys_download)
                            .setContentTitle(book.title)
                            .setOngoing(true)
                            .setOnlyAlertOnce(true)
                            .addAction(0, "Cancel", cancelIntent)

                        setDownloadState(book.hash, DownloadState.IN_PROGRESS)
                        notif.setContentText("Waiting for archive…").setProgress(0, 0, true)
                        notificationManager.notify(notifId, notif.build())
                        try {
                            var b = book
                            while (!b.archiveReady) {
                                delay(3_000)
                                b = api!!.getAudiobook(book.hash)
                            }
                            destDir.parentFile?.mkdirs()
                            val startByte = if (archiveFile.exists()) archiveFile.length() else 0L
                            var lastPct = -1
                            api!!.downloadAudiobook(book.hash, archiveFile, startByte) { received, total ->
                                if (total <= 0) return@downloadAudiobook
                                val pct = (received * 100 / total).toInt()
                                downloadProgress = downloadProgress + (book.hash to received.toFloat() / total)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    notif.setContentText("$pct%").setProgress(100, pct, false)
                                    notificationManager.notify(notifId, notif.build())
                                }
                            }
                            notif.setContentText("Extracting…").setProgress(0, 0, true)
                            notificationManager.notify(notifId, notif.build())
                            extractTarGz(archiveFile, destDir)
                            archiveFile.delete()
                            book.duration?.let { store.saveDuration(book.hash, it) }
                            setDownloadState(book.hash, DownloadState.READY)
                            localAudiobooks = withContext(Dispatchers.IO) {
                                store.loadLocalAudiobooks(filesDir).map { it.second }
                            }
                            try {
                                val pos = api!!.getPosition(book.hash)
                                store.savePosition(book.hash, pos)
                                store.saveServerPosition(book.hash, pos)
                                positions = positions + (book.hash to pos)
                            } catch (_: Exception) {}
                            cancelCallbacks.remove(book.hash)
                            notificationManager.notify(notifId, NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                                .setContentTitle(book.title)
                                .setContentText("Download complete")
                                .build())
                        } catch (e: Exception) {
                            setDownloadState(book.hash, DownloadState.REMOTE)
                            downloadProgress = downloadProgress - book.hash
                            cancelCallbacks.remove(book.hash)
                            if (userCancelled || e is CancellationException) {
                                notificationManager.cancel(notifId)
                                archiveFile.delete()
                                if (e is CancellationException) throw e
                            } else {
                                notificationManager.notify(notifId, NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                                    .setSmallIcon(android.R.drawable.stat_notify_error)
                                    .setContentTitle(book.title)
                                    .setContentText("Download failed")
                                    .build())
                            }
                        }
                    }
                    cancelCallbacks[book.hash] = { userCancelled = true; api?.cancelDownload(); job.cancel() }
                }

                fun onDownload(book: Audiobook) {
                    if (!store.loadCellularDownload() && isCellular(context)) {
                        pendingCellularDownload = book
                    } else {
                        startDownload(book)
                    }
                }

                suspend fun refreshBooks() {
                    isRefreshing = true
                    try { books = api!!.getAudiobooks() } catch (_: Exception) {}
                    isRefreshing = false
                }

                LaunchedEffect(api) { if (api != null) refreshBooks() }

                suspend fun auth(serverUrl: String, username: String, password: String, register: Boolean) {
                    val tempApi = IliadApi(Credentials(serverUrl, username, password, null))
                    val token = if (register) tempApi.register(serverUrl, username, password)
                                else tempApi.login(serverUrl, username, password)
                    val creds = Credentials(serverUrl, username, password, token)
                    store.saveCredentials(creds)
                    api = IliadApi(creds)
                    screen = Screen.Main
                }

                // ── Screens ───────────────────────────────────────────────────────────

                when (screen) {
                    Screen.Login -> LoginScreen(
                        onLogin = { url, user, pass -> auth(url, user, pass, register = false) },
                        onRegister = { url, user, pass -> auth(url, user, pass, register = true) },
                    )
                    Screen.Main -> MainScreen(
                        initialPage = mainPage,
                        onPageChange = { mainPage = it },
                        books = books,
                        filteredBooks = filteredBooks,
                        localBooks = localBooks,
                        isRefreshing = isRefreshing,
                        onRefresh = { scope.launch { refreshBooks() } },
                        positions = positions,
                        covers = covers,
                        chapterCounts = chapterCounts,
                        downloadTimestamps = downloadTimestamps,
                        downloadStates = downloadStates,
                        downloadProgress = downloadProgress,
                        store = store,
                        filesDir = filesDir,
                        onDownloadStateChange = { hash, state -> setDownloadState(hash, state) },
                        onDownload = { onDownload(it) },
                        onCancelDownload = { cancelCallbacks[it.hash]?.invoke() },
                        onDelete = { onDelete(it) },
                        onBookSelected = { selectBook(it) },
                        onLocalBookSelected = { book -> selectedBook = book.copy(description = descriptions[book.hash]) },
                        filterDate = filterDate,
                        filterDuration = filterDuration,
                        filterGenres = filterGenres,
                        bookMinYear = bookMinYear,
                        bookMaxYear = bookMaxYear,
                        bookMinDuration = bookMinDuration,
                        bookMaxDuration = bookMaxDuration,
                        onFilterChange = { d, dur, g -> filterDate = d; filterDuration = dur; filterGenres = g },
                        onNavigateAudiobooksSearch = { screen = Screen.AudiobooksSearch },
                        onNavigateLibrarySearch = { screen = Screen.LibrarySearch },
                        onNavigatePlayer = { navigateToPlayer(it) },
                        onNavigateSettings = { settingsReturnScreen = Screen.Main; screen = Screen.Settings },
                    )
                    Screen.LibrarySearch -> SearchScreen(
                        books = filteredBooks,
                        downloadStates = downloadStates,
                        downloadProgress = downloadProgress,
                        onDownload = { onDownload(it) },
                        onCancelDownload = { cancelCallbacks[it.hash]?.invoke() },
                        onBookSelected = { selectBook(it) },
                        onBack = { screen = Screen.Main },
                    )
                    Screen.AudiobooksSearch -> AudiobooksSearchScreen(
                        books = localBooks,
                        positions = positions,
                        chapterCounts = chapterCounts,
                        downloadTimestamps = downloadTimestamps,
                        store = store,
                        filesDir = filesDir,
                        onBack = { screen = Screen.Main },
                        onNavigatePlayer = { navigateToPlayer(it) },
                        onLongPress = { book -> selectedBook = book.copy(description = descriptions[book.hash]) },
                    )
                    Screen.Player -> PlayerScreen(
                        book = playerBook ?: books.first(),
                        chapters = playerChapters,
                        cover = covers[playerBook?.hash],
                        playing = playing,
                        onPlayingChanged = { playing = it },
                        sliderValue = sliderValue,
                        onSliderValueChanged = { sliderValue = it },
                        onSliderSeekFinished = {
                            if (chapterDurationMs > 0) {
                                val seekMs = (sliderValue * chapterDurationMs).toLong()
                                mediaPlayer.seekTo(seekMs.toInt())
                                playerBook?.hash?.let { h -> scope.launch { syncPosition(h) } }
                            }
                        },
                        currentChapter = currentChapter,
                        onCurrentChapterChange = { idx ->
                            playerBook?.hash?.let { h -> scope.launch { syncPosition(h) } }
                            pendingSeekMs = 0L
                            currentChapter = idx
                            playing = true
                        },
                        speedRaw = speedRaw,
                        onSpeedRawChange = { speedRaw = it },
                        chapterDurationMs = chapterDurationMs,
                        chapterDurationsMs = chapterDurationsMs,
                        timerEndMs = timerEndMs,
                        onTimerSet = { endMs ->
                            timerEndMs = endMs
                            originalTimerDurationMs = when {
                                endMs == null -> null
                                endMs == -1L -> -1L
                                else -> endMs - System.currentTimeMillis()
                            }
                            timerGraceEndMs = null
                        },
                        onReplay30 = onReplay30,
                        onForward30 = onForward30,
                        onBack = { screen = Screen.Main },
                        onCarMode = { screen = Screen.CarMode },
                        onNavigateSettings = { settingsReturnScreen = Screen.Player; screen = Screen.Settings },
                    )
                    Screen.CarMode -> CarModeScreen(
                        book = playerBook ?: books.first(),
                        chapters = playerChapters,
                        cover = covers[playerBook?.hash],
                        playing = playing,
                        onPlayingChanged = { playing = it },
                        sliderValue = sliderValue,
                        currentChapter = currentChapter,
                        onCurrentChapterChange = { idx ->
                            playerBook?.hash?.let { h -> scope.launch { syncPosition(h) } }
                            pendingSeekMs = 0L
                            currentChapter = idx
                            playing = true
                        },
                        chapterDurationMs = chapterDurationMs,
                        onReplay30 = onReplay30,
                        onBack = { screen = Screen.Player },
                    )
                    Screen.Settings -> SettingsScreen(
                        store = store,
                        onBack = { screen = settingsReturnScreen },
                        downloadLocation = downloadLocation,
                        onPickDownloadLocation = { pickLauncher.launch(null) },
                        onLogout = {
                            val libBase = store.loadDownloadLocation().let {
                                if (it.isEmpty()) File(filesDir, "library") else File(it)
                            }
                            store.clearAll()
                            libBase.deleteRecursively()
                            downloadLocation = ""
                            screen = Screen.Login
                        },
                        onThemeChange = { theme = it },
                        onAccentChange = { accentIndex = it },
                        onVolumeNormalizationChange = { volumeNormEnabled = it },
                        pairedBtDevices = pairedBtDevices,
                        carDevices = carDevices,
                        onCarDeviceToggle = { address, enabled ->
                            val updated = if (enabled) carDevices + address else carDevices - address
                            carDevices = updated
                            store.saveCarDevices(updated)
                        },
                    )
                }

                selectedBook?.let { book ->
                    BookDetailSheet(
                        book = book,
                        coverBitmap = covers[book.hash],
                        downloadState = downloadStates[book.hash] ?: DownloadState.REMOTE,
                        onDismiss = { selectedBook = null },
                        onPlay = { selectedBook = null; screen = Screen.Player },
                        onDownload = { onDownload(book) },
                        onCancelDownload = { cancelCallbacks[book.hash]?.invoke() },
                        onDelete = { onDelete(book); selectedBook = null },
                    )
                }

                positionConflict?.let { (hash, serverPos, localPos) ->
                    fun formatPos(pos: Position): String {
                        val m = pos.chapterPosition / 60_000
                        val s = (pos.chapterPosition % 60_000) / 1_000
                        return "Chapter ${pos.chapterIndex + 1}, %d:%02d".format(m, s)
                    }
                    val clamp = { idx: Int -> idx.coerceIn(0, (playerChapters.size - 1).coerceAtLeast(0)) }
                    Dialog(onDismissRequest = {}) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Position Conflict", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                                Text("Your local progress differs from the server.", color = MaterialTheme.colorScheme.onBackground)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Local:  ${formatPos(localPos)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                                    Text("Server: ${formatPos(serverPos)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            positionConflict = null
                                            scope.launch {
                                                try {
                                                    api?.putPosition(hash, localPos)
                                                    store.saveServerPosition(hash, localPos)
                                                } catch (_: Exception) {}
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    ) { Text("Keep Local", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Button(
                                        onClick = {
                                            positionConflict = null
                                            currentChapter = clamp(serverPos.chapterIndex)
                                            pendingSeekMs = serverPos.chapterPosition
                                            store.savePosition(hash, serverPos)
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Take Server") }
                                }
                            }
                        }
                    }
                }

                pendingCellularDownload?.let { book ->
                    Dialog(onDismissRequest = { pendingCellularDownload = null }) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Download over cellular", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                                Text("You're on a cellular connection. This may use your data allowance.", color = MaterialTheme.colorScheme.onBackground)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { pendingCellularDownload = null },
                                        modifier = Modifier.weight(1f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    ) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Button(
                                        onClick = { pendingCellularDownload = null; startDownload(book) },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Download") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
