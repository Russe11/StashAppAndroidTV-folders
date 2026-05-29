package com.github.damontecres.stashapp.util

import com.github.damontecres.stashapp.api.fragment.SlimSceneData

/**
 * Deterministic, privacy-preserving recommendation heuristic.
 *
 * Everything here is computed purely from the user's *local* engagement metadata that the
 * server already exposes (play history, tags, performers, ratings). There is NO external
 * service, NO telemetry, and NO network access inside this object: it is a pure transform
 * over scene lists, which is exactly what makes it cheap to unit-test and safe for an
 * adult-media library.
 *
 * The heuristic, in one sentence: **"more of what you actually watch"** — score the tags and
 * performers attached to the scenes you have recently played (weighting more-recent /
 * more-played scenes higher), then recommend other scenes that share those top tags and
 * performers, ranked by how much affinity they accumulate (with rating as a deterministic
 * tie-breaker), and never recommend something you have already watched.
 *
 * The two stages are separated so the affinity profile (cheap, local) can drive a single
 * server `findScenes` query for the candidate pool, and the final ranking is done locally.
 */
object RecommendationEngine {
    /**
     * Default number of top tags to feed into the candidate query.
     */
    const val DEFAULT_TOP_TAGS = 8

    /**
     * Default number of top performers to feed into the candidate query.
     */
    const val DEFAULT_TOP_PERFORMERS = 8

    /**
     * An affinity profile: the entity ids the user engages with most, most-affine first.
     *
     * [tagIds] and [performerIds] are ordered by descending [TagAffinity.score] /
     * [PerformerAffinity.score]; ties are broken deterministically by ascending id so the
     * output is stable across runs (important for both caching and tests).
     */
    data class AffinityProfile(
        val tagIds: List<String>,
        val performerIds: List<String>,
        val tagScores: Map<String, Double>,
        val performerScores: Map<String, Double>,
    ) {
        val isEmpty: Boolean get() = tagIds.isEmpty() && performerIds.isEmpty()
    }

    /**
     * Build an [AffinityProfile] from the scenes the user has recently watched.
     *
     * Each watched scene contributes a weight to every tag and performer attached to it. The
     * weight rewards engagement: a base of 1.0 plus a bounded bonus for repeat plays
     * (`play_count`). We intentionally do NOT weight by recency-rank here — the caller controls
     * recency by *which* scenes it passes in (e.g. the N most-recently-played) — so this stays a
     * simple, order-independent, easily-verifiable tally.
     *
     * @param watched the user's recently/most-played scenes (already fetched locally)
     * @param topTags how many of the highest-scoring tags to keep
     * @param topPerformers how many of the highest-scoring performers to keep
     */
    fun buildProfile(
        watched: List<SlimSceneData>,
        topTags: Int = DEFAULT_TOP_TAGS,
        topPerformers: Int = DEFAULT_TOP_PERFORMERS,
    ): AffinityProfile {
        val tagScores = mutableMapOf<String, Double>()
        val performerScores = mutableMapOf<String, Double>()

        watched.forEach { scene ->
            val weight = engagementWeight(scene)
            scene.tags.forEach { tag ->
                val id = tag.slimTagData.id
                tagScores[id] = (tagScores[id] ?: 0.0) + weight
            }
            scene.performers.forEach { performer ->
                performerScores[performer.id] =
                    (performerScores[performer.id] ?: 0.0) + weight
            }
        }

        return AffinityProfile(
            tagIds = topIds(tagScores, topTags),
            performerIds = topIds(performerScores, topPerformers),
            tagScores = tagScores,
            performerScores = performerScores,
        )
    }

    /**
     * Per-scene engagement weight: base 1.0 plus a bounded log-ish bonus for repeat plays.
     *
     * A scene played 5 times shouldn't drown out five different scenes played once, so the
     * play-count bonus is capped. `play_count` of null/0 still contributes the base weight
     * because the scene is in the watched set at all.
     */
    private fun engagementWeight(scene: SlimSceneData): Double {
        val plays = (scene.play_count ?: 0).coerceAtLeast(0)
        // base 1.0; each extra play adds 0.25, capped at +1.0 total bonus.
        val bonus = (plays - 1).coerceAtLeast(0) * 0.25
        return 1.0 + bonus.coerceAtMost(1.0)
    }

    private fun topIds(
        scores: Map<String, Double>,
        limit: Int,
    ): List<String> =
        scores.entries
            // Highest score first; stable tie-break on ascending id for determinism.
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(limit.coerceAtLeast(0))
            .map { it.key }

    /**
     * Rank candidate scenes by how strongly they match the user's [profile], dropping anything
     * already watched, and return the best [limit].
     *
     * A candidate's score is the sum of the affinity scores of its tags and performers that
     * appear in the profile. Scenes that share nothing with the profile score 0 and are
     * dropped. Rating (0-100, null treated as -1 so unrated sinks below rated) is the
     * deterministic tie-breaker, then ascending id so results never shuffle between identical
     * runs.
     *
     * @param candidates scenes fetched as the candidate pool (e.g. via a tag/performer query)
     * @param profile the user's affinity profile from [buildProfile]
     * @param excludeSceneIds scene ids to never recommend (already watched / continue-watching)
     * @param limit maximum number of recommendations to return
     */
    fun rankRecommendations(
        candidates: List<SlimSceneData>,
        profile: AffinityProfile,
        excludeSceneIds: Set<String>,
        limit: Int,
    ): List<SlimSceneData> {
        if (profile.isEmpty) return emptyList()
        return candidates
            .asSequence()
            .filter { it.id !in excludeSceneIds }
            .map { scene -> scene to affinityScore(scene, profile) }
            .filter { (_, score) -> score > 0.0 }
            .sortedWith(
                compareByDescending<Pair<SlimSceneData, Double>> { it.second }
                    .thenByDescending { it.first.rating100 ?: -1 }
                    .thenBy { it.first.id },
            ).take(limit.coerceAtLeast(0))
            .map { it.first }
            .toList()
    }

    private fun affinityScore(
        scene: SlimSceneData,
        profile: AffinityProfile,
    ): Double {
        var score = 0.0
        scene.tags.forEach { tag ->
            profile.tagScores[tag.slimTagData.id]?.let { score += it }
        }
        scene.performers.forEach { performer ->
            profile.performerScores[performer.id]?.let { score += it }
        }
        return score
    }
}
