package com.habitsheet.data

import com.habitsheet.domain.repository.HabitRepository

/** The in-memory double must behave like SQLite for everything the ViewModel tests rely on. */
class InMemoryRepositoryContractTest : HabitRepositoryContract() {
    override suspend fun withRepository(block: suspend (RepositoryHandle) -> Unit) {
        val repository = InMemoryHabitRepository()
        block(
            object : RepositoryHandle {
                override val repository: HabitRepository = repository
                override suspend fun reopen(): HabitRepository = repository
            },
        )
    }
}
