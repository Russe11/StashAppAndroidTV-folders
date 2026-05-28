package com.github.damontecres.stashapp.folders.sync

import com.github.damontecres.stashapp.folders.data.FolderNode
import com.github.damontecres.stashapp.folders.data.FolderScene
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [LibraryIndexer.computeFolderNodes] — the direct-vs-recursive
 * folder-count rollup that the Folders/New experience is built on.
 *
 * These pin the fork's defining correctness property: a folder's `directCount`
 * (scenes whose `parentPath` is exactly that folder) is distinct from its
 * `recursiveCount` (scenes anywhere beneath it), and the New-feed "newest direct"
 * summary ignores nested scenes.
 */
class LibraryIndexerCountTest {
    @Test
    fun computeFolderNodes_directCountIsNotRecursiveCount() {
        val scenes =
            listOf(
                folderScene("1", "/a/top.mp4", updatedAtEpochMs = 100),
                folderScene("2", "/a/b/c/one.mp4", updatedAtEpochMs = 500),
                folderScene("3", "/a/b/d/two.mp4", updatedAtEpochMs = 900),
            )

        val nodes = LibraryIndexer.computeFolderNodes(SERVER, scenes).associateBy { it.path }

        assertEquals(setOf("/", "/a/", "/a/b/", "/a/b/c/", "/a/b/d/"), nodes.keys)

        // /a/ holds one *direct* scene but three scenes live somewhere beneath it.
        assertEquals(1, nodes.getValue("/a/").directCount)
        assertEquals(3, nodes.getValue("/a/").recursiveCount)

        // An intermediate folder with no direct scenes is still materialised, with
        // a direct count of 0 but a non-zero recursive count.
        assertEquals(0, nodes.getValue("/a/b/").directCount)
        assertEquals(2, nodes.getValue("/a/b/").recursiveCount)

        // Leaf folders: direct == recursive.
        assertEquals(1, nodes.getValue("/a/b/c/").directCount)
        assertEquals(1, nodes.getValue("/a/b/c/").recursiveCount)

        // Root aggregates everything recursively, holds nothing directly.
        assertEquals(0, nodes.getValue("/").directCount)
        assertEquals(3, nodes.getValue("/").recursiveCount)
    }

    @Test
    fun computeFolderNodes_setsNameAndParentPath() {
        val nodes =
            LibraryIndexer
                .computeFolderNodes(SERVER, listOf(folderScene("1", "/a/b/scene.mp4")))
                .associateBy { it.path }

        assertEquals("", nodes.getValue("/").name)
        assertEquals("", nodes.getValue("/").parentPath)
        assertEquals("a", nodes.getValue("/a/").name)
        assertEquals("/", nodes.getValue("/a/").parentPath)
        assertEquals("b", nodes.getValue("/a/b/").name)
        assertEquals("/a/", nodes.getValue("/a/b/").parentPath)
    }

    @Test
    fun computeFolderNodes_newestDirectIgnoresNestedScenes() {
        // /a/ has a single direct scene at t=100; its descendants are newer
        // (t=500, t=900) but must not bump /a/'s newest-direct timestamp.
        val scenes =
            listOf(
                folderScene("1", "/a/top.mp4", updatedAtEpochMs = 100),
                folderScene("2", "/a/b/c/one.mp4", updatedAtEpochMs = 500),
                folderScene("3", "/a/b/d/two.mp4", updatedAtEpochMs = 900),
            )

        val nodes = LibraryIndexer.computeFolderNodes(SERVER, scenes).associateBy { it.path }

        assertEquals(100, nodes.getValue("/a/").newestDirectUpdatedAtEpochMs)
        // Folders with no direct scenes have no newest-direct timestamp.
        assertEquals(0, nodes.getValue("/").newestDirectUpdatedAtEpochMs)
        assertEquals(0, nodes.getValue("/a/b/").newestDirectUpdatedAtEpochMs)
    }

    @Test
    fun computeFolderNodes_emptyInputProducesNoNodes() {
        assertEquals(emptyList<FolderNode>(), LibraryIndexer.computeFolderNodes(SERVER, emptyList()))
    }

    private fun folderScene(
        sceneId: String,
        path: String,
        organized: Boolean = false,
        screenshotUrl: String? = null,
        updatedAtEpochMs: Long = 0,
    ) = FolderScene(
        serverUrl = SERVER,
        sceneId = sceneId,
        path = path,
        parentPath = LibraryIndexer.parentPathOf(path),
        title = null,
        durationSeconds = null,
        rating100 = null,
        organized = organized,
        screenshotUrl = screenshotUrl,
        previewUrl = null,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private companion object {
        const val SERVER = "https://stash.example.test"
    }
}
