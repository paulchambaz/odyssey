package xyz.chambaz.odyssey.store

import android.content.Context
import com.google.gson.Gson
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

    fun saveServerPosition(hash: String, position: Position) {
        prefs.putString("pos_server_$hash", gson.toJson(position))
    }

    fun loadServerPosition(hash: String): Position? {
        val json = prefs.getString("pos_server_$hash") ?: return null
        return gson.fromJson(json, Position::class.java)
    }

    fun clearAll() = prefs.clear()
}
