package xyz.chambaz.odyssey.model

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelsTest {
    private val gson = Gson()

    @Test
    fun `audiobook list shape deserializes`() {
        val json = """{"hash":"abc123","title":"Dune","author":"Herbert","archive_ready":true}"""
        val book = gson.fromJson(json, Audiobook::class.java)
        assertEquals("abc123", book.hash)
        assertEquals("Dune", book.title)
        assertEquals("Herbert", book.author)
        assertTrue(book.archiveReady)
        assertNull(book.date)
        assertNull(book.duration)
    }

    @Test
    fun `audiobook detail shape deserializes`() {
        val json = """{"hash":"abc123","title":"Dune","author":"Herbert","archive_ready":true,
            "date":1965,"description":"A sci-fi epic","genres":["sci-fi","classic"],
            "duration":123456789,"size":987654321}"""
        val book = gson.fromJson(json, Audiobook::class.java)
        assertEquals(1965, book.date)
        assertEquals("A sci-fi epic", book.description)
        assertEquals(listOf("sci-fi", "classic"), book.genres)
        assertEquals(123456789L, book.duration)
        assertEquals(987654321L, book.size)
    }

    @Test
    fun `position get-response deserializes without timestamp`() {
        val json = """{"chapter_index":2,"chapter_position":180000}"""
        val pos = gson.fromJson(json, Position::class.java)
        assertEquals(2, pos.chapterIndex)
        assertEquals(180000L, pos.chapterPosition)
        assertNull(pos.clientTimestamp)
    }

    @Test
    fun `position serializes with timestamp for set-request`() {
        val pos = Position(chapterIndex = 2, chapterPosition = 180000L, clientTimestamp = 1714521600L)
        val json = gson.toJson(pos)
        val roundTrip = gson.fromJson(json, Position::class.java)
        assertEquals(pos, roundTrip)
    }

    @Test
    fun `credentials round-trips through gson`() {
        val creds = Credentials(
            baseUrl = "http://192.168.1.10:9090",
            username = "alice",
            password = "secret",
            token = "tok_abc"
        )
        val roundTrip = gson.fromJson(gson.toJson(creds), Credentials::class.java)
        assertEquals(creds, roundTrip)
    }
}
