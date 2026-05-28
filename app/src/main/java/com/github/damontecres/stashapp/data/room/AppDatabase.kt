package com.github.damontecres.stashapp.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.damontecres.stashapp.folders.data.FolderDao
import com.github.damontecres.stashapp.folders.data.FolderNode
import com.github.damontecres.stashapp.folders.data.FolderScene
import com.github.damontecres.stashapp.folders.data.FolderSyncState

// Schema v6 adds the Folders destination tables (folder_scenes, folders,
// folder_sync_state) via MIGRATION_5_TO_6. v7 adds two composite indexes
// (folder_scenes.serverUrl+path, folders.serverUrl+name). v8 adds
// folders.thumbnailUrl so the child-folder query no longer runs a correlated
// scene lookup per folder row. v9 adds direct-video summary columns for the
// New feed. v10 adds folder_scenes(serverUrl, updatedAtEpochMs) for the New
// feed sort and drops three redundant single-column indexes (folder_scenes.path,
// folder_scenes.parentPath, folders.parentPath) that are fully covered by their
// serverUrl-prefixed composites. StashApplication wires those migrations into the
// database builder; fallbackToDestructiveMigration() remains app-side as a safety net.
@Database(
    entities = [
        RecentSearchItem::class,
        PlaybackEffect::class,
        FolderScene::class,
        FolderNode::class,
        FolderSyncState::class,
    ],
    version = 10,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentSearchItemsDao(): RecentSearchItemsDao

    abstract fun playbackEffectsDao(): PlaybackEffectsDao

    abstract fun folderDao(): FolderDao
}
