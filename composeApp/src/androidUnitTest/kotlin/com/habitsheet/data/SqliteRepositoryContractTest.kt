package com.habitsheet.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.repository.HabitRepository
import java.nio.file.Files

/** The shared repository contract against a real SQLite file; `reopen()` closes it and opens the file again. */
class SqliteRepositoryContractTest : HabitRepositoryContract() {
    override suspend fun withRepository(block: suspend (RepositoryHandle) -> Unit) {
        val file = Files.createTempFile("habit-sheet-contract", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"
        JdbcSqliteDriver(url).also { HabitsDatabase.Schema.create(it) }.close()
        val opened = mutableListOf<LocalHabitRepository>()
        fun open() = LocalHabitRepository({ JdbcSqliteDriver(url) }).also { opened += it }
        val handle = object : RepositoryHandle {
            private var current = open()
            override val repository: HabitRepository get() = current
            override suspend fun reopen(): HabitRepository {
                current.close()
                current = open()
                return current
            }
        }
        try {
            block(handle)
        } finally {
            opened.forEach { runCatching { it.close() } }
            Files.deleteIfExists(file)
        }
    }
}
