package com.github.damontecres.stashapp.util.realtime

import com.github.damontecres.stashapp.util.QueryEngine
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.titleOrFilename
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resolves scene IDs (the only thing the deviceBus wire carries — invariant: IDs not titles) into
 * human titles **locally** for the online-devices UI.
 *
 * Privacy: the server never sends scene titles over the presence bus; the UI looks them up itself
 * by querying the scenes the user already has access to. Resolved names are memoised so repeated
 * presence/playback events for the same scene don't re-hit the network. A scene that can't be
 * resolved (deleted, or not visible) returns null and the UI shows a neutral fallback.
 */
class SceneNameResolver(
    private val server: StashServer,
    private val queryEngine: QueryEngine = QueryEngine(server),
) {
    private val cache = LinkedHashMap<String, String?>()
    private val lock = Mutex()

    /**
     * The local title for [sceneId], fetching + caching it on first request. Null ⇒ unknown (the
     * UI shows e.g. "Scene #<id>"). A blank/whitespace id resolves to null without a query.
     */
    suspend fun resolve(sceneId: String?): String? {
        if (sceneId.isNullOrBlank()) return null
        lock.withLock { if (cache.containsKey(sceneId)) return cache[sceneId] }

        val title =
            runCatching {
                queryEngine
                    .findScenes(ids = listOf(sceneId), useRandom = false)
                    .firstOrNull()
                    ?.titleOrFilename
            }.getOrNull()

        lock.withLock { cache[sceneId] = title }
        return title
    }

    /** Drop the memoised names (e.g. on server switch). */
    suspend fun clear() {
        lock.withLock { cache.clear() }
    }
}
