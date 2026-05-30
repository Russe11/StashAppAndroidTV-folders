package com.github.damontecres.stashapp.util

import com.github.damontecres.stashapp.ui.components.prefs.CacheUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyStorageActionsTest {
    private fun usage(
        networkDisk: Long = 0,
        imageMemory: Long = 0,
        imageMemoryMax: Long = 0,
        imageDisk: Long = 0,
    ) = CacheUsage(
        networkDiskUsed = networkDisk,
        imageMemoryUsed = imageMemory,
        imageMemoryMax = imageMemoryMax,
        imageDiskUsed = imageDisk,
    )

    @Test
    fun imageCacheBytes_sumsDiskAndNetwork_ignoresMemory() {
        // In-memory image cache must be excluded so we don't double-count vs the freed disk size.
        val u = usage(networkDisk = 1_000, imageMemory = 5_000, imageDisk = 2_000)
        assertEquals(3_000L, PrivacyStorageActions.imageCacheBytes(u))
    }

    @Test
    fun imageCacheBytes_clampsNegativeReportedSizes() {
        // Coil/Glide can report -1 for an uninitialised disk cache; never go below zero.
        val u = usage(networkDisk = -1, imageDisk = -1)
        assertEquals(0L, PrivacyStorageActions.imageCacheBytes(u))
    }

    @Test
    fun shouldShowImageCacheSize_falseWhenEmpty() {
        assertFalse(PrivacyStorageActions.shouldShowImageCacheSize(usage()))
    }

    @Test
    fun shouldShowImageCacheSize_trueWhenAnyDiskOrNetworkBytes() {
        assertTrue(PrivacyStorageActions.shouldShowImageCacheSize(usage(imageDisk = 1)))
        assertTrue(PrivacyStorageActions.shouldShowImageCacheSize(usage(networkDisk = 1)))
    }

    @Test
    fun shouldShowImageCacheSize_falseWhenOnlyMemoryUsed() {
        // Memory-only usage doesn't represent freeable on-disk storage.
        assertFalse(PrivacyStorageActions.shouldShowImageCacheSize(usage(imageMemory = 9_999)))
    }

    @Test
    fun bothClearActionsAreRecoverable() {
        // Neither action deletes server data, so the UI may always describe them as recoverable.
        assertTrue(PrivacyStorageActions.isRecoverable(PrivacyStorageActions.ClearAction.IMAGE_CACHE))
        assertTrue(PrivacyStorageActions.isRecoverable(PrivacyStorageActions.ClearAction.LIBRARY_CACHE))
    }

    @Test
    fun onlyLibraryClearRequiresCurrentServer() {
        // The Room cache is server-keyed; the image/network caches are global.
        assertFalse(PrivacyStorageActions.requiresCurrentServer(PrivacyStorageActions.ClearAction.IMAGE_CACHE))
        assertTrue(PrivacyStorageActions.requiresCurrentServer(PrivacyStorageActions.ClearAction.LIBRARY_CACHE))
    }
}
