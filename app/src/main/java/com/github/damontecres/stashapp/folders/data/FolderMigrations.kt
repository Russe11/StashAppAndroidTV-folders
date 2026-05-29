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
 * All Folders migrations are wired into the database builder in `StashApplication.kt`
 * via `.addMigrations(...)`. Keep that list in sync when adding a migration here,
 * otherwise the version bump falls through to `fallbackToDestructiveMigration()` and
 * users lose recent searches and playback effects on upgrade.
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

/**
 * Adds direct-video summary fields to `folders` for the New feed.
 *
 * The New feed should sort folder rows by the newest direct video in that folder
 * and show that video's thumbnail. Materialising the values during sync avoids
 * two correlated ordered lookups into `folder_scenes` for every folder row while
 * paging the feed.
 */
val MIGRATION_8_TO_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `folders` ADD COLUMN `newestDirectUpdatedAtEpochMs` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `folders` ADD COLUMN `newestDirectThumbnailUrl` TEXT")
            db.execSQL(
                "UPDATE `folders` SET `newestDirectUpdatedAtEpochMs` = " +
                    "COALESCE((SELECT s.`updatedAtEpochMs` FROM `folder_scenes` s " +
                    "WHERE s.`serverUrl` = `folders`.`serverUrl` AND s.`parentPath` = `folders`.`path` " +
                    "ORDER BY s.`updatedAtEpochMs` DESC, s.`path` COLLATE NOCASE ASC LIMIT 1), 0)",
            )
            db.execSQL(
                "UPDATE `folders` SET `newestDirectThumbnailUrl` = " +
                    "(SELECT s.`screenshotUrl` FROM `folder_scenes` s " +
                    "WHERE s.`serverUrl` = `folders`.`serverUrl` AND s.`parentPath` = `folders`.`path` " +
                    "ORDER BY s.`updatedAtEpochMs` DESC, s.`path` COLLATE NOCASE ASC LIMIT 1)",
            )
        }
    }

/**
 * Indexes the New feed's recency sort and prunes redundant single-column indexes.
 *
 *   - Adds `folder_scenes(serverUrl, updatedAtEpochMs)`: the New feed's scene arm
 *     sorts by `updatedAtEpochMs DESC` within a server. Without this index SQLite
 *     materialises and sorts the whole scene table on each page load.
 *   - Drops the bare `folder_scenes(path)`, `folder_scenes(parentPath)` and
 *     `folders(parentPath)` indexes. Every query that touches those columns also
 *     filters by `serverUrl`, so the `serverUrl`-prefixed composite indexes already
 *     cover them; the bare indexes only added maintenance cost to each sync upsert.
 *
 * The CREATE/DROP set here must produce exactly the index set Room derives from the
 * v10 @Entity classes (see `app/schemas/...AppDatabase/10.json`).
 */
