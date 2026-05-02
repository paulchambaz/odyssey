package xyz.chambaz.odyssey.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import android.util.Log
import xyz.chambaz.odyssey.model.Audiobook
import xyz.chambaz.odyssey.model.Credentials
import xyz.chambaz.odyssey.model.Position
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthException(message: String) : IOException(message)
class ArchiveNotReadyException : IOException("archive not ready")

class IliadApi(var credentials: Credentials) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()
    @Volatile private var activeDownloadCall: okhttp3.Call? = null

    fun cancelDownload() { activeDownloadCall?.cancel() }

    suspend fun login(baseUrl: String, username: String, password: String): String =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(mapOf("username" to username, "password" to password))
            val req = Request.Builder()
                .url("$baseUrl/auth/login")
                .post(body.toRequestBody(json))
                .build()
            Log.d("IliadApi", "login POST $baseUrl/auth/login")
            client.newCall(req).execute().use { resp ->
                Log.d("IliadApi", "login response code=${resp.code}")
                if (!resp.isSuccessful) throw IOException("login failed: ${resp.code}")
                val responseBody = resp.body?.string() ?: throw IOException("login failed: empty body")
                Log.d("IliadApi", "login response body=$responseBody")
                gson.fromJson(responseBody, Map::class.java)["token"] as? String
                    ?: throw IOException("login failed: no token in response")
            }
        }

    suspend fun register(baseUrl: String, username: String, password: String): String =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(mapOf("username" to username, "password" to password))
            val req = Request.Builder()
                .url("$baseUrl/auth/register")
                .post(body.toRequestBody(json))
                .build()
            Log.d("IliadApi", "register POST $baseUrl/auth/register")
            client.newCall(req).execute().use { resp ->
                Log.d("IliadApi", "register response code=${resp.code}")
                if (resp.code == 409) throw IOException("username taken")
                if (!resp.isSuccessful) throw IOException("register failed: ${resp.code}")
                val responseBody = resp.body?.string() ?: throw IOException("register failed: empty body")
                Log.d("IliadApi", "register response body=$responseBody")
                gson.fromJson(responseBody, Map::class.java)["token"] as? String
                    ?: throw IOException("register failed: no token in response")
            }
        }

    suspend fun getAudiobooks(): List<Audiobook> = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("${credentials.baseUrl}/audiobooks").get()).use { resp ->
            val type = object : TypeToken<List<Audiobook>>() {}.type
            gson.fromJson(resp.body!!.string(), type)
        }
    }

    suspend fun getAudiobook(hash: String): Audiobook = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("${credentials.baseUrl}/audiobooks/$hash").get()).use { resp ->
            gson.fromJson(resp.body!!.string(), Audiobook::class.java)
        }
    }

    suspend fun downloadAudiobook(hash: String, dest: File, startByte: Long = 0L, onProgress: (Long, Long) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${credentials.baseUrl}/audiobooks/$hash/download").get()
        if (startByte > 0L) req.header("Range", "bytes=$startByte-")
        val resp = execute(req) { activeDownloadCall = it }
        if (resp.code == 503) {
            resp.close()
            throw ArchiveNotReadyException()
        }
        try {
            resp.use {
                val contentLength = it.body!!.contentLength()
                val total = it.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    ?: if (startByte > 0L && contentLength >= 0L) startByte + contentLength else contentLength
                var received = startByte
                val buf = ByteArray(65_536)
                it.body!!.byteStream().use { input ->
                    FileOutputStream(dest, startByte > 0L).use { out ->
                        var read: Int
                        while (input.read(buf).also { n -> read = n } != -1) {
                            out.write(buf, 0, read)
                            received += read
                            onProgress(received, total)
                        }
                    }
                }
            }
        } finally {
            activeDownloadCall = null
        }
    }

    suspend fun getPosition(hash: String): Position = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("${credentials.baseUrl}/positions/$hash").get()).use { resp ->
            gson.fromJson(resp.body!!.string(), Position::class.java)
        }
    }

    suspend fun putPosition(hash: String, position: Position) = withContext(Dispatchers.IO) {
        val map = mapOf(
            "chapter_index" to position.chapterIndex,
            "chapter_position" to position.chapterPosition,
            "timestamp" to (position.clientTimestamp ?: (System.currentTimeMillis() / 1000L))
        )
        val body = gson.toJson(map).toRequestBody(json)
        execute(Request.Builder().url("${credentials.baseUrl}/positions/$hash").put(body))
            .use { }
    }

    private fun execute(builder: Request.Builder, onCall: ((okhttp3.Call) -> Unit)? = null): Response {
        val req = builder.auth().build()
        Log.d("IliadApi", "${req.method} ${req.url}")
        val call = client.newCall(req)
        onCall?.invoke(call)
        val resp = call.execute()
        Log.d("IliadApi", "${req.method} ${req.url} -> ${resp.code}")
        if (resp.code != 401) return resp
        resp.close()

        val newToken = reauth()
        val retryReq = builder.auth(newToken).build()
        Log.d("IliadApi", "${retryReq.method} ${retryReq.url} (retry)")
        val retry = client.newCall(retryReq)
        onCall?.invoke(retry)
        val retryResp = retry.execute()
        Log.d("IliadApi", "${retryReq.method} ${retryReq.url} -> ${retryResp.code} (retry)")
        if (retryResp.code == 401) {
            retryResp.close()
            throw AuthException("re-auth failed: wrong password or account deleted")
        }
        return retryResp
    }

    private fun reauth(): String {
        val body = gson.toJson(mapOf("username" to credentials.username, "password" to credentials.password))
        val req = Request.Builder()
            .url("${credentials.baseUrl}/auth/login")
            .post(body.toRequestBody(json))
            .build()
        Log.d("IliadApi", "POST ${req.url} (reauth)")
        client.newCall(req).execute().use { resp ->
            Log.d("IliadApi", "POST ${req.url} -> ${resp.code} (reauth)")
            if (!resp.isSuccessful) throw AuthException("re-auth failed: ${resp.code}")
            val token = gson.fromJson(resp.body!!.string(), Map::class.java)["token"] as String
            credentials = credentials.copy(token = token)
            return token
        }
    }

    private fun Request.Builder.auth(token: String? = credentials.token): Request.Builder =
        header("Authorization", "Bearer $token")
}
