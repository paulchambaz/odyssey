package xyz.chambaz.odyssey.api

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import xyz.chambaz.odyssey.model.Credentials
import xyz.chambaz.odyssey.model.Position
import java.io.IOException

class IliadApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: IliadApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("").toString().trimEnd('/')
        api = IliadApi(Credentials(baseUrl, "alice", "secret", "tok_initial"))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login returns token`() = runTest {
        server.enqueue(MockResponse().setBody("""{"token":"tok_new"}"""))
        val token = api.login(server.url("").toString().trimEnd('/'), "alice", "secret")
        assertEquals("tok_new", token)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/auth/login", req.path)
    }

    @Test
    fun `register returns token`() = runTest {
        server.enqueue(MockResponse().setBody("""{"token":"tok_reg"}"""))
        val token = api.register(server.url("").toString().trimEnd('/'), "bob", "pass")
        assertEquals("tok_reg", token)
    }

    @Test
    fun `register 409 throws with username taken message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("username taken"))
        val ex = assertThrows<IOException> {
            api.register(server.url("").toString().trimEnd('/'), "alice", "pass")
        }
        assertEquals("username taken", ex.message)
    }

    @Test
    fun `getAudiobooks parses list`() = runTest {
        server.enqueue(MockResponse().setBody("""[
            {"hash":"h1","title":"Dune","author":"Herbert","archive_ready":true},
            {"hash":"h2","title":"Foundation","author":"Asimov","archive_ready":false}
        ]"""))
        val books = api.getAudiobooks()
        assertEquals(2, books.size)
        assertEquals("h1", books[0].hash)
        assertEquals("Dune", books[0].title)
        assertEquals(true, books[0].archiveReady)
        assertEquals("h2", books[1].hash)
        assertEquals(false, books[1].archiveReady)
    }

    @Test
    fun `getAudiobook parses full detail`() = runTest {
        server.enqueue(MockResponse().setBody("""{"hash":"h1","title":"Dune","author":"Herbert",
            "archive_ready":true,"date":1965,"description":"Epic","genres":["sci-fi"],
            "duration":123456789,"size":987654321}"""))
        val book = api.getAudiobook("h1")
        assertEquals("h1", book.hash)
        assertEquals(1965, book.date)
        assertEquals(listOf("sci-fi"), book.genres)
        assertEquals(123456789L, book.duration)
        assertEquals(987654321L, book.size)
        val req = server.takeRequest()
        assertEquals("/audiobooks/h1", req.path)
    }

    @Test
    fun `getPosition parses response`() = runTest {
        server.enqueue(MockResponse().setBody("""{"chapter_index":3,"chapter_position":45000}"""))
        val pos = api.getPosition("h1")
        assertEquals(3, pos.chapterIndex)
        assertEquals(45000L, pos.chapterPosition)
        val req = server.takeRequest()
        assertEquals("/positions/h1", req.path)
    }

    @Test
    fun `putPosition sends correct body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val pos = Position(chapterIndex = 2, chapterPosition = 180000L, clientTimestamp = 1714521600L)
        api.putPosition("h1", pos)
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/positions/h1", req.path)
        val body = req.body.readUtf8()
        assert(body.contains("chapter_index"))
        assert(body.contains("timestamp"))
    }

    @Test
    fun `downloadAudiobook 503 throws ArchiveNotReadyException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertThrows<ArchiveNotReadyException> {
            val dest = createTempFile()
            api.downloadAudiobook("h1", dest)
        }
    }

    @Test
    fun `401 triggers re-auth and retries`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("""{"token":"tok_refreshed"}"""))
        server.enqueue(MockResponse().setBody("""[]"""))
        val books = api.getAudiobooks()
        assertEquals(0, books.size)
        assertEquals("tok_refreshed", api.credentials.token)
        val req1 = server.takeRequest()
        val req2 = server.takeRequest()
        val req3 = server.takeRequest()
        assertEquals("Bearer tok_initial", req1.getHeader("Authorization"))
        assertEquals("/auth/login", req2.path)
        assertEquals("Bearer tok_refreshed", req3.getHeader("Authorization"))
    }

    @Test
    fun `401 on retry throws AuthException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("""{"token":"tok_refreshed"}"""))
        server.enqueue(MockResponse().setResponseCode(401))
        assertThrows<AuthException> {
            api.getAudiobooks()
        }
    }

    @Test
    fun `auth header sent on all authenticated requests`() = runTest {
        server.enqueue(MockResponse().setBody("""[]"""))
        api.getAudiobooks()
        val req = server.takeRequest()
        assertEquals("Bearer tok_initial", req.getHeader("Authorization"))
    }
}
