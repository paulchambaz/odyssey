package xyz.chambaz.odyssey.model

import com.google.gson.annotations.SerializedName

data class Credentials(
    val baseUrl: String,
    val username: String,
    val password: String,
    val token: String?
)

data class Audiobook(
    val hash: String,
    val title: String,
    val author: String,
    @SerializedName("archive_ready") val archiveReady: Boolean,
    val date: Int? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val duration: Long? = null,
    val size: Long? = null,
    val cover: String? = null,
)

data class Chapter(val title: String, val path: String)

data class Position(
    @SerializedName("chapter_index") val chapterIndex: Int,
    @SerializedName("chapter_position") val chapterPosition: Long,
    @SerializedName("timestamp") val clientTimestamp: Long? = null
)
