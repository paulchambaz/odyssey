package xyz.chambaz.odyssey.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import xyz.chambaz.odyssey.store.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: Store,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onThemeChange: (String) -> Unit = {},
    onAccentChange: (Int) -> Unit = {},
    onVolumeNormalizationChange: (Boolean) -> Unit = {},
    downloadLocation: String = "",
    onPickDownloadLocation: () -> Unit = {},
    pairedBtDevices: List<Pair<String, String>> = emptyList(),
    carDevices: Set<String> = emptySet(),
    onCarDeviceToggle: (String, Boolean) -> Unit = { _, _ -> },
) {
    BackHandler { onBack() }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }

    var theme by remember { mutableStateOf(store.loadTheme()) }
    var accentIndex by remember { mutableIntStateOf(store.loadAccentColorIndex()) }
    var rewindSeconds by remember { mutableIntStateOf(store.loadRewindOnResume()) }
    var volumeNormalization by remember { mutableStateOf(store.loadVolumeNormalization()) }
    var cellularDownload by remember { mutableStateOf(store.loadCellularDownload()) }
    var shakeToExtend by remember { mutableStateOf(store.loadShakeToExtend()) }
    var autoCarMode by remember { mutableStateOf(store.loadAutoCarMode()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Appearance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Text("Theme", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Light", "Dark", "Black").forEach { option ->
                    val selected = theme == option.lowercase()
                    OutlinedButton(
                        onClick = { theme = option.lowercase(); store.saveTheme(theme); onThemeChange(theme) },
                        border = BorderStroke(1.dp, if (selected) Accent else Color(0xFF444444)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) Accent.copy(alpha = 0.15f) else Color.Transparent,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) { Text(option, color = if (selected) Accent else Color.Gray) }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Accent color", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                accentColors.forEachIndexed { i, color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(if (accentIndex == i) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                            .clickable { accentIndex = i; store.saveAccentColorIndex(i); onAccentChange(i) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(24.dp))

            Text("Playback", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Text("Rewind on resume", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Off", 5 to "5s", 10 to "10s", 15 to "15s", 30 to "30s").forEach { (seconds, label) ->
                    val selected = rewindSeconds == seconds
                    OutlinedButton(
                        onClick = { rewindSeconds = seconds; store.saveRewindOnResume(seconds) },
                        border = BorderStroke(1.dp, if (selected) Accent else Color(0xFF444444)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) Accent.copy(alpha = 0.15f) else Color.Transparent,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text(label, color = if (selected) Accent else Color.Gray) }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Volume normalization", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = volumeNormalization,
                    onCheckedChange = { volumeNormalization = it; store.saveVolumeNormalization(it); onVolumeNormalizationChange(it) },
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(24.dp))

            Text("Library", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download location", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        if (downloadLocation.isEmpty()) "Internal storage" else downloadLocation,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
                OutlinedButton(
                    onClick = onPickDownloadLocation,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) { Text("Change", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Download over cellular", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = cellularDownload,
                    onCheckedChange = { cellularDownload = it; store.saveCellularDownload(it) },
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(24.dp))

            Text("Features", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Shake to extend sleep timer", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = shakeToExtend,
                    onCheckedChange = { shakeToExtend = it; store.saveShakeToExtend(it) },
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Automatic car mode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = autoCarMode,
                    onCheckedChange = { autoCarMode = it; store.saveAutoCarMode(it) },
                )
            }

            if (autoCarMode) {
                Spacer(Modifier.height(12.dp))
                Text("Car Bluetooth devices", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (pairedBtDevices.isEmpty()) {
                    Text("No paired devices found", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    pairedBtDevices.forEach { (name, address) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                                Text(address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Switch(
                                checked = address in carDevices,
                                onCheckedChange = { onCarDeviceToggle(address, it) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Logout") }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLogoutDialog) {
        Dialog(onDismissRequest = { showLogoutDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Logout", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text("Are you sure you want to logout? This will clear all stored credentials.", color = MaterialTheme.colorScheme.onBackground)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showLogoutDialog = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Button(
                            onClick = { showLogoutDialog = false; onLogout() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) { Text("Logout") }
                    }
                }
            }
        }
    }

    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            containerColor = Color(0xFF0D0D0D),
            title = { Text("Position Conflict", color = MaterialTheme.colorScheme.onBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your local and server positions differ.", color = MaterialTheme.colorScheme.onBackground)
                    Text("Server: Chapter 3, 4:22", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Local: Chapter 3, 1:05", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { showConflictDialog = false }) { Text("Keep Local") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConflictDialog = false },
                    border = BorderStroke(2.dp, Accent),
                ) { Text("Take Server", color = Accent) }
            },
        )
    }
}
