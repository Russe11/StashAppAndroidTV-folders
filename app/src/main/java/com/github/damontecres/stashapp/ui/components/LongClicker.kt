package com.github.damontecres.stashapp.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.StashApplication
import com.github.damontecres.stashapp.api.fragment.MarkerData
import com.github.damontecres.stashapp.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.api.fragment.StashData
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.filter.extractTitle
import com.github.damontecres.stashapp.navigation.Destination
import com.github.damontecres.stashapp.navigation.FilterAndPosition
import com.github.damontecres.stashapp.navigation.NavigationManager
import com.github.damontecres.stashapp.playback.PlaybackMode
import com.github.damontecres.stashapp.ui.pages.DialogParams
import com.github.damontecres.stashapp.ui.pages.MAX_PLAYLIST_SIZE
import com.github.damontecres.stashapp.util.SceneQuickActions
import com.github.damontecres.stashapp.util.resume_position
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

// Quick-action highlight tints for the currently-applied organized/rating state.
private val OrganizedTint = Color(0xFF66BB6A)
private val StarTint = Color(0xFFFFC700)

fun interface LongClicker<T> {
    fun longClick(
        item: T,
        filterAndPosition: FilterAndPosition?,
    )
}

/**
 * Callbacks for the scene-card quick-curation actions. Each call reuses the existing
 * [com.github.damontecres.stashapp.util.MutationEngine] writeback (organized / rating
 * / o-counter / tags) and is expected to apply an optimistic UI update. The current
 * effective values (after optimistic overrides) are passed in so the menu can render
 * the right toggle state and pre-select the current rating.
 */
interface SceneQuickActionHandlers {
    fun currentOrganized(scene: SlimSceneData): Boolean = scene.organized

    fun currentRating100(scene: SlimSceneData): Int? = scene.rating100

    fun onToggleOrganized(
        scene: SlimSceneData,
        newValue: Boolean,
    )

    fun onSetRating(
        scene: SlimSceneData,
        rating100: Int,
    )

    fun onIncrementOCounter(scene: SlimSceneData)

    fun onAddTag(scene: SlimSceneData)
}

