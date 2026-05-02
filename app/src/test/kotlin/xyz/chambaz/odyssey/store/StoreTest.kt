package xyz.chambaz.odyssey.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import xyz.chambaz.odyssey.model.Credentials
import xyz.chambaz.odyssey.model.Position

class StoreTest {
    private fun store() = Store(FakePrefs())

    private class FakePrefs : Prefs {
        private val map = mutableMapOf<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun clear() { map.clear() }
    }

    @Test
    fun `credentials round-trip`() {
        val s = store()
        val creds = Credentials("http://192.168.1.1:9090", "alice", "secret", "tok_abc")
        s.saveCredentials(creds)
        assertEquals(creds, s.loadCredentials())
    }

    @Test
    fun `credentials null when absent`() {
        assertNull(store().loadCredentials())
    }

    @Test
    fun `credentials null token survives round-trip`() {
        val s = store()
        val creds = Credentials("http://192.168.1.1:9090", "alice", "secret", null)
        s.saveCredentials(creds)
        assertEquals(creds, s.loadCredentials())
    }

    @Test
    fun `position round-trip`() {
        val s = store()
        val pos = Position(chapterIndex = 3, chapterPosition = 45000L, clientTimestamp = 1714521600L)
        s.savePosition("abc123", pos)
        assertEquals(pos, s.loadPosition("abc123"))
    }

    @Test
    fun `position null when absent`() {
        assertNull(store().loadPosition("abc123"))
    }

    @Test
    fun `position keyed by hash`() {
        val s = store()
        val pos1 = Position(chapterIndex = 1, chapterPosition = 1000L)
        val pos2 = Position(chapterIndex = 2, chapterPosition = 2000L)
        s.savePosition("hash1", pos1)
        s.savePosition("hash2", pos2)
        assertEquals(pos1, s.loadPosition("hash1"))
        assertEquals(pos2, s.loadPosition("hash2"))
    }

    @Test
    fun `download state round-trip`() {
        val s = store()
        s.saveDownloadState("abc123", DownloadState.READY)
        assertEquals(DownloadState.READY, s.loadDownloadState("abc123"))
    }

    @Test
    fun `download state defaults to REMOTE`() {
        assertEquals(DownloadState.REMOTE, store().loadDownloadState("abc123"))
    }

    @Test
    fun `clearAll wipes credentials and positions`() {
        val s = store()
        s.saveCredentials(Credentials("http://192.168.1.1:9090", "alice", "secret", "tok"))
        s.savePosition("abc123", Position(chapterIndex = 1, chapterPosition = 1000L))
        s.saveDownloadState("abc123", DownloadState.READY)
        s.clearAll()
        assertNull(s.loadCredentials())
        assertNull(s.loadPosition("abc123"))
        assertEquals(DownloadState.REMOTE, s.loadDownloadState("abc123"))
    }
}
