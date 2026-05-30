package com.github.damontecres.stashapp.util.realtime

import com.github.damontecres.stashapp.data.DataType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for the `entityChanged` event → refresh mapping: operation parsing, entity-kind →
 * [DataType] resolution, and the coalescing into a [LiveRefreshSignal] that drives cache
 * invalidation. No Apollo, no Room.
 */
class EntityChangeMappingTest {
    // -- Operation parsing ----------------------------------------------------------------

    @Test
    fun operation_parsesServerStringsCaseInsensitively() {
        assertEquals(EntityOperation.CREATE, EntityOperation.fromServer("Create"))
        assertEquals(EntityOperation.UPDATE, EntityOperation.fromServer("Update"))
        assertEquals(EntityOperation.DESTROY, EntityOperation.fromServer("Destroy"))
        assertEquals(EntityOperation.DESTROY, EntityOperation.fromServer("destroy"))
        assertEquals(EntityOperation.CREATE, EntityOperation.fromServer(" CREATE "))
    }

    @Test
    fun operation_unknownDefaultsToUpdate_notDestroy() {
        // An unknown op must trigger a (self-correcting) refetch, never a cache eviction.
        assertEquals(EntityOperation.UPDATE, EntityOperation.fromServer("Frobnicate"))
        assertEquals(EntityOperation.UPDATE, EntityOperation.fromServer(""))
    }

    // -- Entity → DataType ----------------------------------------------------------------

    @Test
    fun entity_mapsCapitalisedSingularServerKinds() {
        assertEquals(DataType.SCENE, EntityChange.dataTypeForEntity("Scene"))
        assertEquals(DataType.TAG, EntityChange.dataTypeForEntity("Tag"))
        assertEquals(DataType.PERFORMER, EntityChange.dataTypeForEntity("Performer"))
        assertEquals(DataType.STUDIO, EntityChange.dataTypeForEntity("Studio"))
        assertEquals(DataType.GROUP, EntityChange.dataTypeForEntity("Group"))
        assertEquals(DataType.GALLERY, EntityChange.dataTypeForEntity("Gallery"))
        assertEquals(DataType.IMAGE, EntityChange.dataTypeForEntity("Image"))
        assertEquals(DataType.MARKER, EntityChange.dataTypeForEntity("SceneMarker"))
    }

    @Test
    fun entity_alsoAcceptsLowercasePluralDeletedSinceForm() {
        // The deletedSince feed uses lowercase plural; one mapper should cover both NG feeds.
        assertEquals(DataType.SCENE, EntityChange.dataTypeForEntity("scenes"))
        assertEquals(DataType.TAG, EntityChange.dataTypeForEntity("tags"))
    }

    @Test
    fun entity_unknownKindsMapToNull_andAreIgnored() {
        // GalleryChapter has no local list/cache, so it must map to null (ignored).
        assertNull(EntityChange.dataTypeForEntity("GalleryChapter"))
        assertNull(EntityChange.dataTypeForEntity("Whatsit"))
    }

    // -- Coalescing into a signal ---------------------------------------------------------

    @Test
    fun signal_coalescesDistinctTypes_andCollectsDestroyedSceneIds() {
        val changes =
            listOf(
                EntityChange("Scene", "1", EntityOperation.CREATE),
                EntityChange("Scene", "2", EntityOperation.UPDATE),
                EntityChange("Scene", "3", EntityOperation.DESTROY),
                EntityChange("Tag", "9", EntityOperation.UPDATE),
                EntityChange("GalleryChapter", "x", EntityOperation.DESTROY), // ignored
            )

        val signal = LiveRefreshSignal.from(changes)

        assertEquals(setOf(DataType.SCENE, DataType.TAG), signal.changedTypes)
        // Only the Destroyed scene contributes a pruned id.
        assertEquals(setOf("3"), signal.deletedSceneIds)
        assertFalse(signal.isEmpty)
    }

    @Test
    fun signal_destroyedNonSceneDoesNotPopulateDeletedSceneIds() {
        val signal =
            LiveRefreshSignal.from(
                listOf(EntityChange("Tag", "7", EntityOperation.DESTROY)),
            )
        assertEquals(setOf(DataType.TAG), signal.changedTypes)
        assertTrue("non-scene destroys aren't row-pruned", signal.deletedSceneIds.isEmpty())
    }

    @Test
    fun signal_emptyWhenOnlyUnknownKinds() {
        val signal =
            LiveRefreshSignal.from(
                listOf(EntityChange("GalleryChapter", "x", EntityOperation.DESTROY)),
            )
        assertTrue(signal.isEmpty)
        assertEquals(LiveRefreshSignal.EMPTY, signal)
    }

    @Test
    fun signal_emptyFromEmptyInput() {
        assertTrue(LiveRefreshSignal.from(emptyList()).isEmpty)
    }
}
