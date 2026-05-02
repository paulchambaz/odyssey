package xyz.chambaz.odyssey.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class PlayerTest {

    private fun tmpYml(content: String): File =
        File.createTempFile("info", ".yml").also { it.writeText(content); it.deleteOnExit() }

    @Test
    fun `parseInfoYml returns chapters in order`() {
        val yml = """
            title: "Test Book"
            author: "Author"
            chapters:
              - title: "Chapter One"
                path: "01-chapter-one.opus"
              - title: "Chapter Two"
                path: "02-chapter-two.opus"
        """.trimIndent()
        val chapters = parseInfoYml(tmpYml(yml))
        assertEquals(2, chapters.size)
        assertEquals("Chapter One", chapters[0].title)
        assertEquals("01-chapter-one.opus", chapters[0].path)
        assertEquals("Chapter Two", chapters[1].title)
        assertEquals("02-chapter-two.opus", chapters[1].path)
    }

    @Test
    fun `parseInfoYml returns empty list when no chapters section`() {
        val yml = "title: \"Test\"\nauthor: \"Author\"\n"
        assertEquals(emptyList<Any>(), parseInfoYml(tmpYml(yml)))
    }

    @Test
    fun `parseInfoYml returns empty list for empty file`() {
        assertEquals(emptyList<Any>(), parseInfoYml(tmpYml("")))
    }

    @Test
    fun `parseInfoYml handles keys after chapters block`() {
        val yml = """
            chapters:
              - title: "Only Chapter"
                path: "01.opus"
            extra: "ignored"
        """.trimIndent()
        val chapters = parseInfoYml(tmpYml(yml))
        assertEquals(1, chapters.size)
        assertEquals("Only Chapter", chapters[0].title)
    }
}
