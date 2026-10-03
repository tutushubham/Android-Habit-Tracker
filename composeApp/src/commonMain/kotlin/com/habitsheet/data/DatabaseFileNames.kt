package com.habitsheet.data

/**
 * Naming rules for moving an unreadable SQLite database aside, shared by Android and iOS so they behave the same
 * and the rules can be tested once. No file access here, only names.
 */
object DatabaseFileNames {
    /** A SQLite database is the main file plus these optional companions. */
    val companionSuffixes = listOf("", "-wal", "-shm", "-journal")

    fun filesOf(name: String): List<String> = companionSuffixes.map { name + it }

    fun asideName(fileName: String, timestamp: Long): String = "$fileName.damaged-$timestamp"

    private fun asidePattern(name: String) = Regex("^" + Regex.escape(name) + "(-wal|-shm|-journal)?\\.damaged-(\\d+)$")

    /** Timestamps of the aside-sets present in [fileNames], newest first. */
    fun asideSets(fileNames: Collection<String>, name: String): List<Long> {
        val pattern = asidePattern(name)
        return fileNames.mapNotNull { pattern.matchEntire(it)?.groupValues?.get(2)?.toLongOrNull() }.distinct().sortedDescending()
    }

    /** Names to delete so that only the newest [keep] aside-sets remain. Anything not matching the pattern is never listed. */
    fun staleAsideNames(fileNames: Collection<String>, name: String, keep: Int): List<String> {
        val pattern = asidePattern(name)
        val newest = asideSets(fileNames, name).take(keep).toSet()
        return fileNames.filter { file ->
            val timestamp = pattern.matchEntire(file)?.groupValues?.get(2)?.toLongOrNull()
            timestamp != null && timestamp !in newest
        }
    }
}
