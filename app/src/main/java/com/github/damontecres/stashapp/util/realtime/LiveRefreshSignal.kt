package com.github.damontecres.stashapp.util.realtime

import com.github.damontecres.stashapp.data.DataType

/**
 * The coalesced result of one debounce window: the union of everything that changed during a
 * burst of [EntityChange] events, reduced to the minimum a consumer needs to refresh.
 *
 * Produced by [EntityChangeDebouncer]; consumed by `LiveRefreshRepository`, which uses
 * [deletedSceneIds] to prune the Room cache and [changedTypes] to invalidate the matching
 * visible lists.
 */
data class LiveRefreshSignal(
    /**
     * The distinct [DataType]s touched in this window (any operation). A consumer invalidates
     * the on-screen list for each. Entity kinds with no local list (e.g. GalleryChapter) are
     * already filtered out upstream and never appear here.
     */
    val changedTypes: Set<DataType>,
    /**
     * Scene ids the server reported as Destroyed in this window. The cache prunes exactly
     * these rather than re-running a full reconcile — `entityChanged` gives us the ids
     * directly. (Only scenes are cached row-by-row in Room today; other destroyed kinds are
     * surfaced via [changedTypes] so their lists refetch.)
     */
    val deletedSceneIds: Set<String>,
) {
    val isEmpty: Boolean get() = changedTypes.isEmpty() && deletedSceneIds.isEmpty()

    companion object {
        val EMPTY = LiveRefreshSignal(emptySet(), emptySet())

        /**
         * Reduce a batch of [EntityChange]s (a debounce window) into one signal. Pure: no I/O,
         * no clock. Changes whose entity kind has no local [DataType] are dropped; a Destroyed
         * scene contributes its id to [deletedSceneIds] as well as its type to [changedTypes].
         */
        fun from(changes: Collection<EntityChange>): LiveRefreshSignal {
            if (changes.isEmpty()) return EMPTY
            val types = LinkedHashSet<DataType>()
            val deletedScenes = LinkedHashSet<String>()
            for (change in changes) {
                val type = change.dataType ?: continue
                types.add(type)
                if (type == DataType.SCENE && change.operation == EntityOperation.DESTROY) {
                    deletedScenes.add(change.id)
                }
            }
            if (types.isEmpty() && deletedScenes.isEmpty()) return EMPTY
            return LiveRefreshSignal(changedTypes = types, deletedSceneIds = deletedScenes)
        }
    }
}
