package com.github.damontecres.stashapp.folders.sync

import com.github.damontecres.stashapp.folders.data.FolderScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [LibraryIndexer]'s path canonicalization helpers.
 *
 * Regression coverage for the `/prv/docs/` empty-grid bug, which was caused by
 * [LibraryIndexer.normalizeFilePath] preserving the (missing) leading slash
 * shape from Stash, while [LibraryIndexer.parentPathOf] always prepended one.
 * The folder browser then had inconsistent canonical paths in its local cache.
 */
class LibraryIndexerPathTest {
    @Test
    fun normalizeFilePath_prependsLeadingSlashWhenMissing() {
        // The bug: Stash returned `prv/docs/foo.mp4` for some scenes (no
        // leading slash). Before the fix, normalize was a no-op for this case
        // and folder browsing missed every such scene.
        assertEquals("/prv/docs/foo.mp4", LibraryIndexer.normalizeFilePath("prv/docs/foo.mp4"))
    }

    @Test
    fun normalizeFilePath_keepsExistingLeadingSlash() {
        assertEquals("/prn/foo.mp4", LibraryIndexer.normalizeFilePath("/prn/foo.mp4"))
    }

    @Test
    fun normalizeFilePath_collapsesRepeatedSlashes() {
        assertEquals("/a/b/c.mp4", LibraryIndexer.normalizeFilePath("//a///b/c.mp4"))
    }

    @Test
    fun normalizeFilePath_convertsBackslashes() {
        // Stash on Windows hosts reports backslash-separated paths.
        assertEquals("/C:/Media/foo.mp4", LibraryIndexer.normalizeFilePath("C:\\Media\\foo.mp4"))
    }

    @Test
    fun normalizeFilePath_returnsEmptyForBlank() {
        assertEquals("", LibraryIndexer.normalizeFilePath(""))
        assertEquals("", LibraryIndexer.normalizeFilePath("   "))
    }

    @Test
    fun parentPathOf_matchesNormalizedPath_forSlashlessSource() {
        // The key invariant for the folder query to work:
        // normalize() and parentPathOf() must produce coherent prefixes —
        // i.e. parentPath must always be a prefix of path.
        val normalized = LibraryIndexer.normalizeFilePath("prv/docs/foo.mp4")
        val parent = LibraryIndexer.parentPathOf(normalized)
        assertEquals("/prv/docs/", parent)
        // Folder rows and scene rows share this canonical prefix contract.
        assert(normalized.startsWith(parent)) {
            "normalized path $normalized must start with parentPath $parent"
        }
    }

    @Test
    fun parentPathOf_rootForFileAtRoot() {
        assertEquals("/", LibraryIndexer.parentPathOf("/foo.mp4"))
    }

    @Test
    fun parentFolderOf_movesUpOneLevel() {
        assertEquals("/a/", LibraryIndexer.parentFolderOf("/a/b/"))
        assertEquals("/", LibraryIndexer.parentFolderOf("/a/"))
        assertEquals("/", LibraryIndexer.parentFolderOf("/"))
    }

    @Test
    fun folderName_lastSegment() {
        assertEquals("docs", LibraryIndexer.folderName("/prv/docs/"))
        assertEquals("", LibraryIndexer.folderName("/"))
    }

    @Test
    fun representativeThumbnail_prefersOrganizedSceneThenLowestNumericId() {
        // The representative thumbnail is now the *scene id* (URL rebuilt at render time);
        // selection still prefers organized scenes, then the lowest numeric id.
        val rows =
            listOf(
                folderScene(sceneId = "20", path = "/root/a/scene20.mp4", organized = false),
                folderScene(sceneId = "7", path = "/root/a/scene7.mp4", organized = true),
                folderScene(sceneId = "3", path = "/root/a/nested/scene3.mp4", organized = true),
            )

        assertEquals("3", LibraryIndexer.representativeThumbnailSceneIdFor("/root/a/", rows))
    }

    @Test
    fun representativeThumbnail_ignoresScenesOutsideFolder() {
        val rows =
            listOf(
                folderScene(sceneId = "1", path = "/root/abc/scene1.mp4", organized = true),
            )

        assertNull(LibraryIndexer.representativeThumbnailSceneIdFor("/root/a/", rows))
    }

    @Test
    fun directFolderSummary_usesNewestDirectSceneOnly() {
        val rows =
            listOf(
                folderScene(sceneId = "1", path = "/root/a/old-direct.mp4", organized = false).copy(updatedAtEpochMs = 100),
                folderScene(sceneId = "2", path = "/root/a/new-direct.mp4", organized = false).copy(updatedAtEpochMs = 300),
                folderScene(sceneId = "3", path = "/root/a/nested/newer.mp4", organized = true).copy(updatedAtEpochMs = 900),
                folderScene(sceneId = "4", path = "/root/ab/sibling.mp4", organized = true).copy(updatedAtEpochMs = 800),
            )

        assertEquals(
            LibraryIndexer.DirectFolderSummary(newestUpdatedAtEpochMs = 300, sceneId = "2"),
            LibraryIndexer.directFolderSummaryFor("/root/a/", rows),
        )
    }

    @Test
    fun directFolderSummary_keepsSceneWhenUpdateTimeIsZero() {
        val rows =
            listOf(
                folderScene(sceneId = "1", path = "/root/a/direct.mp4", organized = false).copy(updatedAtEpochMs = 0),
            )

        assertEquals(
            LibraryIndexer.DirectFolderSummary(newestUpdatedAtEpochMs = 0, sceneId = "1"),
            LibraryIndexer.directFolderSummaryFor("/root/a/", rows),
        )
    }

    private fun folderScene(
        sceneId: String,
        path: String,
        organized: Boolean,
    ) = FolderScene(
        serverUrl = "https://stash.example.test",
        sceneId = sceneId,
        path = path,
        parentPath = LibraryIndexer.parentPathOf(path),
        title = null,
        durationSeconds = null,
        rating100 = null,
        organized = organized,
        updatedAtEpochMs = sceneId.toLong(),
    )
}
