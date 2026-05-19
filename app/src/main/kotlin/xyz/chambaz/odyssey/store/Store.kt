package xyz.chambaz.odyssey.store

import android.content.Context
import com.google.gson.Gson
import xyz.chambaz.odyssey.model.Audiobook
import xyz.chambaz.odyssey.model.Credentials
import xyz.chambaz.odyssey.model.Position
import java.io.File

interface Prefs {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

class SharedPrefsAdapter(context: Context) : Prefs {
    private val sp = context.getSharedPreferences("odyssey", Context.MODE_PRIVATE)
    override fun getString(key: String) = sp.getString(key, null)
    override fun putString(key: String, value: String) { sp.edit().putString(key, value).apply() }
    override fun remove(key: String) { sp.edit().remove(key).apply() }
    override fun clear() { sp.edit().clear().apply() }
}

enum class DownloadState { REMOTE, IN_PROGRESS, READY }

class Store(private val prefs: Prefs) {
    private val gson = Gson()

    fun saveCredentials(c: Credentials) {
        prefs.putString("baseUrl", c.baseUrl)
        prefs.putString("username", c.username)
        prefs.putString("password", c.password)
        if (c.token != null) prefs.putString("token", c.token) else prefs.remove("token")
    }

    fun loadCredentials(): Credentials? {
        val baseUrl = prefs.getString("baseUrl") ?: return null
        val username = prefs.getString("username") ?: return null
        val password = prefs.getString("password") ?: return null
        val token = prefs.getString("token")
        return Credentials(baseUrl, username, password, token)
    }

    fun savePosition(hash: String, position: Position) {
        prefs.putString("pos_$hash", gson.toJson(position))
    }

    fun loadPosition(hash: String): Position? {
        val json = prefs.getString("pos_$hash") ?: return null
        return gson.fromJson(json, Position::class.java)
    }

    fun saveDownloadState(hash: String, state: DownloadState) {
        prefs.putString("dl_$hash", state.name)
    }

    fun loadDownloadState(hash: String): DownloadState {
        val name = prefs.getString("dl_$hash") ?: return DownloadState.REMOTE
        return DownloadState.valueOf(name)
    }

    fun saveTheme(theme: String) = prefs.putString("theme", theme)
    fun loadTheme(): String = prefs.getString("theme") ?: "black"

    fun saveAccentColorIndex(index: Int) = prefs.putString("accentColorIndex", index.toString())
    fun loadAccentColorIndex(): Int = prefs.getString("accentColorIndex")?.toIntOrNull() ?: 0

    fun saveRewindOnResume(seconds: Int) = prefs.putString("rewindOnResume", seconds.toString())
    fun loadRewindOnResume(): Int = prefs.getString("rewindOnResume")?.toIntOrNull() ?: 10

    fun saveVolumeNormalization(enabled: Boolean) = prefs.putString("volumeNormalization", enabled.toString())
    fun loadVolumeNormalization(): Boolean = prefs.getString("volumeNormalization")?.toBoolean() ?: false

    fun saveLoudnessGain(gainMb: Int) = prefs.putString("loudnessGain", gainMb.toString())
    fun loadLoudnessGain(): Int = prefs.getString("loudnessGain")?.toIntOrNull() ?: 1000

    fun saveDownloadLocation(path: String) = prefs.putString("downloadLocation", path)
    fun loadDownloadLocation(): String = prefs.getString("downloadLocation") ?: ""

    fun libraryDir(hash: String, filesDir: File): File {
        val loc = loadDownloadLocation()
        return if (loc.isEmpty()) File(filesDir, "library/$hash") else File(loc, hash)
    }

    fun saveCellularDownload(enabled: Boolean) = prefs.putString("cellularDownload", enabled.toString())
    fun loadCellularDownload(): Boolean = prefs.getString("cellularDownload")?.toBoolean() ?: false

    fun saveShakeToExtend(enabled: Boolean) = prefs.putString("shakeToExtend", enabled.toString())
    fun loadShakeToExtend(): Boolean = prefs.getString("shakeToExtend")?.toBoolean() ?: true

