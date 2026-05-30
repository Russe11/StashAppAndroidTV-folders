package com.github.damontecres.stashapp.util

import com.github.damontecres.stashapp.ui.components.prefs.CacheUsage

/**
 * Pure decision logic backing the "Privacy & Storage" dashboard's cache-clearing actions.
 *
 * This holds NO Android dependencies on purpose: the actual cache wipes (Glide disk/memory,
 * the network OkHttp cache, the Room "Folders/New" library index) are side effects performed by
 * the UI/`SettingsFragment.clearCaches` / `LibraryIndexerBridge.forceResync`, which require an
 * instrumented test to exercise. Everything that can be decided without touching the device —
 * how big the caches are, which clear action is destructive vs. recoverable, what label to show —
 * lives here so it can be unit-tested.
 */
object PrivacyStorageActions {
    /**
     * The two local caches the dashboard can clear. Server data is never touched.
     */
    enum class ClearAction {
        /**
         * Glide's disk + memory image cache plus the network/OkHttp response cache. Purely a
         * convenience cache — clearing it only forces images/requests to be re-fetched lazily.
         */
        IMAGE_CACHE,

        /**
         * The Room-backed "Folders / New" library index for the current server (scenes, the
         * materialised folder tree, and the delta-sync watermark). Recoverable: the next time the
         * Folders/New screen opens it re-syncs from the server.
         */
        LIBRARY_CACHE,
    }

    /**
     * Bytes attributable to the image-cache action (Glide/Coil disk + the network response cache).
     * The in-memory image cache is excluded: it is reclaimed automatically and showing it would
     * double-count what the user is about to free on disk.
     */
    fun imageCacheBytes(usage: CacheUsage): Long =
        (usage.imageDiskUsed.coerceAtLeast(0L)) + (usage.networkDiskUsed.coerceAtLeast(0L))

    /**
     * Whether it's worth surfacing an approximate on-disk size for the image cache. When the
     * caches are empty (fresh install / just cleared) we skip the "Using 0 B" noise.
     */
    fun shouldShowImageCacheSize(usage: CacheUsage): Boolean = imageCacheBytes(usage) > 0L

    /**
     * Clearing the library index is recoverable (it just re-syncs), so the confirmation can say so.
     * Clearing the image cache is likewise non-destructive. Neither action is ever permanent, but
     * the library re-sync can take noticeably longer, so the UI warns about that specifically.
     */
    fun isRecoverable(action: ClearAction): Boolean =
        when (action) {
            ClearAction.IMAGE_CACHE -> true
            ClearAction.LIBRARY_CACHE -> true
        }

    /**
     * The library clear needs the current server's URL to scope the wipe (the Room cache is
     * server-keyed); the image clear is global. Returns true when the action cannot run without a
     * connected server, so the UI can disable/skip it instead of throwing.
     */
    fun requiresCurrentServer(action: ClearAction): Boolean =
        when (action) {
            ClearAction.IMAGE_CACHE -> false
            ClearAction.LIBRARY_CACHE -> true
        }
}