val MIGRATION_9_TO_10 =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_folder_scenes_serverUrl_updatedAtEpochMs` " +
                    "ON `folder_scenes` (`serverUrl`, `updatedAtEpochMs`)",
            )
            db.execSQL("DROP INDEX IF EXISTS `index_folder_scenes_path`")
            db.execSQL("DROP INDEX IF EXISTS `index_folder_scenes_parentPath`")
            db.execSQL("DROP INDEX IF EXISTS `index_folders_parentPath`")
        }
    }

/**
 * Stops caching absolute media URLs. Before v11, `folder_scenes.screenshotUrl/previewUrl` and
 * `folders.thumbnailUrl/newestDirectThumbnailUrl` stored server-rooted absolute URLs, which
 * went stale after a server move and leaked one server's origin onto another's rows. The cache
 * now stores only the **scene id** (+ that scene's `updated_at`); URLs are rebuilt at render
 * time by `SceneUrlBuilder` against the current server root.
 *
 *   - `folder_scenes`: drop `screenshotUrl` / `previewUrl` (recreate the table without them,
 *     copying the retained columns).
 *   - `folders`: drop `thumbnailUrl` / `newestDirectThumbnailUrl`; add `thumbnailSceneId` +
 *     `thumbnailUpdatedAtEpochMs` and `newestDirectSceneId`.
 *
 * The dropped URLs can't be reverse-mapped to scene ids, so the new id columns can't be
 * back-filled here. We clear `folder_sync_state` instead, which makes the next sync run a fresh
 * full scan that repopulates the id-based thumbnail columns from the server. (Scene rows are
 * preserved so the user still sees a populated library while the re-scan runs.)
 *
 * The CREATE/ALTER set here must produce exactly the index/column set Room derives from the v11
 * @Entity classes (see `app/schemas/...AppDatabase/11.json`).
 *
 * WIRING REQUIRED: pass to the database builder in `StashApplication.kt`:
 *
 *     .addMigrations(..., MIGRATION_9_TO_10, MIGRATION_10_TO_11)
 */
val MIGRATION_10_TO_11 =
    object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // -- folder_scenes: drop the two URL columns by recreating the table. --
            db.execSQL(
                "CREATE TABLE `folder_scenes_new` (" +
                    "`serverUrl` TEXT NOT NULL, " +
                    "`sceneId` TEXT NOT NULL, " +
                    "`path` TEXT NOT NULL, " +
                    "`parentPath` TEXT NOT NULL, " +
                    "`title` TEXT, " +
                    "`durationSeconds` REAL, " +
                    "`rating100` INTEGER, " +
                    "`organized` INTEGER NOT NULL, " +
                    "`tagIdsJson` TEXT NOT NULL DEFAULT '[]', " +
                    "`updatedAtEpochMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`serverUrl`, `sceneId`))",
            )
            db.execSQL(
                "INSERT INTO `folder_scenes_new` (" +
                    "`serverUrl`, `sceneId`, `path`, `parentPath`, `title`, `durationSeconds`, " +
                    "`rating100`, `organized`, `tagIdsJson`, `updatedAtEpochMs`) " +
                    "SELECT `serverUrl`, `sceneId`, `path`, `parentPath`, `title`, `durationSeconds`, " +
                    "`rating100`, `organized`, `tagIdsJson`, `updatedAtEpochMs` FROM `folder_scenes`",
            )
            db.execSQL("DROP TABLE `folder_scenes`")
            db.execSQL("ALTER TABLE `folder_scenes_new` RENAME TO `folder_scenes`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_folder_scenes_serverUrl_parentPath` " +
                    "ON `folder_scenes` (`serverUrl`, `parentPath`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_folder_scenes_serverUrl_path` " +
                    "ON `folder_scenes` (`serverUrl`, `path`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_folder_scenes_serverUrl_updatedAtEpochMs` " +
                    "ON `folder_scenes` (`serverUrl`, `updatedAtEpochMs`)",
            )

            // -- folders: drop the URL columns, add the id columns. --
            db.execSQL(
                "CREATE TABLE `folders_new` (" +
                    "`serverUrl` TEXT NOT NULL, " +
                    "`path` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`parentPath` TEXT NOT NULL, " +
                    "`recursiveCount` INTEGER NOT NULL, " +
                    "`directCount` INTEGER NOT NULL, " +
                    "`thumbnailSceneId` TEXT, " +
                    "`thumbnailUpdatedAtEpochMs` INTEGER NOT NULL DEFAULT 0, " +
                    "`newestDirectUpdatedAtEpochMs` INTEGER NOT NULL DEFAULT 0, " +
                    "`newestDirectSceneId` TEXT, " +
                    "PRIMARY KEY(`serverUrl`, `path`))",
            )
            db.execSQL(
                "INSERT INTO `folders_new` (" +
                    "`serverUrl`, `path`, `name`, `parentPath`, `recursiveCount`, `directCount`, " +
                    "`newestDirectUpdatedAtEpochMs`) " +
                    "SELECT `serverUrl`, `path`, `name`, `parentPath`, `recursiveCount`, `directCount`, " +
                    "`newestDirectUpdatedAtEpochMs` FROM `folders`",
            )
            db.execSQL("DROP TABLE `folders`")
            db.execSQL("ALTER TABLE `folders_new` RENAME TO `folders`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_folders_serverUrl_parentPath` " +
                    "ON `folders` (`serverUrl`, `parentPath`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_folders_serverUrl_name` " +
                    "ON `folders` (`serverUrl`, `name`)",
            )

            // -- folder_sync_state: add the deletedSince resume cursor column. --
            db.execSQL("ALTER TABLE `folder_sync_state` ADD COLUMN `deletedSinceCursor` TEXT DEFAULT NULL")

            // The dropped URLs can't be reverse-mapped to scene ids; force a fresh full scan so
            // the next sync repopulates the id-based thumbnail columns from the server.
            db.execSQL("DELETE FROM `folder_sync_state`")
        }
    }
