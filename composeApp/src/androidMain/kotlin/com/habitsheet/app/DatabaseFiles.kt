package com.habitsheet.app

import com.habitsheet.data.DatabaseFileNames
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The files that make up the SQLite database [name] in [directory]: the main file plus its write-ahead log,
 * shared-memory and rollback-journal companions. Pure `java.io`, so it can be unit-tested on the JVM.
 */
internal class DatabaseFiles(private val directory: File, private val name: String) {
    /** The files that exist right now, main file first. */
    fun existing(): List<File> = DatabaseFileNames.filesOf(name).map { File(directory, it) }.filter { it.isFile }

    /** Writes every existing file into one zip (flat, original names). Returns how many files were included. */
    fun writeZip(out: OutputStream): Int {
        val files = existing()
        ZipOutputStream(out).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return files.size
    }

    /**
     * Renames every existing file to `<file>.damaged-<timestamp>` (nothing is deleted), so the next open creates a
     * fresh database. If any rename fails, the ones already done are renamed back and an [IOException] is thrown.
     * Afterwards only the newest [keep] aside-sets remain; older sets are deleted.
     * Returns the number of files moved (0 if there was nothing to move).
     */
    fun setAside(timestamp: Long, keep: Int = 3): Int {
        val moved = mutableListOf<Pair<File, File>>()
        try {
            for (file in existing()) {
                val target = File(directory, DatabaseFileNames.asideName(file.name, timestamp))
                if (target.exists() || !file.renameTo(target)) throw IOException("Could not move ${file.name} aside")
                moved += file to target
            }
        } catch (e: IOException) {
            moved.reversed().forEach { (original, target) -> target.renameTo(original) }
            throw e
        }
        if (moved.isNotEmpty()) {
            DatabaseFileNames.staleAsideNames(directory.list().orEmpty().toList(), name, keep).forEach { File(directory, it).delete() }
        }
        return moved.size
    }

    /** Timestamps of the sets that were moved aside, newest first. */
    fun asideSets(): List<Long> = DatabaseFileNames.asideSets(directory.list().orEmpty().toList(), name)
}
