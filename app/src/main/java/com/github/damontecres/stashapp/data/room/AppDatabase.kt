package com.github.damontecres.stashapp.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.damontecres.stashapp.folders.data.FolderDao
import com.github.damontecres.stashapp.folders.data.FolderNode
import com.github.damontecres.stashapp.folders.data.FolderScene
import com.github.damontecres.stashapp.folders.data.FolderSyncState

// Schema v6 adds the Folders destination tables (folder_scenes, folders,
// folder_sync_state) via MIGRATION_5_TO_6. StashApplication is responsible for
// wiring that migration into the database builder; if a device skips it,
// fallbackToDestructiveMigration() is already configured app-side as a safety net.
@Database(
    entities = [
        RecentSearchItem::class,
        PlaybackEffect::class,
        FolderScene::class,
        FolderNode::class,
        FolderSyncState::class,
    ],
    version = 6,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentSearchItemsDao(): RecentSearchItemsDao

    abstract fun playbackEffectsDao(): PlaybackEffectsDao

    abstract fun folderDao(): FolderDao
}
