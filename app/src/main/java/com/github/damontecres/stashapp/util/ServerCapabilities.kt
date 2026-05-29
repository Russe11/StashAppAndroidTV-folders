package com.github.damontecres.stashapp.util

/**
 * The NG capability handshake (see `docs/api/ng-contract.md` §1).
 *
 * This is an **NG-only** client: feature gating keys off [features] (a build-time set the
 * server advertises), never off a semantic server version. [edition] is `"ng"` for the fork;
 * upstream Stash has no `serverCapabilities` query at all, so probing it there yields a
 * "Cannot query field" GraphQL error — see [QueryEngine.getServerCapabilities], which maps
 * that negative signal to [UPSTREAM].
 *
 * Stored per-server in [ServerPreferences] so NG paths (e.g. the `deletedSince` deletion feed)
 * can be gated at connect without re-probing on every operation.
 */
data class ServerCapabilities(
    val edition: String,
    val apiVersion: Int,
    val features: Set<String>,
    val deletedSinceRetentionDays: Int,
) {
    val isNg: Boolean get() = edition == EDITION_NG

    fun supports(feature: String): Boolean = feature in features

    val supportsDeletedSince: Boolean get() = supports(FEATURE_DELETED_SINCE)
    val supportsMoveFolder: Boolean get() = supports(FEATURE_MOVE_FOLDER)
    val supportsFolderCounts: Boolean get() = supports(FEATURE_FOLDER_COUNTS)

    companion object {
        const val EDITION_NG = "ng"
        const val EDITION_UPSTREAM = "upstream"

        const val FEATURE_DELETED_SINCE = "deletedSince"
        const val FEATURE_MOVE_FOLDER = "moveFolder"
        const val FEATURE_FOLDER_COUNTS = "folderCounts"
        const val FEATURE_WEBHOOKS = "webhooks"

        /**
         * The capabilities of a server that does not answer `serverCapabilities` (i.e. plain
         * upstream Stash). No NG features; gating helpers all return false.
         */
        val UPSTREAM =
            ServerCapabilities(
                edition = EDITION_UPSTREAM,
                apiVersion = 0,
                features = emptySet(),
                deletedSinceRetentionDays = 0,
            )
    }
}