    fun saveAutoCarMode(enabled: Boolean) = prefs.putString("autoCarMode", enabled.toString())
    fun loadAutoCarMode(): Boolean = prefs.getString("autoCarMode")?.toBoolean() ?: false

    fun saveCarDevices(addresses: Set<String>) = prefs.putString("carDevices", addresses.joinToString(","))
    fun loadCarDevices(): Set<String> {
        val s = prefs.getString("carDevices") ?: return emptySet()
        return if (s.isEmpty()) emptySet() else s.split(",").toSet()
    }

    fun savePlaybackSpeed(speed: Float) = prefs.putString("playbackSpeed", speed.toString())
    fun loadPlaybackSpeed(): Float = prefs.getString("playbackSpeed")?.toFloatOrNull() ?: 1.0f

    fun saveDuration(hash: String, duration: Long) = prefs.putString("dur_$hash", duration.toString())
    fun loadDuration(hash: String): Long? = prefs.getString("dur_$hash")?.toLongOrNull()

    fun saveServerPosition(hash: String, position: Position) {
        prefs.putString("pos_server_$hash", gson.toJson(position))
    }

    fun loadServerPosition(hash: String): Position? {
        val json = prefs.getString("pos_server_$hash") ?: return null
        return gson.fromJson(json, Position::class.java)
    }

    fun clearAll() = prefs.clear()

    fun loadLocalAudiobooks(filesDir: File): List<Pair<String, Audiobook>> {
        val loc = loadDownloadLocation()
        val libBase = if (loc.isEmpty()) File(filesDir, "library") else File(loc)
        if (!libBase.exists()) return emptyList()

        return libBase.listFiles()?.mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            val hash = dir.name
            val infoFile = File(dir, "info.yml")
            if (!infoFile.exists()) return@mapNotNull null

            try {
                val lines = infoFile.readLines()
                val title = lines.firstOrNull { it.startsWith("title:") }
                    ?.removePrefix("title:")?.trim()?.trim('"') ?: "Unknown"
                val author = lines.firstOrNull { it.startsWith("author:") }
                    ?.removePrefix("author:")?.trim()?.trim('"') ?: "Unknown"
                val date = lines.firstOrNull { it.startsWith("date:") }
                    ?.removePrefix("date:")?.trim()?.toIntOrNull()
                val description = lines.firstOrNull { it.startsWith("description:") }
                    ?.removePrefix("description:")?.trim()?.trim('"')
                val genreLines = lines.dropWhile { !it.startsWith("genres:") }
                    .drop(1).takeWhile { it.startsWith("  - ") }
                val genres = genreLines.map { it.removePrefix("  - ").trim() }
                val chapterLines = lines.dropWhile { !it.startsWith("chapters:") }
                    .drop(1).takeWhile { it.startsWith("  - ") || it.startsWith("    ") }
                val chapterCount = chapterLines.count { it.trim().startsWith("- title:") }
                val coverPath = lines.firstOrNull { it.startsWith("cover:") }
                    ?.removePrefix("cover:")?.trim()?.trim('"')
                val coverBitmap = coverPath?.let {
                    try {
                        android.graphics.BitmapFactory.decodeFile(File(dir, it).absolutePath)
                            ?.let { bmp -> android.util.Base64.encodeToString(
                                android.graphics.Bitmap.createBitmap(bmp).let {
                                    val stream = java.io.ByteArrayOutputStream()
                                    it.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                                    stream.toByteArray()
                                }, android.util.Base64.DEFAULT) }
                    } catch (_: Exception) { null }
                }

                val audiobook = xyz.chambaz.odyssey.model.Audiobook(
                    hash = hash,
                    title = title,
                    author = author,
                    cover = coverBitmap,
                    date = date,
                    description = description,
                    genres = if (genres.isNotEmpty()) genres else null,
                    duration = null,
                    archiveReady = false
                )
                hash to audiobook
            } catch (_: Exception) {
                null
            }
        } ?: emptyList()
    }
}
