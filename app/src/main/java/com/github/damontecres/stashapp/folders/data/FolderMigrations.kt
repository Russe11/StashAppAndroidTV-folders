package com.github.damontecres.stashapp.folders.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration that adds the Folders destination's three tables. This is strictly
 * additive — no existing tables are touched — so it's safe to run on any device
 * coming from schema v5.
 *
 * The CREATE TABLE statements here must stay byte-identical to what Room generates
 * from the @Entity classes; the generated schema JSON in `app/schemas/` is the
 * source of truth. If you edit an entity, regenerate the schema by running
 * `./gradlew :app:assembleDebug` and update these statements to match.
 *
 * WIRING REQUIRED: this migration must be passed to the database builder in
 * `StashApplication.kt`, alongside the existing `MIGRATION_4_TO_5`:
 *
 *     .addMigrations(MIGRATION_4_TO_5, MIGRATION_5_TO_6)
 *
 * Without that line, the v5→v6 bump will fall through to
 * `fallbackToDestructiveMigration()` and users will lose their recent searches and
 * playback effects on upgrade. This file was kept strict-additive on purpose; the
 * one-line edit to `StashApplication.kt` is owned by the sync-engine wiring task.
 */
val MIGRATION_5_TO_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // folder_scenes
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `folder_scenes` (" +
                    "`serverUrl` TEXT NOT NULL, " +
                    "`sceneId` TEXT NOT NULL, " +
                    "`path` TEXT NOT NULL, " +
                    "`parentPath` TEXT NOT NULL, " +
                    "`title` TEXT, " +
                    "`durationSeconds` REAL, " +
                    "`rating100` INTEGER, " +
                    "`organized` INTEGER NOT NULL, " +
                    "`screenshotUrl` TEXT, " +
                    "`previewUrl` TEXT, " +
                    "`tagIdsJson` TEXT NOT NULL DEFAULT '[]', " +
                    "`updatedAtEpochMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`serverUrl`, `sceneId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folder_scenes_path` ON `folder_scenes` (`path`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folder_scenes_parentPath` ON `folder_scenes` (`parentPath`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folder_scenes_serverUrl_parentPath` ON `folder_scenes` (`serverUrl`, `parentPath`)")

            // folders
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `folders` (" +
                    "`serverUrl` TEXT NOT NULL, " +
                    "`path` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`parentPath` TEXT NOT NULL, " +
                    "`recursiveCount` INTEGER NOT NULL, " +
                    "`directCount` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`serverUrl`, `path`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentPath` ON `folders` (`parentPath`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_serverUrl_parentPath` ON `folders` (`serverUrl`, `parentPath`)")

            // folder_sync_state
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `folder_sync_state` (" +
                    "`serverUrl` TEXT NOT NULL, " +
                    "`lastSyncAtEpochMs` INTEGER, " +
                    "`scanComplete` INTEGER NOT NULL, " +
                    "`scanProgressTotal` INTEGER, " +
                    "`scanProgressDone` INTEGER, " +
                    "PRIMARY KEY(`serverUrl`))",
            )
        }
    }

/**
 * Adds two composite indexes that the Folders queries already lean on:
 *
 *   - `folder_scenes(serverUrl, path)`: server-scoped path scans can avoid
 *     walking path rows from every configured server and then re-filtering
 *     by serverUrl in memory.
 *   - `folders(serverUrl, name)`: the left-pane subfolder list orders by
 *     `name COLLATE NOCASE`. Without this index Room builds a transient sort
 *     buffer for each folder change.
 *
 * Strictly additive. If this migration is skipped, queries still work — they
 * just fall back to the v6 indexes — so `fallbackToDestructiveMigration()` is
 * still acceptable as a safety net.
 *
 * WIRING REQUIRED: must be passed to the database builder in
 * `StashApplication.kt`:
 *
 *     .addMigrations(MIGRATION_4_TO_5, MIGRATION_5_TO_6, MIGRATION_6_TO_7)
 */
val MIGRATION_6_TO_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_folder_scenes_serverUrl_path` " +
                    "ON `folder_scenes` (`serverUrl`, `path`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_folders_serverUrl_name` " +
                    "ON `folders` (`serverUrl`, `name`)",
            )
        }
    }

/**
 * Adds a materialised representative thumbnail URL to `folders`.
 *
 * Before v8, the left-pane child query ran a correlated recursive lookup against
 * `folder_scenes` for every visible folder row. That made deep folder browsing
 * depend on repeated scene-table scans. The sync rebuild now computes the
 * representative thumbnail once and stores it on the folder row.
 */
val MIGRATION_7_TO_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `folders` ADD COLUMN `thumbnailUrl` TEXT")
        }
    }
