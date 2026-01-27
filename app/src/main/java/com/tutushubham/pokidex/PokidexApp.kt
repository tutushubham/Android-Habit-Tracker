package com.tutushubham.pokidex

import android.app.Application
import androidx.room.Room
import com.tutushubham.pokidex.core.data.local.db.AppDatabase

class PokidexApp : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "habit-tracker-db"
        )
            .fallbackToDestructiveMigration() // Only for development - remove in production
            .build()
    }
}
