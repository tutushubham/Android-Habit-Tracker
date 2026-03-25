package com.tutushubham.pokidex.core.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tutushubham.pokidex.core.data.AnchorEntity
import com.tutushubham.pokidex.core.data.ArtifactEntity
import com.tutushubham.pokidex.core.data.CaptureEntity
import com.tutushubham.pokidex.core.data.DailyFocusOverrideEntity
import com.tutushubham.pokidex.core.data.DomainBehaviorProfileEntity
import com.tutushubham.pokidex.core.data.DomainFocusConfigEntity
import com.tutushubham.pokidex.core.data.FocusEntity
import com.tutushubham.pokidex.core.data.IntentEntity
import com.tutushubham.pokidex.core.data.SessionEntity
import com.tutushubham.pokidex.core.data.SignalEntity
import com.tutushubham.pokidex.core.data.UserIntentStatsEntity
import com.tutushubham.pokidex.core.data.local.db.dao.AnchorDao
import com.tutushubham.pokidex.core.data.local.db.dao.CaptureDao
import com.tutushubham.pokidex.core.data.local.db.dao.DailyFocusOverrideDao
import com.tutushubham.pokidex.core.data.local.db.dao.DomainBehaviorProfileDao
import com.tutushubham.pokidex.core.data.local.db.dao.DomainFocusConfigDao
import com.tutushubham.pokidex.core.data.local.db.dao.FocusDao
import com.tutushubham.pokidex.core.data.local.db.dao.IntentDao
import com.tutushubham.pokidex.core.data.local.db.dao.SessionDao
import com.tutushubham.pokidex.core.data.local.db.dao.UserIntentStatsDao

@Database(
    entities = [
        SessionEntity::class,
        IntentEntity::class,
        AnchorEntity::class,
        CaptureEntity::class,
        ArtifactEntity::class,
        SignalEntity::class,
        FocusEntity::class,
        DomainFocusConfigEntity::class,
        DailyFocusOverrideEntity::class,
        UserIntentStatsEntity::class,
        DomainBehaviorProfileEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun intentDao(): IntentDao
    abstract fun anchorDao(): AnchorDao
    abstract fun captureDao(): CaptureDao
    abstract fun focusDao(): FocusDao
    abstract fun domainFocusConfigDao(): DomainFocusConfigDao
    abstract fun dailyFocusOverrideDao(): DailyFocusOverrideDao
    abstract fun userIntentStatsDao(): UserIntentStatsDao
    abstract fun domainBehaviorProfileDao(): DomainBehaviorProfileDao
}
