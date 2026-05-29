package com.github.damontecres.stashapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the storage-time API-key normalization (Stream E, task 2): a key pasted with
 * surrounding whitespace must be trimmed once at the source so no call site (ExoPlayer
 * direct-play, OkHttp interceptor, Glide) ever sends a whitespace-tainted header that 401s.
 */
class StashServerApiKeyTest {
    @Test
    fun normalizeApiKey_trimsLeadingAndTrailingWhitespace() {
        assertEquals("abc123", StashServer.normalizeApiKey("  abc123  "))
        assertEquals("abc123", StashServer.normalizeApiKey("abc123\n"))
        assertEquals("abc123", StashServer.normalizeApiKey("\tabc123"))
    }

    @Test
    fun normalizeApiKey_keepsCleanKeyUnchanged() {
        assertEquals("abc123", StashServer.normalizeApiKey("abc123"))
    }

    @Test
    fun normalizeApiKey_blankBecomesNull() {
        assertNull(StashServer.normalizeApiKey(""))
        assertNull(StashServer.normalizeApiKey("   "))
        assertNull(StashServer.normalizeApiKey("\n\t "))
    }

    @Test
    fun normalizeApiKey_nullStaysNull() {
        assertNull(StashServer.normalizeApiKey(null))
    }
}
