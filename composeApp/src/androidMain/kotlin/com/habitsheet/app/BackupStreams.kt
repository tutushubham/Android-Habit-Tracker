package com.habitsheet.app

import com.habitsheet.presentation.UiText
import com.habitsheet.resources.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** A backup file could not be read or written. [userMessage] is safe to show. */
internal class BackupIoException(val userMessage: UiText, cause: Throwable? = null) : Exception("Backup file I/O failed", cause)

/** Stream handling for backup files, kept free of Android classes so it can be unit-tested on the JVM. */
internal object BackupStreams {
    const val MAX_IMPORT_BYTES = 32L * 1024 * 1024

    /**
     * Reads the WHOLE stream as UTF-8 (line breaks included; a BOM is dropped). Throws [BackupIoException] if the
     * stream cannot be opened, fails midway, or is larger than [maxBytes].
     */
    fun readText(open: () -> InputStream?, maxBytes: Long = MAX_IMPORT_BYTES): String {
        val input = try {
            open()
        } catch (e: IOException) {
            throw BackupIoException(UiText.of(Res.string.file_err_open), e)
        } catch (e: SecurityException) {
            throw BackupIoException(UiText.of(Res.string.file_err_read_denied), e)
        } ?: throw BackupIoException(UiText.of(Res.string.file_err_open))
        try {
            input.use {
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = it.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw BackupIoException(UiText.of(Res.string.file_err_too_large))
                    out.write(buffer, 0, read)
                }
                return out.toByteArray().decodeToString().removePrefix("\uFEFF")
            }
        } catch (e: IOException) {
            throw BackupIoException(UiText.of(Res.string.file_err_read_incomplete), e)
        }
    }

    /** Writes all [bytes] and flushes. Throws [BackupIoException] if the stream is unavailable or the write fails. */
    fun writeBytes(open: () -> OutputStream?, bytes: ByteArray) {
        val output = try {
            open()
        } catch (e: IOException) {
            throw BackupIoException(UiText.of(Res.string.file_err_create), e)
        } catch (e: SecurityException) {
            throw BackupIoException(UiText.of(Res.string.file_err_write_denied), e)
        } ?: throw BackupIoException(UiText.of(Res.string.file_err_create))
        try {
            output.use {
                it.write(bytes)
                it.flush()
            }
        } catch (e: IOException) {
            throw BackupIoException(UiText.of(Res.string.file_err_write_incomplete), e)
        }
    }
}
