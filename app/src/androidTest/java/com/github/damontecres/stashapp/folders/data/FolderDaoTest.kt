package com.github.damontecres.stashapp.folders.data

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.damontecres.stashapp.data.room.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: FolderDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.folderDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun pagingScenesInFolder_excludesNestedAndSiblingScenes() =
        runBlocking {
            val serverUrl = "https://stash.example.test"
            dao.upsertScenes(
                listOf(
                    folderScene(serverUrl, sceneId = "1", path = "/Movies/direct.mp4", parentPath = "/Movies/"),
                    folderScene(serverUrl, sceneId = "2", path = "/Movies/Sub/nested.mp4", parentPath = "/Movies/Sub/"),
                    folderScene(serverUrl, sceneId = "3", path = "/Movies2/sibling.mp4", parentPath = "/Movies2/"),
                ),
            )

            val result =
                dao
                    .pagingScenesInFolder(serverUrl = serverUrl, parentPath = "/Movies/", tagIdFilter = null)
                    .load(
                        PagingSource.LoadParams.Refresh(
                            key = null,
                            loadSize = 10,
                            placeholdersEnabled = false,
                        ),
                    )

            val page = result as PagingSource.LoadResult.Page<Int, FolderScene>
            assertEquals(listOf("/Movies/direct.mp4"), page.data.map { it.path })
        }

    @Test
    fun pagingNewestItems_mixesScenesAndDirectFoldersNewestFirst() =
        runBlocking {
            val serverUrl = "https://stash.example.test"
            dao.upsertFolders(
                listOf(
                    folderNode(
                        serverUrl,
                        path = "/Movies/",
                        name = "Movies",
                        parentPath = "/",
                        directCount = 1,
                        newestDirectUpdatedAtEpochMs = 300,
                    ),
                    folderNode(
                        serverUrl,
                        path = "/Movies/Sub/",
                        name = "Sub",
                        parentPath = "/Movies/",
                        directCount = 1,
                        newestDirectUpdatedAtEpochMs = 500,
                    ),
                    folderNode(serverUrl, path = "/OnlyNested/", name = "OnlyNested", parentPath = "/", directCount = 0),
                ),
            )
            dao.upsertScenes(
                listOf(
                    folderScene(serverUrl, sceneId = "1", path = "/Movies/direct.mp4", parentPath = "/Movies/", updatedAtEpochMs = 300),
                    folderScene(serverUrl, sceneId = "2", path = "/Movies/Sub/nested.mp4", parentPath = "/Movies/Sub/", updatedAtEpochMs = 500),
                    folderScene(serverUrl, sceneId = "3", path = "/OnlyNested/Sub/nested.mp4", parentPath = "/OnlyNested/Sub/", updatedAtEpochMs = 700),
                    folderScene(serverUrl, sceneId = "4", path = "/Loose/video.mp4", parentPath = "/Loose/", updatedAtEpochMs = 400),
                ),
            )

            val result =
                dao
                    .pagingNewestItems(serverUrl = serverUrl)
                    .load(
                        PagingSource.LoadParams.Refresh(
                            key = null,
                            loadSize = 20,
                            placeholdersEnabled = false,
                        ),
                    )

            val page = result as PagingSource.LoadResult.Page<Int, NewItemRow>
            assertEquals(
                listOf(
                    "scene:/OnlyNested/Sub/nested.mp4:700",
                    "folder:/Movies/Sub/:500",
                    "scene:/Movies/Sub/nested.mp4:500",
                    "scene:/Loose/video.mp4:400",
                    "folder:/Movies/:300",
                    "scene:/Movies/direct.mp4:300",
                ),
                page.data.map { "${it.itemType}:${it.path}:${it.updatedAtEpochMs}" },
            )
        }

    @Test
    fun pagingNewestItems_usesMaterializedFolderRecencyAndThumbnail() =
        runBlocking {
            val serverUrl = "https://stash.example.test"
            dao.upsertFolders(
                listOf(
                    folderNode(
                        serverUrl,
                        path = "/Movies/",
                        name = "Movies",
                        parentPath = "/",
                        directCount = 1,
                        newestDirectUpdatedAtEpochMs = 900,
                        newestDirectThumbnailUrl = "materialized.jpg",
                    ),
                ),
            )
            dao.upsertScenes(
                listOf(
                    folderScene(
                        serverUrl,
                        sceneId = "1",
                        path = "/Movies/direct.mp4",
                        parentPath = "/Movies/",
                        updatedAtEpochMs = 100,
                    ),
                ),
            )

            val result =
                dao
                    .pagingNewestItems(serverUrl = serverUrl)
                    .load(
                        PagingSource.LoadParams.Refresh(
                            key = null,
                            loadSize = 20,
                            placeholdersEnabled = false,
                        ),
                    )

            val page = result as PagingSource.LoadResult.Page<Int, NewItemRow>
            val folder = page.data.first { it.itemType == NewItemRow.TYPE_FOLDER }
            assertEquals(900, folder.updatedAtEpochMs)
            assertEquals("materialized.jpg", folder.thumbnailUrl)
        }

    @Test
    fun pagingNewFolderItems_showsImmediateFoldersAndDirectScenesOnly() =
        runBlocking {
            val serverUrl = "https://stash.example.test"
            dao.upsertFolders(
                listOf(
                    folderNode(serverUrl, path = "/Movies/Alpha/", name = "Alpha", parentPath = "/Movies/", directCount = 0),
                    folderNode(
                        serverUrl,
                        path = "/Movies/Beta/",
                        name = "Beta",
                        parentPath = "/Movies/",
                        directCount = 1,
                        newestDirectUpdatedAtEpochMs = 500,
                    ),
                    folderNode(
                        serverUrl,
                        path = "/Movies/Beta/Nested/",
                        name = "Nested",
                        parentPath = "/Movies/Beta/",
                        directCount = 1,
                        newestDirectUpdatedAtEpochMs = 500,
                    ),
                    folderNode(
                        serverUrl,
                        path = "/Other/",
                        name = "Other",
                        parentPath = "/",
                        directCount = 1,
                        newestDirectUpdatedAtEpochMs = 600,
                    ),
                ),
            )
            dao.upsertScenes(
                listOf(
                    folderScene(serverUrl, sceneId = "1", path = "/Movies/direct-b.mp4", parentPath = "/Movies/", updatedAtEpochMs = 300),
                    folderScene(serverUrl, sceneId = "2", path = "/Movies/direct-a.mp4", parentPath = "/Movies/", updatedAtEpochMs = 400),
                    folderScene(serverUrl, sceneId = "3", path = "/Movies/Beta/nested.mp4", parentPath = "/Movies/Beta/", updatedAtEpochMs = 500),
                    folderScene(serverUrl, sceneId = "4", path = "/Other/video.mp4", parentPath = "/Other/", updatedAtEpochMs = 600),
                ),
            )

            val result =
                dao
                    .pagingNewFolderItems(serverUrl = serverUrl, parentPath = "/Movies/")
                    .load(
                        PagingSource.LoadParams.Refresh(
                            key = null,
                            loadSize = 20,
                            placeholdersEnabled = false,
                        ),
                    )

            val page = result as PagingSource.LoadResult.Page<Int, NewItemRow>
            assertEquals(
                listOf(
                    "folder:/Movies/Alpha/:0",
                    "folder:/Movies/Beta/:500",
                    "scene:/Movies/direct-a.mp4:400",
                    "scene:/Movies/direct-b.mp4:300",
                ),
                page.data.map { "${it.itemType}:${it.path}:${it.updatedAtEpochMs}" },
            )
        }

    private fun folderNode(
        serverUrl: String,
        path: String,
        name: String,
        parentPath: String,
        directCount: Int,
        newestDirectUpdatedAtEpochMs: Long = 0,
        newestDirectThumbnailUrl: String? = null,
    ): FolderNode =
        FolderNode(
            serverUrl = serverUrl,
            path = path,
            name = name,
            parentPath = parentPath,
            recursiveCount = directCount,
            directCount = directCount,
            thumbnailUrl = null,
            newestDirectUpdatedAtEpochMs = newestDirectUpdatedAtEpochMs,
            newestDirectThumbnailUrl = newestDirectThumbnailUrl,
        )

    private fun folderScene(
        serverUrl: String,
        sceneId: String,
        path: String,
        parentPath: String,
        updatedAtEpochMs: Long = sceneId.toLong(),
    ): FolderScene =
        FolderScene(
            serverUrl = serverUrl,
            sceneId = sceneId,
            path = path,
            parentPath = parentPath,
            title = null,
            durationSeconds = null,
            rating100 = null,
            organized = false,
            screenshotUrl = null,
            previewUrl = null,
            updatedAtEpochMs = updatedAtEpochMs,
        )
}
