package com.github.damontecres.stashapp.util.realtime

import com.github.damontecres.stashapp.data.DataType

/**
 * The operation an [EntityChange] reports. Mirrors the server's `entityChanged.operation`
 * string values ("Create"/"Update"/"Destroy"); [DESTROY] is the only one that requires a
 * cache eviction rather than a refetch.
 */
enum class EntityOperation {
    CREATE,
    UPDATE,
    DESTROY,
    ;

    companion object {
        /**
         * Map the server's operation string (case-insensitive) to an [EntityOperation]. An
         * unknown value maps to [UPDATE] — the conservative choice, because treating an unknown
         * change as an update triggers a refetch (which self-corrects) rather than a destroy
         * (which would evict a still-live row).
         */
        fun fromServer(raw: String): EntityOperation =
            when (raw.trim().lowercase()) {
                "create" -> CREATE
                "destroy" -> DESTROY
                else -> UPDATE
            }
    }
}

/**
 * A metadata-only entity change decoded from the NG `entityChanged` subscription.
 *
 * Deliberately decoupled from the Apollo-generated `EntityChangedSubscription.EntityChanged`
 * type so the live-refresh logic (mapping, debounce, reconnect) is pure and unit-testable
 * without standing up Apollo. The repository adapts the generated type into this on receipt.
 *
 * Privacy: carries IDs only — never titles or paths (the wire payload has none either).
 */
data class EntityChange(
    /** The raw server entity kind string, e.g. "Scene", "Tag" (capitalised singular). */
    val entity: String,
    /** The affected entity's id. */
    val id: String,
    val operation: EntityOperation,
) {
    /**
     * The [DataType] this change targets, or null if the entity kind isn't one this client
     * caches/displays (e.g. "GalleryChapter"). A null mapping means "ignore" — there is no
     * list or cache to refresh for it.
     */
    val dataType: DataType? get() = dataTypeForEntity(entity)

    companion object {
        /**
         * Map a server `entityChanged.entity` string to a client [DataType].
         *
         * The server emits capitalised singular kinds — "Scene", "Tag", "Performer",
         * "Gallery", "Image", "Studio", "Group", "SceneMarker", "GalleryChapter" (see the
         * server SDL for `EntityChangeEvent.entity`). We match case-insensitively and also
         * accept the lowercase-plural form the `deletedSince` feed uses ("scenes", "tags", …)
         * so a single mapper covers both NG metadata feeds. Kinds without a local list/cache
         * (currently "GalleryChapter") map to null and are ignored.
         */
        fun dataTypeForEntity(entity: String): DataType? =
            when (entity.trim().lowercase().removeSuffix("s")) {
                "scene" -> DataType.SCENE
                "tag" -> DataType.TAG
                "performer" -> DataType.PERFORMER
                "studio" -> DataType.STUDIO
                "group" -> DataType.GROUP
                "gallery" -> DataType.GALLERY
                "image" -> DataType.IMAGE
                "scenemarker", "marker" -> DataType.MARKER
                else -> null // GalleryChapter and any future/unknown kind: nothing to refresh.
            }
    }
}
