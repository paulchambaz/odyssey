package xyz.chambaz.odyssey.ui

import xyz.chambaz.odyssey.model.Audiobook
import xyz.chambaz.odyssey.model.Chapter

val fakeBooks = listOf(
    Audiobook(
        hash = "abc123",
        title = "The Lord of the Rings",
        author = "J.R.R. Tolkien",
        archiveReady = true,
        date = 1954,
        description = "An epic high-fantasy novel following the quest to destroy the One Ring.",
        genres = listOf("Fantasy", "Adventure"),
        duration = 36_000_000L,
        size = 520_000_000L,
    ),
    Audiobook(
        hash = "def456",
        title = "Dune",
        author = "Frank Herbert",
        archiveReady = true,
        date = 1965,
        description = "A science fiction masterpiece set on the desert planet Arrakis.",
        genres = listOf("Science Fiction"),
        duration = 21_600_000L,
        size = 310_000_000L,
    ),
    Audiobook(
        hash = "ghi789",
        title = "Neuromancer",
        author = "William Gibson",
        archiveReady = false,
        date = 1984,
        description = "The seminal cyberpunk novel that defined a genre.",
        genres = listOf("Science Fiction", "Cyberpunk"),
        duration = 18_000_000L,
        size = 240_000_000L,
    ),
)

val fakeChapters = listOf(
    Chapter("A Long-expected Party", "ch01.mp3"),
    Chapter("The Shadow of the Past", "ch02.mp3"),
    Chapter("Three is Company", "ch03.mp3"),
    Chapter("A Short Cut to Mushrooms", "ch04.mp3"),
    Chapter("A Conspiracy Unmasked", "ch05.mp3"),
)