class DefaultLongClicker(
    private val nav: NavigationManager,
    private val itemOnClick: ItemOnClicker<Any>,
    private val alwaysStartFromBeginning: Boolean,
    private val markerPlayAllOnClick: (FilterAndPosition) -> Unit,
    private val quickActionHandlers: SceneQuickActionHandlers? = null,
    private val onLongClick: (DialogParams) -> Unit,
) : LongClicker<Any> {
    override fun longClick(
        item: Any,
        filterAndPosition: FilterAndPosition?,
    ) {
        item as StashData
        val context = StashApplication.getApplication()
        val title = extractTitle(item) ?: ""
        val items =
            buildList {
                if (item is MarkerData) {
                    add(
                        DialogItem(
                            context.getString(R.string.play_scene),
                            Icons.Default.PlayArrow,
                        ) {
                            itemOnClick.onClick(item, filterAndPosition)
                        },
                    )
                    add(
                        DialogItem(
                            context.getString(R.string.go_to_scene),
                            Icons.AutoMirrored.Default.ArrowForward,
                        ) {
                            nav.navigate(
                                Destination.Item(DataType.SCENE, item.scene.minimalSceneData.id),
                            )
                        },
                    )
                    add(
                        DialogItem(
                            context.getString(R.string.stashapp_details),
                            Icons.Default.Info,
                        ) {
                            nav.navigate(
                                Destination.MarkerDetails(item.id),
                            )
                        },
                    )
                } else {
                    add(
                        DialogItem(context.getString(R.string.go_to), Icons.Default.Info) {
                            itemOnClick.onClick(
                                item,
                                filterAndPosition,
                            )
                        },
                    )
                }
                if (item is SlimSceneData) {
                    if (item.resume_time != null && item.resume_time > 0 && !alwaysStartFromBeginning) {
                        add(
                            DialogItem(
                                context.getString(R.string.resume),
                                Icons.Default.PlayArrow,
                            ) {
                                nav.navigate(
                                    Destination.Playback(
                                        item.id,
                                        item.resume_position!!,
                                        PlaybackMode.Choose,
                                    ),
                                )
                            },
                        )
                        add(
                            DialogItem(
                                context.getString(R.string.restart),
                                Icons.Default.Refresh,
                            ) {
                                nav.navigate(
                                    Destination.Playback(
                                        item.id,
                                        0L,
                                        PlaybackMode.Choose,
                                    ),
                                )
                            },
                        )
                    } else {
                        add(
                            DialogItem(
                                context.getString(R.string.play_scene),
                                Icons.Default.PlayArrow,
                            ) {
                                nav.navigate(
                                    Destination.Playback(
                                        item.id,
                                        0L,
                                        PlaybackMode.Choose,
                                    ),
                                )
                            },
                        )
                    }
                }
                if ((item is SlimSceneData || item is MarkerData) &&
                    filterAndPosition != null &&
                    filterAndPosition.position < MAX_PLAYLIST_SIZE // TODO
                ) {
                    add(
                        DialogItem(
                            context.getString(R.string.play_from_here),
                            Icons.Default.PlayArrow,
                        ) {
                            if (item is MarkerData) {
                                markerPlayAllOnClick.invoke(filterAndPosition)
                            } else {
                                nav.navigate(
                                    Destination.Playlist(
                                        filterAndPosition.filter,
                                        filterAndPosition.position,
                                        30.seconds.inWholeMilliseconds,
                                    ),
                                )
                            }
                        },
                    )
                }
                // Quick-curation actions: curate a scene without opening full detail
                // (a real win on a remote). Reuses MutationEngine via the handlers.
                val handlers = quickActionHandlers
                if (item is SlimSceneData && handlers != null) {
                    val organized = handlers.currentOrganized(item)
                    add(
                        DialogItem(
                            text =
                                if (organized) {
                                    context.getString(R.string.quick_action_mark_unorganized)
                                } else {
                                    context.getString(R.string.quick_action_mark_organized)
                                },
                            icon = Icons.Filled.CheckCircle,
                            // Tint the check green when already organized, neutral otherwise.
                            iconTintColor = if (organized) OrganizedTint else null,
                            onClick = { handlers.onToggleOrganized(item, !organized) },
                        ),
                    )
                    val currentRating = handlers.currentRating100(item)
                    SceneQuickActions.QUICK_RATING_LADDER.forEach { ladderValue ->
                        val stars = SceneQuickActions.rating100ToStars(ladderValue)
                        val selected = SceneQuickActions.isRatingSelected(ladderValue, currentRating)
                        val label =
                            if (ladderValue == 0) {
                                context.getString(R.string.quick_action_clear_rating)
                            } else {
                                context.getString(R.string.quick_action_set_rating_stars, stars)
                            }
                        add(
                            DialogItem(
                                text = label,
                                icon = Icons.Filled.Star,
                                // The currently-applied rating is highlighted gold.
                                iconTintColor = if (selected) StarTint else null,
                                onClick = { handlers.onSetRating(item, ladderValue) },
                            ),
                        )
                    }
                    add(
                        DialogItem(
                            text = context.getString(R.string.quick_action_increment_o_counter),
                            iconStringRes = R.string.fa_thumbs_up,
                            onClick = { handlers.onIncrementOCounter(item) },
                        ),
                    )
                    add(
                        DialogItem(
                            text = context.getString(R.string.quick_action_add_tag),
                            icon = Icons.Filled.Add,
                            onClick = { handlers.onAddTag(item) },
                        ),
                    )
                }
            }
        onLongClick.invoke(DialogParams(true, title, items))
    }
}

data class LongClickerAction<T>(
    @StringRes val title: Int,
    val filter: (T) -> Boolean,
    val action: (T, FilterAndPosition?) -> Unit,
) {
    val id: Int = idCounter.getAndIncrement()

    companion object {
        private val idCounter = AtomicInteger(0)
    }
}

data class LongClickPopup(
    val item: Any,
    val filterAndPosition: FilterAndPosition?,
    val actions: List<LongClickerAction<Any>>,
)

fun buildLongClickActionList(
    nav: NavigationManager,
    itemOnClicker: ItemOnClicker<Any>,
): List<LongClickerAction<Any>> =
    listOf(
        LongClickerAction<Any>(
            R.string.go_to,
            { true },
            { item, fp -> itemOnClicker.onClick(item, fp!!) },
        ),
        LongClickerAction<Any>(
            R.string.play_scene,
            { it is SlimSceneData && (it.resume_position == null || it.resume_position!! <= 0) },
            { item, _ ->
                item as SlimSceneData
                nav.navigate(Destination.Playback(item.id, 0L, PlaybackMode.Choose))
            },
        ),
        LongClickerAction<Any>(
            R.string.resume,
            { it is SlimSceneData && (it.resume_position != null && it.resume_position!! > 0) },
            { item, _ ->
                item as SlimSceneData
                nav.navigate(
                    Destination.Playback(
                        item.id,
                        item.resume_position ?: 0,
                        PlaybackMode.Choose,
                    ),
                )
            },
        ),
        LongClickerAction<Any>(
            R.string.restart,
            { it is SlimSceneData && (it.resume_position != null && it.resume_position!! > 0) },
            { item, _ ->
                item as SlimSceneData
                nav.navigate(Destination.Playback(item.id, 0L, PlaybackMode.Choose))
            },
        ),
    )
