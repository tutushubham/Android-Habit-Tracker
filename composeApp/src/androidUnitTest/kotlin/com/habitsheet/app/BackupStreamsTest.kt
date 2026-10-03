package com.habitsheet.app

import com.habitsheet.domain.backup.BackupFixtures
import com.habitsheet.domain.backup.BackupSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupStreamsTest {
    private fun read(text: String, maxBytes: Long = BackupStreams.MAX_IMPORT_BYTES) =
        BackupStreams.readText({ ByteArrayInputStream(text.encodeToByteArray()) }, maxBytes)

    @Test
    fun readsTheWholeFileIncludingLineBreaksSoPrettyPrintedBackupsParse() {
        // The old reader joined lines without "\n": multi-line string values and the checksum input were corrupted.
        val text = BackupFixtures.V4
        assertTrue('\n' in text)
        assertEquals(text, read(text))
        assertEquals(BackupSerializer.parse(BackupFixtures.V4), BackupSerializer.parse(read(text)))
    }

    @Test
    fun keepsLineBreaksInsideValuesAndNonAsciiText() {
        val text = "line one\nline two\r\nünïcödé ✓ 日本語"
        assertEquals(text, read(text))
    }

    @Test
    fun dropsAByteOrderMarkSoEditorsThatAddOneStillWork() {
        assertEquals("{}", read("﻿{}"))
    }

    @Test
    fun readsFilesLargerThanTheInternalBuffer() {
        val text = "x".repeat(200_000)
        assertEquals(text, read(text))
    }

    @Test
    fun filesOverTheLimitAreRefusedInsteadOfExhaustingMemory() {
        val error = assertFailsWith<BackupIoException> { read("x".repeat(100), maxBytes = 99) }
        assertTrue("too large" in error.userMessage)
        assertEquals("x".repeat(99), read("x".repeat(99), maxBytes = 99))
    }

    @Test
    fun aNullInputStreamIsAUserVisibleFailureNotASilentEmptyImport() {
        val error = assertFailsWith<BackupIoException> { BackupStreams.readText({ null }) }
        assertEquals("Couldn't open that file.", error.userMessage)
    }

    @Test
    fun openingOrReadingFailuresBecomeFailures() {
        assertFailsWith<BackupIoException> { BackupStreams.readText({ throw IOException("gone") }) }
        assertFailsWith<BackupIoException> { BackupStreams.readText({ throw SecurityException("no permission") }) }
        val brokenMidway = object : InputStream() {
            private var sent = false
            override fun read(): Int = throw IOException("unused")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!sent) { sent = true; b[off] = 'a'.code.toByte(); return 1 }
                throw IOException("connection to the cloud drive lost")
            }
        }
        val error = assertFailsWith<BackupIoException> { BackupStreams.readText({ brokenMidway }) }
        assertTrue("completely" in error.userMessage)
    }

    @Test
    fun writesEveryByteAndClosesTheStream() {
        var closed = false
        val sink = object : ByteArrayOutputStream() {
            override fun close() { closed = true; super.close() }
        }
        BackupStreams.writeBytes({ sink }, "héllo\nworld".encodeToByteArray())
        assertEquals("héllo\nworld", sink.toByteArray().decodeToString())
        assertTrue(closed)
    }

    @Test
    fun aNullOutputStreamIsAFailureNotASilentSuccess() {
        val error = assertFailsWith<BackupIoException> { BackupStreams.writeBytes({ null }, byteArrayOf(1)) }
        assertEquals("Couldn't create the file.", error.userMessage)
    }

    @Test
    fun writeFailuresAreReported() {
        val full = object : OutputStream() {
            override fun write(b: Int) = throw IOException("No space left on device")
        }
        val error = assertFailsWith<BackupIoException> { BackupStreams.writeBytes({ full }, ByteArray(10)) }
        assertTrue("storage" in error.userMessage)
        assertFailsWith<BackupIoException> { BackupStreams.writeBytes({ throw SecurityException("denied") }, ByteArray(1)) }
    }
}
