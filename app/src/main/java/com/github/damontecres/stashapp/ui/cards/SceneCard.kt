package com.github.damontecres.stashapp.ui.cards

import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.navigation.FilterAndPosition
import com.github.damontecres.stashapp.presenters.ScenePresenter
import com.github.damontecres.stashapp.ui.ComposeUiConfig
import com.github.damontecres.stashapp.ui.LocalSceneCurationOverrides
import com.github.damontecres.stashapp.ui.compat.isNotTvDevice
import com.github.damontecres.stashapp.ui.components.LongClicker
import com.github.damontecres.stashapp.ui.enableMarquee
import com.github.damontecres.stashapp.util.isNotNullOrBlank
import com.github.damontecres.stashapp.util.resolutionName
import com.github.damontecres.stashapp.util.titleOrFilename
import com.github.damontecres.stashapp.views.durationToString
import java.util.EnumMap

@Composable
fun SceneCard(
    uiConfig: ComposeUiConfig,
    item: SlimSceneData?,
    onClick: (() -> Unit),
    longClicker: LongClicker<Any>,
    getFilterAndPosition: ((item: Any) -> FilterAndPosition)?,
    modifier: Modifier = Modifier,
    cardContext: CardContext.SceneCardContext? = null,
) {
    // Built once per item rather than allocated + repopulated on every recompose.
    val dataTypeMap =
        remember(item) {
            EnumMap<DataType, Int>(DataType::class.java).apply {
                item?.let {
                    this[DataType.TAG] = item.tags.size
                    this[DataType.PERFORMER] = item.performers.size
                    this[DataType.GROUP] = item.groups.size
                    this[DataType.MARKER] = item.scene_markers.size
                    this[DataType.GALLERY] = item.galleries.size
                }
            }
        }

    // Reflect optimistic quick-action edits (organized / rating / o-counter) applied from
    // the long-press menu before the paged source refetches. Falls back to server values.
    val overrides = LocalSceneCurationOverrides.current
    val effectiveRating100 = item?.let { overrides.effectiveRating100(it.id, it.rating100) }
    val effectiveOCounter = item?.let { overrides.effectiveOCounter(it.id, it.o_counter) }
    val effectiveOrganized = item?.let { overrides.effectiveOrganized(it.id, it.organized) } ?: false

    RootCard(
        item = item,
        modifier =
            modifier
                .padding(0.dp),
        contentPadding = PaddingValues(0.dp),
        onClick = onClick,
        longClicker = longClicker,
        getFilterAndPosition = getFilterAndPosition,
        uiConfig = uiConfig,
        imageWidth = ScenePresenter.CARD_WIDTH.dp / 2,
        imageHeight = ScenePresenter.CARD_HEIGHT.dp / 2,
        imageUrl = item?.paths?.screenshot,
        defaultImageDrawableRes = R.drawable.default_scene,
        videoUrl = item?.paths?.preview,
        title = AnnotatedString(item?.titleOrFilename ?: ""),
        subtitle = {
            Column {
                Text(item?.date ?: "")
                cardContext?.let {
                    val index =
                        item?.groups?.firstOrNull { cardContext.sceneInGroupId == it.group.id }?.scene_index
                    if (index != null) {
                        Text(
                            text = stringResource(R.string.stashapp_scene) + " #$index",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        description = {
            // `it` is the D-pad-focused flag; on touch there is no focus event, so also
            // animate the icon row there so phone users see the full row.
            IconRowText(
                sfwMode = uiConfig.sfwMode,
                dataTypeMap,
                effectiveOCounter ?: -1,
                Modifier
                    .enableMarquee(it || isNotTvDevice)
                    .align(Alignment.Center),
            )
        },
        imageOverlay = {
            ImageOverlay(uiConfig.ratingAsStars, rating100 = effectiveRating100) {
                if (effectiveOrganized) {
                    // Drawn at the right edge, vertically centered: avoids the rating (TopStart),
                    // studio badge (TopEnd), duration/resolution/progress (bottom row).
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.stashapp_organized),
                        tint = colorResource(android.R.color.holo_green_light),
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(8.dp),
                    )
                }
                val videoFile = item?.files?.firstOrNull()?.videoFile
                if (videoFile != null) {
                    val duration = durationToString(videoFile.duration)
                    Text(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                        text = duration,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        text = videoFile.resolutionName().toString(),
                    )
                    if (item.resume_time != null && uiConfig.showCardProgress) {
                        val percentWatched = item.resume_time / videoFile.duration
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .background(
                                        MaterialTheme.colorScheme.tertiary,
                                    ).clip(RectangleShape)
                                    .height(4.dp)
                                    .width((ScenePresenter.CARD_WIDTH * percentWatched).dp / 2),
                        )
                    }
                }
                if (item?.studio != null) {
                    val imageUrl = item.studio.image_path
                    if (!uiConfig.showStudioAsText &&
                        imageUrl.isNotNullOrBlank() &&
                        !imageUrl.contains(
                            "default=true",
                        )
                    ) {
                        AsyncImage(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .fillMaxWidth(.4f),
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            text = item.studio.name,
                        )
                    }
                }
            }
        },
    )
}
