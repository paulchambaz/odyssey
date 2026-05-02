package xyz.chambaz.odyssey.player

import xyz.chambaz.odyssey.model.Chapter
import java.io.File

fun parseInfoYml(file: File): List<Chapter> {
    val chapters = mutableListOf<Chapter>()
    var inChapters = false
    var pendingTitle: String? = null
    for (line in file.readLines()) {
        if (!inChapters) {
            if (line == "chapters:") inChapters = true
            continue
        }
        if (line.isNotEmpty() && !line[0].isWhitespace()) break
        val trimmed = line.trim()
        if (trimmed.startsWith("- title:")) {
            pendingTitle = trimmed.removePrefix("- title:").trim().trim('"')
        } else if (trimmed.startsWith("path:") && pendingTitle != null) {
            chapters += Chapter(pendingTitle!!, trimmed.removePrefix("path:").trim().trim('"'))
            pendingTitle = null
        }
    }
    return chapters
}
