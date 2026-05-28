package com.github.damontecres.stashapp

import androidx.room.testing.MigrationTestHelper
import androidx.room.util.useCursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.data.room.AppDatabase
import com.github.damontecres.stashapp.data.room.MIGRATION_4_TO_5
import com.github.damontecres.stashapp.folders.data.MIGRATION_7_TO_8
import com.github.damontecres.stashapp.folders.data.MIGRATION_8_TO_9
import com.github.damontecres.stashapp.folders.data.MIGRATION_9_TO_10
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class TestDbMigrations {
    private val testDbName = "migration-test"

    private val itemId = "123"
    private val blurValue = 50

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    @Throws(IOException::class)
    fun migrate4To5() {
        helper.createDatabase(testDbName, 4).apply {
            execSQL(
                "INSERT INTO playback_effects VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    "https://server",
                    itemId,
                    90,
                    100,
                    100,
                    100,
                    200,
                    75,
                    100,
                    100,
                    blurValue,
                ),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 5, true, MIGRATION_4_TO_5)

        // MigrationTestHelper automatically verifies the schema changes,
        // but you need to validate that the data was migrated properly.
        db.query("SELECT dataType, id, blur FROM playback_effects").useCursor { c ->
            c.moveToFirst()
            Assert.assertEquals(DataType.SCENE.ordinal, c.getInt(0))
            Assert.assertEquals(itemId, c.getString(1))
            Assert.assertEquals(blurValue, c.getInt(2))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8_addsFolderThumbnailUrl() {
        helper.createDatabase(testDbName, 7).apply {
            execSQL(
                "INSERT INTO folders VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    "https://server",
                    "/Movies/",
                    "Movies",
                    "/",
                    2,
                    0,
                ),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 8, true, MIGRATION_7_TO_8)

        db.query("SELECT thumbnailUrl FROM folders WHERE serverUrl = ? AND path = ?", arrayOf("https://server", "/Movies/")).useCursor { c ->
            c.moveToFirst()
            Assert.assertTrue(c.isNull(0))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate8To9_addsNewFeedMaterializedFolderFields() {
        helper.createDatabase(testDbName, 8).apply {
            execSQL(
                "INSERT INTO folders VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    "https://server",
                    "/Movies/",
                    "Movies",
                    "/",
                    2,
                    1,
                    "thumb.jpg",
                ),
            )
            execSQL(
                "INSERT INTO folder_scenes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "https://server",
                    "123",
                    "/Movies/newest.mp4",
                    "/Movies/",
                    "Newest",
                    null,
                    null,
                    0,
                    "direct-thumb.jpg",
                    null,
                    "[]",
                    12345L,
                ),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 9, true, MIGRATION_8_TO_9)

        db.query(
            "SELECT newestDirectUpdatedAtEpochMs, newestDirectThumbnailUrl FROM folders WHERE serverUrl = ? AND path = ?",
            arrayOf("https://server", "/Movies/"),
        ).useCursor { c ->
            c.moveToFirst()
            Assert.assertEquals(12345L, c.getLong(0))
            Assert.assertEquals("direct-thumb.jpg", c.getString(1))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate9To10_reindexesNewFeedAndKeepsData() {
        helper.createDatabase(testDbName, 9).apply {
            execSQL(
                "INSERT INTO folder_scenes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "https://server",
                    "123",
                    "/Movies/newest.mp4",
                    "/Movies/",
                    "Newest",
                    null,
                    null,
                    0,
                    "direct-thumb.jpg",
                    null,
                    "[]",
                    12345L,
                ),
            )
            close()
        }

        // runMigrationsAndValidate asserts the resulting schema (including the new
        // serverUrl+updatedAtEpochMs index and the three dropped single-column
        // indexes) matches the generated 10.json.
        val db = helper.runMigrationsAndValidate(testDbName, 10, true, MIGRATION_9_TO_10)

        db.query("SELECT sceneId, updatedAtEpochMs FROM folder_scenes WHERE serverUrl = ?", arrayOf("https://server")).useCursor { c ->
            c.moveToFirst()
            Assert.assertEquals("123", c.getString(0))
            Assert.assertEquals(12345L, c.getLong(1))
        }
    }
}
