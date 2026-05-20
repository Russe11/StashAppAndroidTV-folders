package com.github.damontecres.stashapp.folders.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [LibraryIndexer]'s path canonicalization helpers.
 *
 * Regression coverage for the `/prv/docs/` empty-grid bug, which was caused by
 * [LibraryIndexer.normalizeFilePath] preserving the (missing) leading slash
 * shape from Stash, while [LibraryIndexer.parentPathOf] always prepended one.
 * The folder browser's `path LIKE :pathPrefix || '%'` query then missed every
 * scene whose raw path arrived without a leading slash.
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
        // The folder query is `path LIKE parent || '%'`; this assertion
        // captures that contract explicitly.
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
}
