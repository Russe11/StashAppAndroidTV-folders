package com.github.damontecres.stashapp.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ServerCapabilities] — the NG capability handshake gating.
 *
 * NG-only invariant: feature gating keys off the advertised [ServerCapabilities.features] set,
 * never off a semantic server version.
 */
class ServerCapabilitiesTest {
    @Test
    fun ngServer_gatesEachFeatureOnTheAdvertisedSet() {
        val caps =
            ServerCapabilities(
                edition = ServerCapabilities.EDITION_NG,
                apiVersion = 1,
                features = setOf("deletedSince", "moveFolder"),
                deletedSinceRetentionDays = 365,
            )

        assertTrue(caps.isNg)
        assertTrue(caps.supportsDeletedSince)
        assertTrue(caps.supportsMoveFolder)
        // folderCounts/webhooks are not in this server's set, so they gate off.
        assertFalse(caps.supportsFolderCounts)
        assertFalse(caps.supports(ServerCapabilities.FEATURE_WEBHOOKS))
    }

    @Test
    fun upstreamFallback_disablesEveryNgPath() {
        // The negative signal (no serverCapabilities query) maps to UPSTREAM, which must keep
        // all NG-gated paths off rather than firing blind.
        val caps = ServerCapabilities.UPSTREAM

        assertFalse(caps.isNg)
        assertFalse(caps.supportsDeletedSince)
        assertFalse(caps.supportsMoveFolder)
        assertFalse(caps.supportsFolderCounts)
        assertTrue(caps.features.isEmpty())
    }
}
